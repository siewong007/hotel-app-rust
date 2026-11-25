import { beforeEach, describe, expect, it, vi } from 'vitest';

const get = vi.fn();
const post = vi.fn();

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    api: {
      get: (...args: unknown[]) => get(...args),
      post: (...args: unknown[]) => post(...args),
    },
  };
});

import { GuestPortalService } from '../api/guestPortal.service';
import { BookingsService } from '../api/bookings.service';
import { EkycService } from '../api/ekyc.service';
import { LoyaltyService } from '../api/loyalty.service';
import { PublicBookingApi } from '../features/guest-portal/booking/api';
import { payBookingGroup } from '../features/bookings/api';
import type { Booking, CheckInAdvisory } from '../types';

function mockJsonResponse<T>(data: T) {
  return {
    json: vi.fn().mockResolvedValue(data),
    blob: vi.fn().mockResolvedValue(new Blob([JSON.stringify(data)], { type: 'application/json' })),
  };
}

function mockBlobResponse(blob: Blob) {
  return {
    json: vi.fn().mockRejectedValue(new Error('not json')),
    blob: vi.fn().mockResolvedValue(blob),
  };
}

describe('Core Hotel Business Flows (Happy Path Integration)', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  describe('Flow 1: Anonymous Guest Public Booking & Quote', () => {
    it('searches room inventory, gets a price quote, and reserves a room anonymously', async () => {
      const mockOffers = [
        {
          room_type_id: 101,
          name: 'Deluxe King',
          available_rooms: 3,
          max_occupancy: 2,
          total_price: 360,
          currency: 'MYR',
        },
      ];
      get.mockReturnValue(mockJsonResponse(mockOffers));

      const offers = await PublicBookingApi.search({
        check_in_date: '2026-12-01',
        check_out_date: '2026-12-03',
        adults: 2,
        children: 0,
      });

      expect(get).toHaveBeenCalledWith('booking/offers', {
        headers: { 'x-skip-api-notification': 'true' },
        searchParams: {
          check_in_date: '2026-12-01',
          check_out_date: '2026-12-03',
          adults: '2',
          children: '0',
        },
      });
      expect(offers).toEqual(mockOffers);

      const mockQuote = {
        room_type_id: 101,
        nights: 2,
        base_rate: 300,
        tax_amount: 30,
        tourism_tax: 20,
        extra_bed_charge: 0,
        total_amount: 350,
        currency: 'MYR',
      };
      post.mockReturnValue(mockJsonResponse(mockQuote));

      const quote = await PublicBookingApi.quote({
        room_type_id: 101,
        check_in_date: '2026-12-01',
        check_out_date: '2026-12-03',
        adults: 2,
        children: 0,
        tourism_type: 'local',
      });

      expect(post).toHaveBeenCalledWith('booking/quote', {
        headers: { 'x-skip-api-notification': 'true' },
        json: {
          room_type_id: 101,
          check_in_date: '2026-12-01',
          check_out_date: '2026-12-03',
          adults: 2,
          children: 0,
          tourism_type: 'local',
        },
      });
      expect(quote).toEqual(mockQuote);

      const mockConfirmation = {
        booking_id: 88,
        booking_number: 'BK-2026-0088',
        guest_name: 'Wong Ah Kow',
        email: 'wong@example.com',
        check_in_date: '2026-12-01',
        check_out_date: '2026-12-03',
        total_amount: 350,
        status: 'pending',
        payment_status: 'unpaid',
        payment_url: 'https://payment.example.com/checkout/88',
        booking_token: 'tok_secret_88',
      };
      post.mockReturnValue(mockJsonResponse(mockConfirmation));

      const confirmation = await PublicBookingApi.create({
        client_request_id: 'req_client_88',
        room_type_id: 101,
        check_in_date: '2026-12-01',
        check_out_date: '2026-12-03',
        adults: 2,
        children: 0,
        expected_total: 350,
        guest: {
          first_name: 'Ah Kow',
          email: 'wong@example.com',
          phone: '+60123456789',
          tourism_type: 'local',
        },
        consents: [
          {
            document: 'terms_of_service',
            version: '2026-09-01',
            granted: true,
            locale: 'en',
          },
        ],
        marketing_opt_in: false,
        special_requests: 'High floor preferred',
      });

      expect(post).toHaveBeenCalledWith('booking/reservations', {
        headers: { 'x-skip-api-notification': 'true' },
        json: {
          client_request_id: 'req_client_88',
          room_type_id: 101,
          check_in_date: '2026-12-01',
          check_out_date: '2026-12-03',
          adults: 2,
          children: 0,
          expected_total: 350,
          guest: {
            first_name: 'Ah Kow',
            email: 'wong@example.com',
            phone: '+60123456789',
            tourism_type: 'local',
          },
          consents: [
            {
              document: 'terms_of_service',
              version: '2026-09-01',
              granted: true,
              locale: 'en',
            },
          ],
          marketing_opt_in: false,
          special_requests: 'High floor preferred',
        },
      });
      expect(confirmation.booking_id).toBe(88);
      expect(confirmation.booking_number).toBe('BK-2026-0088');
    });
  });

  describe('Flow 2: Guest Portal Token Verification & Details Lookup', () => {
    it('verifies a booking reference and fetches reservation details via booking token header', async () => {
      const verifyPayload = {
        booking_number: 'BK-2026-0088',
        name: 'Wong',
      };
      const verifyResponse = {
        token: 'portal-session-token-xyz',
        expires_at: '2026-12-04T00:00:00Z',
        booking_id: '88',
      };
      post.mockReturnValue(mockJsonResponse(verifyResponse));

      const verification = await GuestPortalService.verify(verifyPayload);

      expect(post).toHaveBeenCalledWith('guest-portal/verify', {
        headers: { 'x-skip-api-notification': 'true' },
        json: verifyPayload,
      });
      expect(verification.token).toBe('portal-session-token-xyz');

      const mockBookingRecord: Partial<Booking> = {
        id: '88',
        booking_number: 'BK-2026-0088',
        check_in_date: '2026-12-01',
        check_out_date: '2026-12-03',
        total_amount: 350,
        status: 'confirmed',
        payment_status: 'paid',
      };
      const getBookingPayload = {
        booking: mockBookingRecord as Booking,
        guest: {
          id: '12',
          first_name: 'Ah Kow',
          last_name: 'Wong',
          email: 'wong@example.com',
          phone_number: '+60123456789',
        },
        ekyc_summary: {
          status: 'verified',
          has_pending_review: false,
          verified_at: '2026-11-15T10:00:00Z',
        },
      };
      get.mockReturnValue(mockJsonResponse(getBookingPayload));

      const bookingDetails = await GuestPortalService.getBooking('portal-session-token-xyz');

      expect(get).toHaveBeenCalledWith('guest-portal/booking', {
        headers: {
          'X-Booking-Access-Token': 'portal-session-token-xyz',
          'x-skip-api-notification': 'true',
        },
      });
      expect(bookingDetails.booking.id).toBe('88');
      expect(bookingDetails.guest.last_name).toBe('Wong');
      expect(bookingDetails.ekyc_summary?.status).toBe('verified');
    });
  });

  describe('Flow 3: Staff Front Desk Booking Lifecycle & Group Folio Settlement', () => {
    it('creates a booking, inspects check-in advisory, checks in guest, and records group payment', async () => {
      const createdBooking: Partial<Booking> = {
        id: '99',
        booking_number: 'BK-STAFF-99',
        room_id: '102',
        guest_id: '15',
        status: 'confirmed',
        payment_status: 'unpaid',
        total_amount: 500,
      };
      post.mockReturnValue(mockJsonResponse(createdBooking));

      const newBooking = await BookingsService.createBooking({
        guest_id: 15,
        room_id: '102',
        check_in_date: '2026-12-05',
        check_out_date: '2026-12-07',
        number_of_guests: 2,
        payment_method: 'cash',
        payment_status: 'unpaid',
      });

      expect(post).toHaveBeenCalledWith('bookings', {
        json: {
          guest_id: 15,
          room_id: 102,
          check_in_date: '2026-12-05',
          check_out_date: '2026-12-07',
          number_of_guests: 2,
          payment_method: 'cash',
          payment_status: 'unpaid',
        },
      });
      expect(newBooking.booking_number).toBe('BK-STAFF-99');

      const mockAdvisory: CheckInAdvisory = {
        needs_attention: false,
        prior_total_bookings: 3,
        prior_company_bookings: 0,
        suggested_company_id: null,
        suggested_company_name: null,
      };
      get.mockReturnValue(mockJsonResponse(mockAdvisory));

      const advisory = await BookingsService.getCheckInAdvisory('99');
      expect(get).toHaveBeenCalledWith('bookings/99/checkin-advisory');
      expect(advisory.needs_attention).toBe(false);
      expect(advisory.prior_total_bookings).toBe(3);

      const checkedInBooking: Partial<Booking> = {
        ...createdBooking,
        status: 'checked_in',
      };
      post.mockReturnValue(mockJsonResponse(checkedInBooking));

      const checkInResult = await BookingsService.checkInGuest('99', {
        booking_update: { remarks: 'Key card issued at reception' },
      });
      expect(post).toHaveBeenCalledWith('bookings/99/checkin', {
        json: { booking_update: { remarks: 'Key card issued at reception' } },
      });
      expect(checkInResult.status).toBe('checked_in');

      const groupPayResult = { amount: 500 };
      post.mockReturnValue(mockJsonResponse(groupPayResult));

      const settlement = await payBookingGroup('99', {
        payment_method: 'duitnow',
        transaction_reference: 'DN-2026-9901',
        notes: 'Group folio balance settled by organizer',
      });
      expect(post).toHaveBeenCalledWith('bookings/99/group/pay', {
        json: {
          payment_method: 'duitnow',
          transaction_reference: 'DN-2026-9901',
          notes: 'Group folio balance settled by organizer',
        },
      });
      expect(settlement.amount).toBe(500);
    });
  });

  describe('Flow 4: Staff Compliance (eKYC) Document Inspection & Loyalty Analytics', () => {
    it('fetches an encrypted identity document blob, approves verification, and queries loyalty metrics', async () => {
      const mockDocBlob = new Blob(['sample-id-bytes'], { type: 'image/jpeg' });
      get.mockReturnValue(mockBlobResponse(mockDocBlob));

      const docBlob = await EkycService.getApplicationDocumentBlob(42, 'id_front');
      expect(get).toHaveBeenCalledWith('ekyc/admin/applications/42/documents/id_front');
      expect(docBlob.size).toBe(mockDocBlob.size);
      expect(docBlob.type).toBe('image/jpeg');

      const reviewResponse = {
        summary: {
          id: 42,
          guest_id: 15,
          guest_name: 'Alice Tan',
          status: 'approved',
          version: 2,
        },
      };
      post.mockReturnValue(mockJsonResponse(reviewResponse));

      const review = await EkycService.performReviewAction(42, {
        action: 'approve',
        expected_version: 1,
        reason_code: 'id_verified',
        reason: 'NRIC hologram and face match confirmed',
        self_checkin_enabled: true,
      });
      expect(post).toHaveBeenCalledWith('ekyc/admin/applications/42/actions', {
        json: {
          action: 'approve',
          expected_version: 1,
          reason_code: 'id_verified',
          reason: 'NRIC hologram and face match confirmed',
          self_checkin_enabled: true,
        },
      });
      expect(review.summary.status).toBe('approved');

      const mockRewards = [
        {
          id: 1,
          name: 'Late Checkout',
          category: 'service',
          points_cost: 500,
          minimum_tier_id: 2,
          minimum_tier_name: 'Silver',
          inventory_count: 10,
          is_active: true,
        },
      ];
      get.mockReturnValue(mockJsonResponse(mockRewards));

      const rewards = await LoyaltyService.getRewards();
      expect(get).toHaveBeenCalledWith('admin/loyalty/rewards', {
        searchParams: { include_inactive: true },
      });
      expect(rewards).toHaveLength(1);
      expect(rewards[0].minimum_tier_level).toBe(2);
      expect(rewards[0].stock_quantity).toBe(10);
    });
  });
});
