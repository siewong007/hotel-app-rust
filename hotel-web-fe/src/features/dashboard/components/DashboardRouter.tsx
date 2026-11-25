import React, { useEffect, useMemo } from 'react';
import { useNavigate } from '../../../router';
import { useAuth } from '../../../auth/AuthContext';
import {
  canAccessNavigationRoute,
  navigationRouteDefinitions,
} from '../../../navigation/routeRegistry';
import ReportsAnalytics from './reports/ReportsAnalytics';
import ReceptionistDashboard from './ReceptionistDashboard';

const DashboardRouter: React.FC = () => {
  const { user, hasRole, hasPermission, getRoutePolicy } = useAuth();
  const navigate = useNavigate();

  // Define role groups based on user instructions
  const isAdminOrSuper = hasRole('admin') || hasRole('super_admin');
  const isExec = hasRole('manager');
  const isEmployee = hasRole('receptionist') || hasRole('employee');

  const fallbackPath = useMemo(() => {
    if (isAdminOrSuper || isExec || isEmployee) return null;
    // Non-guest operational roles (e.g. support_readonly, housekeeper, staff)
    // normalize to `user_type === 'admin'` and must never be routed to
    // /guest-portal, which rejects staff back to `/` and triggers a full-page
    // reload loop. Land them on their first allowed staff route, or `/profile`
    // when no sidebar route is permitted.
    if (user?.user_type === 'admin') {
      const firstAllowed = navigationRouteDefinitions.find(
        (route) =>
          route.id !== 'dashboard' &&
          canAccessNavigationRoute(route, { hasPermission, hasRole, getRoutePolicy }),
      );
      return firstAllowed?.path ?? '/profile';
    }
    return '/guest-portal';
  }, [isAdminOrSuper, isExec, isEmployee, user?.user_type, hasPermission, hasRole, getRoutePolicy]);

  useEffect(() => {
    if (fallbackPath) {
      navigate(fallbackPath, { replace: true });
    }
  }, [fallbackPath, navigate]);

  // Front desk lands on the operational "today" board — arrivals, departures
  // and room states — not their own profile page.
  if (isEmployee && !isAdminOrSuper && !isExec) {
    return <ReceptionistDashboard />;
  }

  // Admin, Super User, and Exec User (Manager) see the analytics dashboard
  if (isAdminOrSuper || isExec) {
    return <ReportsAnalytics />;
  }

  // Fallback (e.g., during redirection)
  return null;
};

export default DashboardRouter;
