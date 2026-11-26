import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  items: [] as Array<Record<string, unknown>>,
  markRead: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, user_type: 'staff' } }),
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

import { StaffAlertToaster } from './StaffAlertToaster';

describe('StaffAlertToaster', () => {
  beforeEach(() => {
    mocks.items = [];
    mocks.markRead.mockReset();
    mocks.navigate.mockReset();
  });

  afterEach(cleanup);

  it('does not toast initial alerts on mount, but pops CTA toast for live arrivals', () => {
    mocks.items = [
      {
        id: 1,
        kind: 'online_booking_created',
        subject: 'BK-001',
        title: 'New online booking',
        body: 'Initial booking',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'normal',
        category: 'bookings',
        action_url: '/bookings?search=BK-001',
        action_label_key: 'viewBooking',
        requires_action: false,
        action_pending: true,
      },
    ];

    const { rerender } = render(<StaffAlertToaster />);
    expect(screen.queryByRole('button', { name: 'View booking' })).toBeNull();

    mocks.items = [
      {
        id: 2,
        kind: 'payment_receipt_uploaded',
        subject: 'BK-900',
        title: 'Payment receipt uploaded',
        body: 'Receipt uploaded',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'urgent',
        category: 'payments',
        action_url: '/payment-approvals',
        action_label_key: 'reviewReceipt',
        requires_action: true,
        action_pending: true,
      },
      ...mocks.items,
    ];

    rerender(<StaffAlertToaster />);

    const cta = screen.getByRole('button', { name: 'Review receipt' });
    fireEvent.click(cta);
    expect(mocks.markRead).toHaveBeenCalledWith(2);
    expect(mocks.navigate).toHaveBeenCalledWith('/payment-approvals');
  });
});
