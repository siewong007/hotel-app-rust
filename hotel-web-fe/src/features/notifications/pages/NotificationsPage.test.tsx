import React from 'react';
import { cleanup, render, screen, fireEvent } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  hasPermission: true,
  staffUnread: 0,
  staffItems: [] as Array<Record<string, unknown>>,
  markOneRead: vi.fn(),
  markAllRead: vi.fn(),
  searchQuery: '',
  feed: {
    items: [] as Array<{ id: number; subject: string; status: string }>,
    total: 0,
    unread: 0,
    page: 1,
    page_size: 20,
  },
  feedCalls: [] as Array<{ enabled: boolean; open: boolean }>,
}));

vi.mock('../../../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1, user_type: 'staff' },
    hasPermission: () => mocks.hasPermission,
  }),
}));

vi.mock('../../../router/compat', () => ({
  Link: ({ to, children, ...rest }: { to: string; children?: React.ReactNode }) => (
    <a href={to} {...rest}>
      {children}
    </a>
  ),
  useBrowserSearchParams: () => new URLSearchParams(mocks.searchQuery),
}));

vi.mock('../../admin/system/hooks', () => ({
  useStaffNotifications: () => ({
    data: {
      unread: mocks.staffUnread,
      urgent_unread: 0,
      action_pending_count: mocks.staffUnread,
      items: mocks.staffItems,
    },
    isPending: false,
  }),
  useMarkStaffNotificationRead: () => ({ mutate: mocks.markOneRead, isPending: false }),
  useMarkAllStaffNotificationsRead: () => ({ mutate: mocks.markAllRead, isPending: false }),
}));

vi.mock('../hooks/useDeliveryFeed', () => ({
  useDeliveryFeed: (_params: unknown, enabled: boolean, open: boolean) => {
    mocks.feedCalls.push({ enabled, open });
    return { data: mocks.feed, isPending: false };
  },
}));

import NotificationsPage from './NotificationsPage';
import { expectNoAxeViolations } from '../../../test/axe';

describe('NotificationsPage', () => {
  beforeEach(() => {
    mocks.hasPermission = true;
    mocks.staffUnread = 0;
    mocks.staffItems = [];
    mocks.searchQuery = '';
    mocks.markOneRead.mockReset();
    mocks.markAllRead.mockReset();
    mocks.feed = { items: [], total: 0, unread: 0, page: 1, page_size: 20 };
    mocks.feedCalls = [];
  });

  afterEach(cleanup);

  it('shows Action Inbox without forbidden state when communications:read is absent', () => {
    mocks.hasPermission = false;
    mocks.staffUnread = 1;
    mocks.staffItems = [
      {
        id: 21,
        kind: 'maintenance_critical',
        subject: 'MT-202611-0001',
        title: 'Critical maintenance ticket',
        body: 'Main chiller failure.',
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

    render(<NotificationsPage />);

    expect(screen.queryByText(/do not have permission/i)).toBeNull();
    expect(screen.getByText('Critical maintenance ticket')).toBeTruthy();
    const link = screen.getByRole('link', { name: 'Open maintenance' });
    expect(link.getAttribute('href')).toBe('/housekeeping?tab=maintenance');

    // Never call the delivery feed endpoint without communications:read.
    expect(mocks.feedCalls.length).toBeGreaterThan(0);
    expect(mocks.feedCalls.every((call) => !call.enabled)).toBe(true);
  });

  it('switches to Guest Deliveries tab and exposes the status filter', () => {
    mocks.feed = {
      ...mocks.feed,
      items: [{ id: 31, subject: 'Receipt BK-9', status: 'sent' }],
      total: 1,
    };

    render(<NotificationsPage />);

    fireEvent.click(screen.getByRole('tab', { name: 'Guest Deliveries' }));
    expect(screen.getByText('Receipt BK-9')).toBeTruthy();
    expect(mocks.feedCalls.some((call) => call.enabled && call.open)).toBe(true);

    fireEvent.mouseDown(screen.getByRole('combobox'));
    fireEvent.click(screen.getByRole('option', { name: 'Failed' }));
    expect(screen.getAllByRole('option', { hidden: true }).length).toBeGreaterThan(0);
  });

  it('shows an empty message for the active delivery tier tab when nothing matches', () => {
    render(<NotificationsPage />);

    fireEvent.click(screen.getByRole('tab', { name: 'Guest Deliveries' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Marketing' }));
    expect(screen.getByText('Marketing: nothing here yet')).toBeTruthy();
  });

  it('has no axe violations on Action Inbox and Guest Deliveries', async () => {
    mocks.staffUnread = 1;
    mocks.staffItems = [
      {
        id: 22,
        kind: 'ekyc_pending_review',
        subject: '99',
        title: 'eKYC awaiting review',
        body: 'Verification #99 submitted.',
        created_at: new Date().toISOString(),
        read_at: null,
        priority: 'high',
        category: 'ekyc',
        action_url: '/ekyc-admin?application_id=99',
        action_label_key: 'reviewEkyc',
        requires_action: true,
        action_pending: true,
      },
    ];
    mocks.feed = {
      ...mocks.feed,
      items: [{ id: 31, subject: 'Receipt BK-9', status: 'sent' }],
      total: 1,
    };

    const { container } = render(<NotificationsPage />);
    expect(screen.getByText('eKYC awaiting review')).toBeTruthy();
    await expectNoAxeViolations(container);

    fireEvent.click(screen.getByRole('tab', { name: 'Guest Deliveries' }));
    expect(screen.getByText('Receipt BK-9')).toBeTruthy();
    await expectNoAxeViolations(container);
  });
});
