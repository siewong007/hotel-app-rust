import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { BookingWithDetails, Room } from '../../../types';
import { addLocalDays, formatLocalDate } from '../../../utils/date';
import { useRoomManagementFilters } from './useRoomManagementFilters';

const today = formatLocalDate();
const tomorrow = formatLocalDate(addLocalDays(new Date(), 1));

function makeRoom(id: string, roomNumber: string, roomType: string): Room {
  return {
    id,
    room_number: roomNumber,
    room_type: roomType,
    price_per_night: 150,
    available: true,
    max_occupancy: 2,
    status: 'available',
    floor: 1,
    is_smoking: false,
  } as Room;
}

function makeBooking(overrides: Partial<BookingWithDetails>): BookingWithDetails {
  return {
    id: '1',
    booking_number: 'BK-9001',
    folio_number: 'F-9001',
    guest_id: 'g-1',
    guest_name: 'Aisha Rahman',
    room_id: 'r-1',
    room_number: '101',
    room_type: 'Deluxe King',
    check_in_date: `${today}T00:00:00.000Z`,
    check_out_date: `${tomorrow}T00:00:00.000Z`,
    total_amount: 150,
    price_per_night: 150,
    status: 'checked_in',
    payment_status: 'paid',
    source: 'walk_in',
    is_complimentary: false,
    deposit_paid: false,
    ...overrides,
  } as BookingWithDetails;
}

describe('useRoomManagementFilters', () => {
  const rooms = [
    makeRoom('r-1', '101', 'Deluxe King'),
    makeRoom('r-2', '102', 'Executive Suite'),
    makeRoom('r-3', '103', 'Standard Twin'),
  ];
  const roomBookings = new Map<string, BookingWithDetails>([
    ['r-1', makeBooking({ room_id: 'r-1', guest_name: 'Aisha Rahman', folio_number: 'F-7788' })],
  ]);
  const reservedBookings = new Map<string, BookingWithDetails>([
    ['r-2', makeBooking({ id: '2', room_id: 'r-2', status: 'confirmed', guest_name: 'Lim Wei', company_name: 'Acme Corp' })],
  ]);

  it('filters rooms by room number, room type, guest name, folio, and company', () => {
    const { result } = renderHook(() =>
      useRoomManagementFilters({ rooms, roomBookings, reservedBookings })
    );

    act(() => result.current.setRoomSearch('Aisha'));
    expect(result.current.filteredRooms.map((r) => r.room_number)).toEqual(['101']);

    act(() => result.current.setRoomSearch('f-7788'));
    expect(result.current.filteredRooms.map((r) => r.room_number)).toEqual(['101']);

    act(() => result.current.setRoomSearch('Acme'));
    expect(result.current.filteredRooms.map((r) => r.room_number)).toEqual(['102']);

    act(() => result.current.setRoomSearch('standard'));
    expect(result.current.filteredRooms.map((r) => r.room_number)).toEqual(['103']);
  });
});
