import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('../../api/client', () => ({
  api: {
    get: (...args: unknown[]) => mocks.get(...args),
    post: (...args: unknown[]) => mocks.post(...args),
  },
}));

import {
  availableGroupActions,
  fetchBookingGroup,
  payBookingGroup,
  reactivateBookingGroup,
  runBookingGroupAction,
  type StaffBookingGroupRoom,
} from './api';

const makeRoom = (bookingId: number, status: string): StaffBookingGroupRoom => ({
  booking_id: bookingId,
  booking_number: `BK-${bookingId}`,
  room_number: String(100 + bookingId),
  room_type_name: 'Deluxe',
  adults: 2,
  children: 0,
  extra_bed_count: 0,
  status,
  payment_status: 'paid',
  total_amount: '100.00',
});

describe('bookings/api', () => {
  beforeEach(() => {
    mocks.get.mockReset();
    mocks.post.mockReset();
  });

  it('fetches group details for a booking', async () => {
    const payload = { group: { id: 5, group_number: 'GBK-5', status: 'active', payment_status: 'paid', room_count: 2, total_amount: '200.00' }, rooms: [] };
    mocks.get.mockReturnValue({ json: () => Promise.resolve(payload) });

    await expect(fetchBookingGroup(10)).resolves.toEqual(payload);
    expect(mocks.get).toHaveBeenCalledWith('bookings/10/group');
  });

  it('runs group actions with and without cancellation reason', async () => {
    mocks.post.mockReturnValue({
      json: () => Promise.resolve({ booking_group_id: 5, action: 'checkin', processed: [10, 11], skipped: [] }),
    });
    await runBookingGroupAction(10, 'checkin');
    expect(mocks.post).toHaveBeenCalledWith('bookings/10/group/checkin', { json: {} });

    await runBookingGroupAction(10, 'cancel', 'Guest requested');
    expect(mocks.post).toHaveBeenCalledWith('bookings/10/group/cancel', { json: { reason: 'Guest requested' } });
  });

  it('records group payment and reactivates voided groups', async () => {
    mocks.post.mockReturnValueOnce({ json: () => Promise.resolve({ amount: '350.00' }) });
    await expect(
      payBookingGroup(10, { payment_method: 'cash', transaction_reference: null, notes: 'Front desk' }),
    ).resolves.toEqual({ amount: '350.00' });
    expect(mocks.post).toHaveBeenCalledWith('bookings/10/group/pay', {
      json: { payment_method: 'cash', transaction_reference: null, notes: 'Front desk' },
    });

    mocks.post.mockReturnValueOnce({ json: () => Promise.resolve({ processed: [10, 11] }) });
    await reactivateBookingGroup(10);
    expect(mocks.post).toHaveBeenCalledWith('bookings/10/group/reactivate');
  });

  it('computes available group actions from room statuses', () => {
    expect(availableGroupActions([makeRoom(1, 'confirmed'), makeRoom(2, 'pending')])).toEqual(['checkin', 'cancel']);
    expect(availableGroupActions([makeRoom(1, 'checked_in'), makeRoom(2, 'confirmed')])).toEqual(['checkin', 'checkout']);
    expect(availableGroupActions([makeRoom(1, 'checked_in'), makeRoom(2, 'voided')])).toEqual(['checkout']);
    expect(availableGroupActions([makeRoom(1, 'voided')])).toEqual([]);
  });
});
