import React from 'react';
import { cleanup, render, screen, fireEvent } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  hasPermission: true as boolean | ((permission: string) => boolean),
  serverUnread: 3,
  staffUnread: 0,
  urgentUnread: 0,
  staffItems: [] as Array<Record<string, unknown>>,
  feedCalls: [] as Array<{ enabled: boolean; open: boolean }>,
  markOneStaffRead: vi.fn(),
  markAllStaffRead: vi.fn(),
  markLocalRead: vi.fn(),
}));

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1 },
    hasPermission: (permission: string) =>
      typeof mocks.hasPermission === 'function' ? mocks.hasPermission(permission) : mocks.hasPermission,
  }),
}));

vi.mock('../../features/notifications/hooks/useDeliveryFeed', () => ({
  useDeliveryFeed: (params: { tier?: string }, enabled: boolean, open: boolean) => {
    mocks.feedCalls.push({ enabled, open });
    const all = [
      {
        id: 11,
        campaign_id: null,
        kind: 'checkout_receipt',
        guest_id: 5,
        topic: 'checkout_receipt',
        recipient_masked: 'j•••@example.com',
        subject: 'Your receipt for booking BK-1',
        status: 'sent',
        attempts: 1,
        last_error: null,
        sent_at: null,
        created_at: new Date().toISOString(),
        tier: 'transactional',
      },
      {
        id: 12,
        campaign_id: 7,
        kind: 'campaign',
        guest_id: 5,
        topic: 'announcement',
        recipient_masked: 'b•••@example.com',
        subject: 'Summer promo',
        status: 'sent',
        attempts: 1,
        last_error: null,
        sent_at: null,
        created_at: new Date().toISOString(),
        tier: 'marketing',
      },
    ];
    const items = !params?.tier || params.tier === 'all'
      ? all
      : all.filter((i) => i.tier === params.tier);
    return {
      data: {
        items,
        total: items.length,
        unread: mocks.serverUnread,
        page: 1,
        page_size: 10,
      },
      isPending: false,
    };
  },
}));

vi.mock('../../router/compat', () => ({
  Link: ({ to, children, ...rest }: { to: string; children?: React.ReactNode }) => (
    <a href={to} {...rest}>
      {children}
    </a>
  ),
}));

vi.mock('../../utils/notificationStore', () => ({
  useNotifications: () => ({ items: [], unreadCount: 0 }),
  markAllRead: (...args: unknown[]) => mocks.markLocalRead(...args),
  clearAll: vi.fn(),
  removeNotification: vi.fn(),
}));

vi.mock('../../features/admin/system/hooks', () => ({
  useStaffNotifications: () => ({
    data: {
      unread: mocks.staffUnread,
      urgent_unread: mocks.urgentUnread,
      action_pending_count: mocks.staffUnread,
      items: mocks.staffItems,
    },
    isPending: false,
  }),
  useMarkStaffNotificationRead: () => ({ mutate: mocks.markOneStaffRead, isPending: false }),
  useMarkAllStaffNotificationsRead: () => ({ mutate: mocks.markAllStaffRead, isPending: false }),
}));

import { NotificationCenter } from './NotificationCenter';

