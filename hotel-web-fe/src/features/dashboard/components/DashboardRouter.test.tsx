import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  roles: [] as string[],
  permissions: [] as string[],
  userType: 'guest' as 'guest' | 'admin',
  navigate: vi.fn(),
}));

vi.mock('../../../router', () => ({
  useNavigate: () => mocks.navigate,
}));

vi.mock('../../../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { user_type: mocks.userType },
    hasRole: (role: string) => mocks.roles.includes(role),
    hasPermission: (perm: string) => mocks.permissions.includes(perm),
    getRoutePolicy: (routeId: string) => {
      if (routeId === 'support') {
        return {
          route_id: 'support',
          path: '/support',
          required_permissions: ['support:read'],
          required_roles: [],
          excluded_roles: [],
          nav_permissions: ['support:read'],
          nav_roles: [],
          nav_excluded_roles: [],
        };
      }
      return {
        route_id: routeId,
        path: `/${routeId}`,
        required_permissions: ['restricted:none'],
        required_roles: [],
        excluded_roles: [],
        nav_permissions: ['restricted:none'],
        nav_roles: [],
        nav_excluded_roles: [],
      };
    },
  }),
}));

vi.mock('./reports/ReportsAnalytics', () => ({
  default: () => <div data-testid="reports-analytics" />,
}));

vi.mock('./ReceptionistDashboard', () => ({
  default: () => <div data-testid="receptionist-dashboard" />,
}));

import DashboardRouter from './DashboardRouter';

describe('DashboardRouter', () => {
  beforeEach(() => {
    mocks.roles = [];
    mocks.permissions = [];
    mocks.userType = 'guest';
    mocks.navigate.mockReset();
  });

  afterEach(cleanup);

  it.each([
    ['admin', 'reports'],
    ['super_admin', 'reports'],
    ['manager', 'reports'],
  ])('lands %s on the analytics dashboard', (role, expected) => {
    mocks.roles = [role];

    render(<DashboardRouter />);

    expect(screen.getByTestId(expected === 'reports' ? 'reports-analytics' : 'receptionist-dashboard')).toBeTruthy();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it('lands receptionist/employee roles on the operational dashboard', () => {
    for (const role of ['receptionist', 'employee']) {
      cleanup();
      mocks.roles = [role];

      render(<DashboardRouter />);

      expect(screen.getByTestId('receptionist-dashboard')).toBeTruthy();
      expect(mocks.navigate).not.toHaveBeenCalled();
    }
  });

  it('redirects unrecognised roles to the guest portal', () => {
    mocks.roles = ['guest'];

    render(<DashboardRouter />);

    expect(mocks.navigate).toHaveBeenCalledWith('/guest-portal', { replace: true });
    expect(screen.queryByTestId('reports-analytics')).toBeNull();
  });

  it('treats combined staff roles as staff even when guest is also present', () => {
    // A user can hold multiple roles; any staff role wins over the redirect.
    mocks.roles = ['guest', 'receptionist'];

    render(<DashboardRouter />);

    expect(screen.getByTestId('receptionist-dashboard')).toBeTruthy();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });

  it('routes non-dashboard staff roles to their first allowed route instead of /guest-portal', () => {
    mocks.userType = 'admin';
    mocks.roles = ['support_readonly'];
    mocks.permissions = ['support:read', 'navigation_support:read'];

    render(<DashboardRouter />);

    expect(mocks.navigate).toHaveBeenCalledWith('/support', { replace: true });
    expect(mocks.navigate).not.toHaveBeenCalledWith('/guest-portal', expect.anything());
  });

  it('falls back to /profile when a staff user holds no permitted sidebar routes', () => {
    mocks.userType = 'admin';
    mocks.roles = ['custom_limited'];
    mocks.permissions = [];

    render(<DashboardRouter />);

    expect(mocks.navigate).toHaveBeenCalledWith('/profile', { replace: true });
  });
});
