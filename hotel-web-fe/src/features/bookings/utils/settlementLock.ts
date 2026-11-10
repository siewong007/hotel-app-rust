import type { BookingWithDetails } from '../../../types';

/**
 * Settlement lock — mirrors `hotel-app-be/src/modules/payments/settlement_lock.rs`.
 *
 * A booking taken through an online channel (source `online`/`website`, or a
 * linked channel of type `ota`/`website`) is settled by that channel: its
 * room charge (`payment_type = 'booking'`) may only be recorded with the
 * channel's configured tender(s). Deposits and folio extras stay open. The
 * backend is the enforcement point; this helper only presets and restricts
 * the UI so staff never pick a tender the server will refuse.
 */
export type SettlementLock =
  | { kind: 'unlocked' }
  | { kind: 'locked'; platform: string; allowedMethods: string[] }
  | { kind: 'missing_channel' }
  | { kind: 'unconfigured'; platform: string };

export const WEBSITE_DEFAULT_METHODS = ['Bank Transfer', 'PayPal'];

type LockFields = Pick<
  BookingWithDetails,
  | 'source'
  | 'booking_channel_name'
  | 'booking_channel_type'
  | 'booking_channel_settlement_methods'
  | 'channel_payout_recorded'
>;

/**
 * Desk-remainder context. Once the channel payout for the room charge is
 * recorded (`channelPaid`), anything still owed — typically a front-desk
 * upgrade or room change — may be collected with the hotel's own tenders,
 * capped at the balance by the backend. `channelNames` lists booking-channel
 * names whose tenders stay refused (another OTA is never a desk tender).
 */
export interface RoomMethodContext {
  channelPaid?: boolean;
  channelNames?: readonly string[];
}

/**
 * Lower-case, keep letters/digits, drop a trailing `com` (same rule as the
 * backend): `Booking.Com` → `booking`, `Traveloka.Com` → `traveloka`,
 * `bank_transfer` → `banktransfer`.
 */
