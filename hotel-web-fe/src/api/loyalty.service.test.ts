import { describe, it, expect, vi, beforeEach } from 'vitest';
import { buildKyHttpError } from './testSupport/httpError';

// Mock the configured ky instance so no real HTTP happens.
const get = vi.fn();
const post = vi.fn();
const put = vi.fn();
const del = vi.fn();
vi.mock('./client', async () => {
  const actual = await vi.importActual<typeof import('./client')>('./client');
  return {
    ...actual,
    api: {
      get: (...args: any[]) => get(...args),
      post: (...args: any[]) => post(...args),
      put: (...args: any[]) => put(...args),
      delete: (...args: any[]) => del(...args),
    },
  };
});

import { LoyaltyService } from './loyalty.service';

function mockJsonResponse(payload: unknown) {
  return { json: () => Promise.resolve(payload) };
}

/** Methods that chain `.json()` onto the api call must reject from that
 * `.json()` call — a bare rejected mock return value is never awaited. */
function mockJsonRejection(error: unknown) {
  return { json: () => Promise.reject(error) };
}

/** A ky HTTPError as a real failed request throws it: body parsed onto
 *  `data`, response stream already consumed. See testSupport/httpError.ts. */
function buildHttpError(status: number, body: unknown, url = 'http://localhost/api/rewards') {
  return buildKyHttpError(status, body, url);
}

