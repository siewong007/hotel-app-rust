import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  canApprovePayments: false,
  items: [] as Array<Record<string, unknown>>,
  markRead: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1, user_type: 'staff' },
    hasPermission: (perm: string) => perm === 'payments:approve' && mocks.canApprovePayments,
  }),
}));

vi.mock('../../router/compat', () => ({
  useNavigate: () => mocks.navigate,
}));

vi.mock('../../features/admin/system/hooks', () => ({
  useStaffNotifications: () => ({
    data: { unread: mocks.items.length, items: mocks.items },
    isPending: false,
  }),
  useMarkStaffNotificationRead: () => ({ mutate: mocks.markRead }),
}));

import { UrgentStaffAlertsBanner } from './UrgentStaffAlertsBanner';

describe('UrgentStaffAlertsBanner', () => {
  beforeEach(() => {
    mocks.canApprovePayments = false;
    mocks.items = [];
    mocks.markRead.mockReset();
    mocks.navigate.mockReset();
  });

  afterEach(cleanup);

  it('renders urgent pending alert with CTA and acknowledge action', () => {
    mocks.items = [
      {
        id: 101,
        kind: 'maintenance_critical',
        subject: 'MT-202611-0001',
        title: 'Critical maintenance ticket',
        body: 'Boiler offline',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'urgent',
        category: 'maintenance',
        action_url: '/housekeeping?tab=maintenance',
        action_label_key: 'openMaintenance',
        requires_action: true,
        action_pending: true,
      },
    ];

    render(<UrgentStaffAlertsBanner />);

    expect(screen.getByTestId('urgent-staff-alerts-banner')).toBeTruthy();
    expect(screen.getByText('Critical maintenance ticket')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Open maintenance' }));
    expect(mocks.markRead).toHaveBeenCalledWith(101);
    expect(mocks.navigate).toHaveBeenCalledWith('/housekeeping?tab=maintenance');

    fireEvent.click(screen.getByRole('button', { name: 'Acknowledge alert' }));
    expect(mocks.markRead).toHaveBeenCalledWith(101);
  });

  it('hides bank_transfer_pending for payments:approve holders to avoid duplicating UrgentTransferAlert', () => {
    mocks.items = [
      {
        id: 102,
        kind: 'bank_transfer_pending',
        subject: 'BK-500',
        title: 'Payment pending approval',
        body: null,
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'urgent',
        category: 'payments',
        action_url: '/payment-approvals',
        action_label_key: 'reviewPayment',
        requires_action: true,
        action_pending: true,
      },
    ];

    mocks.canApprovePayments = true;
    const { container, rerender } = render(<UrgentStaffAlertsBanner />);
    expect(container.innerHTML).toBe('');

    mocks.canApprovePayments = false;
    rerender(<UrgentStaffAlertsBanner />);
    expect(screen.getByTestId('urgent-staff-alerts-banner')).toBeTruthy();
  });

  it('renders nothing once urgent alerts are handled or read', () => {
    mocks.items = [
      {
        id: 103,
        kind: 'support_sla_breached',
        subject: 'SUP-1:breached',
        title: 'Support SLA breached',
        body: null,
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'urgent',
        category: 'support',
        action_url: '/support?queue=at_risk&search=SUP-1',
        action_label_key: 'handleSlaBreach',
        requires_action: true,
        action_pending: false,
      },
    ];

    const { container } = render(<UrgentStaffAlertsBanner />);
    expect(container.innerHTML).toBe('');
  });
});