export const normalizePaymentMethod = (method: string | null | undefined): string => {
  const compact = (method ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
  return compact.length > 3 && compact.endsWith('com') ? compact.slice(0, -3) : compact;
};

export const paymentMethodsMatch = (a: string | null | undefined, b: string | null | undefined): boolean => {
  const left = normalizePaymentMethod(a);
  return left.length > 0 && left === normalizePaymentMethod(b);
};

const isToken = (value: string | null | undefined, token: string) =>
  (value ?? '').trim().toLowerCase() === token;

export const getSettlementLock = (booking: Partial<LockFields> | null | undefined): SettlementLock => {
  if (!booking) return { kind: 'unlocked' };
  const sourceOnline = isToken(booking.source, 'online');
  const sourceWebsite = isToken(booking.source, 'website');
  const channelOta = isToken(booking.booking_channel_type, 'ota');
  const channelWebsite = isToken(booking.booking_channel_type, 'website');
  if (!(sourceOnline || sourceWebsite || channelOta || channelWebsite)) return { kind: 'unlocked' };

  const configured = (booking.booking_channel_settlement_methods ?? [])
    .map((m) => m.trim())
    .filter(Boolean);
  const channelName = booking.booking_channel_name?.trim();

  if (channelName) {
    if (configured.length > 0) return { kind: 'locked', platform: channelName, allowedMethods: configured };
    if (channelWebsite) return { kind: 'locked', platform: channelName, allowedMethods: WEBSITE_DEFAULT_METHODS };
    return { kind: 'unconfigured', platform: channelName };
  }
  if (sourceWebsite) return { kind: 'locked', platform: 'Direct Website', allowedMethods: WEBSITE_DEFAULT_METHODS };
  return { kind: 'missing_channel' };
};

export const isSettlementLocked = (lock: SettlementLock) => lock.kind !== 'unlocked';

/**
 * Whether the desk may collect the remaining room balance with its own
 * tenders: the booking is locked to a channel and that channel's payout is
 * already recorded.
 */
export const isDeskRemainderOpen = (lock: SettlementLock, ctx?: RoomMethodContext): boolean =>
  lock.kind === 'locked' && Boolean(ctx?.channelPaid);

/** Whether the booking's channel payout is recorded, from the booking flag or a payments list. */
interface PayoutPaymentRow {
  payment_method?: string | null;
  payment_type?: string | null;
  payment_status?: string | null;
  status?: string | null;
}
type LockBooking = Partial<LockFields> | null | undefined;

export const hasChannelPayout = (
  lock: SettlementLock,
  booking: LockBooking,
  payments?: readonly PayoutPaymentRow[],
): boolean => {
  if (lock.kind !== 'locked') return false;
  if (booking?.channel_payout_recorded) return true;
  return (payments ?? []).some((p) =>
    (p.payment_status ?? p.status) === 'completed'
    && (p.payment_type ?? 'booking') === 'booking'
    && lock.allowedMethods.some((allowed) => paymentMethodsMatch(allowed, p.payment_method)));
};

const isOtherChannelTender = (lock: SettlementLock, method: string, ctx?: RoomMethodContext): boolean =>
  lock.kind === 'locked'
  && (ctx?.channelNames ?? []).some((name) => paymentMethodsMatch(name, method));

/** Whether `method` may settle the room charge under `lock`. */
export const isRoomMethodAllowed = (lock: SettlementLock, method: string, ctx?: RoomMethodContext): boolean => {
  if (lock.kind === 'unlocked') return true;
  if (lock.kind !== 'locked') return false;
  if (lock.allowedMethods.some((allowed) => paymentMethodsMatch(allowed, method))) return true;
  return isDeskRemainderOpen(lock, ctx)
    && normalizePaymentMethod(method).length > 0
    && !isOtherChannelTender(lock, method, ctx);
};

/**
 * Room-charge tender options for a dialog: every configured method when
 * unlocked; otherwise only the allowed tenders, spelled the way the hotel's
 * configured payment-method list spells them (so reports stay consistent).
 * Once the channel payout is recorded, the hotel's desk tenders follow the
 * channel tender(s) for collecting the remainder.
 */
export const getRoomMethodOptions = (
  lock: SettlementLock,
  configuredMethods: readonly string[],
  ctx?: RoomMethodContext,
): string[] => {
  if (lock.kind === 'unlocked') return [...configuredMethods];
  if (lock.kind !== 'locked') return [];
  const channel = lock.allowedMethods.map(
    (allowed) => configuredMethods.find((m) => paymentMethodsMatch(m, allowed)) ?? allowed,
  );
  if (!isDeskRemainderOpen(lock, ctx)) return channel;
  const desk = configuredMethods.filter((m) =>
    !channel.some((c) => paymentMethodsMatch(c, m)) && isRoomMethodAllowed(lock, m, ctx));
  return [...channel, ...desk];
};

/**
 * The method a picker should hold: `current` when allowed, else (desk
 * remainder open) the first desk tender, else the first allowed option.
 */
export const presetRoomMethod = (
  lock: SettlementLock,
  configuredMethods: readonly string[],
  current: string,
  ctx?: RoomMethodContext,
): string => {
  if (lock.kind === 'unlocked') return current;
  const options = getRoomMethodOptions(lock, configuredMethods, ctx);
  const matched = options.find((m) => paymentMethodsMatch(m, current));
  if (matched) return matched;
  return initialRoomMethod(lock, configuredMethods, current, ctx);
};

/**
 * The method a payment form should open with. With the desk remainder open
 * the channel has already paid its part, so a desk tender (`current` when it
 * is one, else the first) is preferred over the channel tender.
 */
export const initialRoomMethod = (
  lock: SettlementLock,
  configuredMethods: readonly string[],
  current: string,
  ctx?: RoomMethodContext,
): string => {
  if (lock.kind === 'unlocked') return current;
  const options = getRoomMethodOptions(lock, configuredMethods, ctx);
  if (lock.kind === 'locked' && isDeskRemainderOpen(lock, ctx)) {
    const desk = options.filter((m) => !lock.allowedMethods.some((a) => paymentMethodsMatch(a, m)));
    const pick = desk.find((m) => paymentMethodsMatch(m, current)) ?? desk[0];
    if (pick) return pick;
  }
  return options.find((m) => paymentMethodsMatch(m, current)) ?? options[0] ?? current;
};