describe('NotificationCenter', () => {
  beforeEach(() => {
    mocks.hasPermission = true;
    mocks.serverUnread = 3;
    mocks.staffUnread = 0;
    mocks.urgentUnread = 0;
    mocks.staffItems = [];
    mocks.feedCalls = [];
    mocks.markOneStaffRead.mockReset();
    mocks.markAllStaffRead.mockReset();
    mocks.markLocalRead.mockReset();
  });

  afterEach(cleanup);

  it('counts actionable staff unread on the bell badge and shows View all for all staff', () => {
    mocks.hasPermission = false;
    mocks.staffUnread = 2;

    render(<NotificationCenter />);

    const bell = screen.getByLabelText('Notifications (2 unread)');
    expect(bell).toBeTruthy();
    // Guest outbox feed must stay disabled without communications:read.
    expect(mocks.feedCalls.length).toBeGreaterThan(0);
    expect(mocks.feedCalls.every((call) => !call.enabled)).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: /^Notifications/ }));
    const viewAll = screen.getByLabelText('View all notifications');
    expect(viewAll.getAttribute('href')).toBe('/notifications');
    expect(mocks.markAllStaffRead).not.toHaveBeenCalled();
  });

  it('defaults to Action Required, avoids auto-mark-read, and marks single items on CTA or check', () => {
    mocks.staffUnread = 2;
    mocks.urgentUnread = 1;
    mocks.staffItems = [
      {
        id: 1,
        kind: 'job_failure',
        subject: 'night_audit',
        title: 'Background job failed: night_audit',
        body: 'db timeout',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'urgent',
        category: 'system',
        action_url: '/jobs',
        action_label_key: 'inspectJobs',
        requires_action: true,
        action_pending: true,
      },
      {
        id: 2,
        kind: 'ekyc_pending_review',
        subject: '42',
        title: 'eKYC awaiting review',
        body: 'Verification #42 submitted.',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'high',
        category: 'ekyc',
        action_url: '/ekyc-admin?application_id=42',
        action_label_key: 'reviewEkyc',
        requires_action: true,
        action_pending: true,
      },
    ];

    render(<NotificationCenter />);

    fireEvent.click(screen.getByRole('button', { name: 'Notifications (2 unread)' }));
    expect(mocks.markAllStaffRead).not.toHaveBeenCalled();
    expect(screen.getByText('Background job failed: night_audit')).toBeTruthy();
    expect(screen.getByText('db timeout')).toBeTruthy();

    const inspectLink = screen.getByRole('link', { name: 'Inspect jobs' });
    expect(inspectLink.getAttribute('href')).toBe('/jobs');
    fireEvent.click(inspectLink);
    expect(mocks.markOneStaffRead).toHaveBeenCalledWith(1);
  });

  it('supports explicit Mark all as read and per-item Mark as read buttons', () => {
    mocks.staffUnread = 1;
    mocks.staffItems = [
      {
        id: 14,
        kind: 'guest_support_conversation',
        subject: 'SUP-2026-0001',
        title: 'Guest support request',
        body: 'A guest opened support conversation SUP-2026-0001.',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'high',
        category: 'support',
        action_url: '/support?search=SUP-2026-0001',
        action_label_key: 'replyGuest',
        requires_action: true,
        action_pending: true,
      },
    ];

    render(<NotificationCenter />);
    fireEvent.click(screen.getByRole('button', { name: 'Notifications (1 unread)' }));

    fireEvent.click(screen.getByRole('button', { name: 'Mark as read' }));
    expect(mocks.markOneStaffRead).toHaveBeenCalledWith(14);

    fireEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
    expect(mocks.markAllStaffRead).toHaveBeenCalledTimes(1);
  });

  it('shows localized bank-transfer claim on default Action Required tab without communications:read', () => {
    mocks.hasPermission = (permission: string) => permission === 'payments:read';
    mocks.serverUnread = 0;
    mocks.staffUnread = 1;
    mocks.urgentUnread = 1;
    mocks.staffItems = [
      {
        id: 9,
        kind: 'bank_transfer_pending',
        subject: 'BK-200',
        title: 'Payment pending approval',
        body: 'raw english body',
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

    render(<NotificationCenter />);

    fireEvent.click(screen.getByRole('button', { name: 'Notifications (1 unread)' }));
    expect(screen.getByText('Payment pending approval')).toBeTruthy();
    expect(screen.getByText('A guest submitted a bank transfer for BK-200. It is waiting for approval.')).toBeTruthy();
    expect(screen.queryByText('raw english body')).toBeNull();
    const review = screen.getByRole('link', { name: 'Review payment' });
    expect(review.getAttribute('href')).toBe('/payment-approvals');
  });

  it('groups the outbox feed on Deliveries tab and filters by tier', () => {
    render(<NotificationCenter />);

    fireEvent.click(screen.getByRole('button', { name: /^Notifications/ }));
    fireEvent.click(screen.getByRole('tab', { name: 'Deliveries' }));

    fireEvent.click(screen.getByRole('tab', { name: 'Transactional' }));
    expect(screen.getByText('Your receipt for booking BK-1')).toBeTruthy();
    expect(screen.queryByText('Summer promo')).toBeNull();

    fireEvent.click(screen.getByRole('tab', { name: 'Marketing' }));
    expect(screen.getByText('Summer promo')).toBeTruthy();
    expect(screen.queryByText(/receipt for booking/)).toBeNull();
  });
});
