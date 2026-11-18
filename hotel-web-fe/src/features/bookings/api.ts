import { api } from '../../api/client';

export interface StaffBookingGroupRoom {
  booking_id: number;
  booking_number: string;
  room_number?: string | null;
  room_type_name?: string | null;
  adults: number;
  children: number;
  extra_bed_count: number;
  status: string;
  payment_status: string;
  total_amount: string | number;
}

export interface StaffBookingGroup {
  group: {
    id: number;
    group_number: string;
    status: string;
    payment_status: string;
    room_count: number;
    total_amount: string | number;
  } | null;
  rooms: StaffBookingGroupRoom[];
}

export type GroupAction = 'cancel' | 'checkin' | 'checkout';

export interface GroupActionResult {
  booking_group_id: number;
  action: GroupAction;
  processed: number[];
  skipped: { booking_id: number; status: string }[];
}

export const GROUP_PAY_METHODS = ['cash', 'card', 'bank_transfer', 'duitnow', 'online_banking'] as const;
export type GroupPaymentMethod = (typeof GROUP_PAY_METHODS)[number];

export interface GroupPayPayload {
  payment_method: GroupPaymentMethod;
  transaction_reference: string | null;
  notes: string | null;
}

export interface GroupPayResult {
  amount: string | number;
}

const IN_HOUSE_STATUSES = ['checked_in', 'auto_checked_in', 'late_checkout'];
const DEPARTED_STATUSES = ['checked_out', 'completed'];

/** Fetch the multi-room group summary and sibling rooms for a booking, or `null` for a single-room booking. */
export async function fetchBookingGroup(bookingId: number | string): Promise<StaffBookingGroup | null> {
  return await api.get(`bookings/${bookingId}/group`).json<StaffBookingGroup | null>();
}

/** Run a staff group lifecycle action (`checkin`, `checkout`, `cancel`) across the group `bookingId` belongs to. */
export async function runBookingGroupAction(
  bookingId: number | string,
  action: GroupAction,
  reason?: string,
): Promise<GroupActionResult> {
  return await api
    .post(`bookings/${bookingId}/group/${action}`, { json: action === 'cancel' ? { reason: reason || null } : {} })
    .json<GroupActionResult>();
}

/** Record one payment covering all unpaid rooms in the booking group. */
export async function payBookingGroup(
  bookingId: number | string,
  payload: GroupPayPayload,
): Promise<GroupPayResult> {
  return await api.post(`bookings/${bookingId}/group/pay`, { json: payload }).json<GroupPayResult>();
}

/** Reactivate every voided room in the booking group together. */
export async function reactivateBookingGroup(bookingId: number | string): Promise<void> {
  await api.post(`bookings/${bookingId}/group/reactivate`).json();
}

/** Compute which group actions apply to the current room statuses — mirrors the server's planning rules. */
export function availableGroupActions(rooms: StaffBookingGroupRoom[]): GroupAction[] {
  const live = rooms.filter((room) => room.status !== 'voided');
  const arrivedOrGone = live.some(
    (room) => IN_HOUSE_STATUSES.includes(room.status) || DEPARTED_STATUSES.includes(room.status),
  );
  const actions: GroupAction[] = [];
  if (live.some((room) => !IN_HOUSE_STATUSES.includes(room.status) && !DEPARTED_STATUSES.includes(room.status))) {
    actions.push('checkin');
  }
  if (live.some((room) => IN_HOUSE_STATUSES.includes(room.status))) actions.push('checkout');
  if (live.length > 0 && !arrivedOrGone) actions.push('cancel');
  return actions;
}
