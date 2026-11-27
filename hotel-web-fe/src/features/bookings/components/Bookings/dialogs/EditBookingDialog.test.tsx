import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { BookingWithDetails, Room } from '../../../../../types';

const mocks = vi.hoisted(() => ({
  hasPermission: vi.fn(),
  getAllRoomTypes: vi.fn(),
  getAvailableRoomsForDates: vi.fn(),
  listBookingChannels: vi.fn(),
  preview: vi.fn(),
  updateMutateAsync: vi.fn(),
}));

vi.mock('../../../../../auth/AuthContext', () => ({
  useAuth: () => ({ hasPermission: mocks.hasPermission }),
}));

vi.mock('../../../../../hooks/useCurrency', () => ({
  useCurrency: () => ({ format: (value: number) => `RM${Number(value).toFixed(2)}`, symbol: 'RM' }),
}));

vi.mock('../../../../../api', () => ({
  RoomsService: {
    getAllRoomTypes: (...args: unknown[]) => mocks.getAllRoomTypes(...args),
    getAvailableRoomsForDates: (...args: unknown[]) => mocks.getAvailableRoomsForDates(...args),
  },
}));

vi.mock('../../../../../api/reports.service', () => ({
  ReportsService: {
    listBookingChannels: (...args: unknown[]) => mocks.listBookingChannels(...args),
  },
}));

vi.mock('../../../../channels/api', () => ({
  ChannelsApi: {
    preview: (...args: unknown[]) => mocks.preview(...args),
  },
}));

vi.mock('../../../hooks/useBookingQueries', () => ({
  useActiveCompanies: () => ({ data: [], isLoading: false, isFetching: false }),
  useUpdateBooking: () => ({ mutateAsync: mocks.updateMutateAsync, isPending: false }),
}));

import EditBookingDialog from './EditBookingDialog';

function buildBooking(overrides: Partial<BookingWithDetails> = {}): BookingWithDetails {
  return {
    id: 42,
    booking_number: 'BK-42',
    folio_number: 'F-42',
    guest_id: 7,
    guest_name: 'Jane Doe',
    room_id: 'r-101',
    room_number: '101',
    room_type: 'Deluxe',
    check_in_date: '2026-12-01T00:00:00.000Z',
    check_out_date: '2026-12-03T00:00:00.000Z',
    price_per_night: 150,
    total_amount: 300,
    status: 'confirmed',
    payment_status: 'unpaid',
    source: 'online',
    booking_channel_id: 5,
    is_complimentary: false,
    deposit_paid: false,
    ...overrides,
  } as BookingWithDetails;
}

const rooms: Room[] = [
  {
    id: 'r-101',
    room_number: '101',
    room_type: 'Deluxe',
    price_per_night: 150,
    available: true,
    max_occupancy: 2,
  } as Room,
];

function renderDialog(booking: BookingWithDetails) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <EditBookingDialog
        open
        booking={booking}
        rooms={rooms}
        onClose={() => undefined}
        onError={() => undefined}
        onCompleted={() => undefined}
      />
    </QueryClientProvider>,
  );
}

describe('EditBookingDialog channel preview regression', () => {
  beforeEach(() => {
    mocks.hasPermission.mockReturnValue(true);
    mocks.getAllRoomTypes.mockResolvedValue([
      { id: 11, name: 'Deluxe', allows_extra_bed: false, max_extra_beds: 0, extra_bed_charge: 0 },
    ]);
    mocks.getAvailableRoomsForDates.mockResolvedValue(rooms);
    mocks.listBookingChannels.mockResolvedValue([
      {
        id: 5,
        name: 'Booking.com',
        channel_type: 'ota',
        is_active: true,
      },
    ]);
    mocks.preview.mockResolvedValue({
      channel_id: 5,
      channel_name: 'Booking.com',
      room_type_id: 11,
      room_type_name: 'Deluxe',
      rate_plan_id: null,
      check_in: '2026-12-01',
      check_out: '2026-12-03',
      currency: 'MYR',
      nights: [],
      selling_subtotal: '220.00',
      commission_base: '220.00',
      commission_type: 'percentage',
      commission_value: '15.00',
      commission_scope: 'per_booking',
      commission_amount: '33.00',
      net_revenue: '187.00',
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('requests channel pricing preview once when opened and does not loop after settling', async () => {
    renderDialog(buildBooking());

    await waitFor(() => {
      expect(screen.getByText(/Sell RM220\.00/)).toBeTruthy();
    });

    // Editing an unrelated field re-renders the dialog without re-triggering preview.
    const remarksField = screen.getByLabelText(/remarks/i);
    fireEvent.change(remarksField, { target: { value: 'Late arrival' } });
    await new Promise((resolve) => setTimeout(resolve, 40));

    expect(mocks.preview).toHaveBeenCalledTimes(1);
    expect(mocks.preview).toHaveBeenCalledWith({
      channel_id: 5,
      room_type_id: 11,
      check_in: '2026-12-01',
      check_out: '2026-12-03',
    });
  });

  it('skips preview and renders no loading ellipsis when no channel is selected', async () => {
    renderDialog(buildBooking({ booking_channel_id: null, source: 'walk_in' }));

    await waitFor(() => {
      expect(mocks.getAllRoomTypes).toHaveBeenCalledTimes(1);
    });
    await new Promise((resolve) => setTimeout(resolve, 40));

    expect(mocks.preview).not.toHaveBeenCalled();
    expect(screen.queryByText('…')).toBeNull();
  });
});