describe('LoyaltyService', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
    del.mockReset();
  });

  describe('getUserLoyaltyMembership', () => {
    it('calls GET loyalty/me and maps enrolled membership details', async () => {
      get.mockReturnValue(
        mockJsonResponse({
          enrolled: true,
          member: {
            id: 7,
            member_number: 'LM-STG-0007',
            status: 'active',
            enrolled_at: '2026-01-01T00:00:00Z',
            available_points: 2450,
            lifetime_points: 6450,
            tier_code: 'gold',
            tier_name: 'Gold',
          },
          tier_progress: {
            next_tier_name: 'Platinum',
            next_tier_minimum: 10000,
            remaining_to_next_tier: 3550,
          },
          recent_activity: [
            {
              id: 91,
              member_id: 7,
              transaction_type: 'earn',
              points_delta: 250,
              balance_after: 2450,
              description: 'Stay points',
              created_at: '2026-11-20T00:00:00Z',
            },
          ],
        }),
      );

      const result = await LoyaltyService.getUserLoyaltyMembership();

      expect(get).toHaveBeenCalledWith('loyalty/me');
      expect(result).toMatchObject({
        id: 7,
        membership_number: 'LM-STG-0007',
        points_balance: 2450,
        lifetime_points: 6450,
        tier_level: 3,
        tier_name: 'Gold',
        points_to_next_tier: 3550,
      });
    });

    it('returns null without 404 when the guest is not enrolled', async () => {
      get.mockReturnValue(
        mockJsonResponse({
          enrolled: false,
          member: null,
          tier_progress: null,
          recent_activity: [],
        }),
      );

      const result = await LoyaltyService.getUserLoyaltyMembership();

      expect(get).toHaveBeenCalledWith('loyalty/me');
      expect(result).toBeNull();
    });
  });

  describe('getLoyaltyRewards', () => {
    it('calls GET loyalty/rewards and normalizes tier/stock fields', async () => {
      const rewards = [{ id: 1, minimum_tier_name: 'Gold', inventory_count: 5 }];
      get.mockReturnValue(mockJsonResponse(rewards));

      const result = await LoyaltyService.getLoyaltyRewards();

      expect(get).toHaveBeenCalledWith('loyalty/rewards');
      expect(result).toEqual([
        {
          id: 1,
          minimum_tier_name: 'Gold',
          minimum_tier_level: 3,
          inventory_count: 5,
          stock_quantity: 5,
        },
      ]);
    });
  });

  describe('redeemReward', () => {
    it('posts to loyalty/rewards/<id>/redeem with optional notes', async () => {
      const response = { id: 1, status: 'pending' };
      post.mockReturnValue(mockJsonResponse(response));

      const result = await LoyaltyService.redeemReward({ reward_id: 5, notes: 'Late checkout' });

      expect(post).toHaveBeenCalledWith('loyalty/rewards/5/redeem', {
        json: { notes: 'Late checkout' },
      });
      expect(result).toEqual(response);
    });
  });

  describe('getRewards (admin)', () => {
    it('calls GET admin/loyalty/rewards with include_inactive when no category is given', async () => {
      get.mockReturnValue(mockJsonResponse([]));

      await LoyaltyService.getRewards();

      expect(get).toHaveBeenCalledWith('admin/loyalty/rewards', {
        searchParams: { include_inactive: true },
      });
    });

    it('forwards category as a search param and normalizes reward fields', async () => {
      const rewards = [{ id: 1, category: 'spa', minimum_tier_id: 2, inventory_count: 8 }];
      get.mockReturnValue(mockJsonResponse(rewards));

      const result = await LoyaltyService.getRewards('spa');

      expect(get).toHaveBeenCalledWith('admin/loyalty/rewards', {
        searchParams: { include_inactive: true, category: 'spa' },
      });
      expect(result).toEqual([
        {
          id: 1,
          category: 'spa',
          minimum_tier_id: 2,
          minimum_tier_level: 2,
          inventory_count: 8,
          stock_quantity: 8,
        },
      ]);
    });
  });

  describe('createReward', () => {
    it('posts mapped backend payload to admin/loyalty/rewards', async () => {
      const input = { name: 'Free Spa', category: 'spa', points_cost: 500, minimum_tier_level: 2, stock_quantity: 12 };
      const created = {
        id: 1,
        name: 'Free Spa',
        category: 'spa',
        points_cost: 500,
        minimum_tier_id: 2,
        inventory_count: 12,
        is_active: true,
        created_at: 'x',
        updated_at: 'x',
      };
      post.mockReturnValue(mockJsonResponse(created));

      const result = await LoyaltyService.createReward(input);

      expect(post).toHaveBeenCalledWith('admin/loyalty/rewards', {
        json: {
          name: 'Free Spa',
          category: 'spa',
          points_cost: 500,
          minimum_tier_id: 2,
          inventory_count: 12,
        },
      });
      expect(result).toMatchObject({ ...created, minimum_tier_level: 2, stock_quantity: 12 });
    });

    it('wraps an HTTPError into an APIError with the server message', async () => {
      post.mockReturnValue(mockJsonRejection(buildHttpError(400, { error: 'Invalid category' })));

      await expect(
        LoyaltyService.createReward({ name: 'X', category: 'bad', points_cost: 1, minimum_tier_level: 1 }),
      ).rejects.toMatchObject({ name: 'APIError', message: 'Invalid category', statusCode: 400 });
    });

    it('falls back to a generic message when the error is not an HTTPError', async () => {
      post.mockReturnValue(mockJsonRejection(new Error('offline')));

      await expect(
        LoyaltyService.createReward({ name: 'X', category: 'spa', points_cost: 1, minimum_tier_level: 1 }),
      ).rejects.toMatchObject({ name: 'APIError', message: 'Something went wrong. Please try again.' });
    });
  });

  describe('updateReward', () => {
    it('puts mapped backend payload to admin/loyalty/rewards/<id>', async () => {
      const input = { points_cost: 600, minimum_tier_level: 3, stock_quantity: 5 };
      const updated = { id: 4, points_cost: 600, minimum_tier_id: 3, inventory_count: 5 };
      put.mockReturnValue(mockJsonResponse(updated));

      const result = await LoyaltyService.updateReward(4, input);

      expect(put).toHaveBeenCalledWith('admin/loyalty/rewards/4', {
        json: { points_cost: 600, minimum_tier_id: 3, inventory_count: 5 },
      });
      expect(result).toMatchObject({ ...updated, minimum_tier_level: 3, stock_quantity: 5 });
    });

    it('wraps an HTTPError into an APIError', async () => {
      put.mockReturnValue(mockJsonRejection(buildHttpError(404, { error: 'Reward not found' })));

      await expect(LoyaltyService.updateReward(999, {})).rejects.toMatchObject({
        name: 'APIError',
        message: 'Reward not found',
        statusCode: 404,
      });
    });
  });

  describe('deleteReward', () => {
    it('soft-deactivates via PUT admin/loyalty/rewards/<id>', async () => {
      put.mockReturnValue(mockJsonResponse({ id: 4, is_active: false }));

      await LoyaltyService.deleteReward(4);

      expect(put).toHaveBeenCalledWith('admin/loyalty/rewards/4', { json: { is_active: false } });
    });

    it('wraps an HTTPError into an APIError', async () => {
      put.mockReturnValue(Promise.reject(buildHttpError(403, { error: 'Not permitted' })));

      await expect(LoyaltyService.deleteReward(4)).rejects.toMatchObject({
        name: 'APIError',
        message: 'Not permitted',
        statusCode: 403,
      });
    });
  });

  describe('getRewardRedemptions', () => {
    it('calls GET admin/loyalty/redemptions', async () => {
      const redemptions = [{ id: 1, guest_name: 'Jane', guest_email: 'a@b.com', reward_name: 'Spa', category: 'spa', points_spent: 500, redeemed_at: 'x', status: 'completed' }];
      get.mockReturnValue(mockJsonResponse(redemptions));

      const result = await LoyaltyService.getRewardRedemptions();

      expect(get).toHaveBeenCalledWith('admin/loyalty/redemptions');
      expect(result).toEqual(redemptions);
    });

    it('wraps an HTTPError into an APIError', async () => {
      get.mockReturnValue(mockJsonRejection(buildHttpError(500, { error: 'Server error' })));

      await expect(LoyaltyService.getRewardRedemptions()).rejects.toMatchObject({
        name: 'APIError',
        message: 'Server error',
        statusCode: 500,
      });
    });
  });
});
