import { api, toApiError } from './client';
import { t } from '../i18n';
import {
  UserLoyaltyMembership,
  LoyaltyReward,
  RedeemRewardInput,
  RewardInput,
  RewardUpdateInput,
  RewardRedemption,
} from '../types';

interface BackendLoyaltyMeResponse {
  enrolled: boolean;
  member: {
    id: number;
    member_number: string;
    status: string;
    enrolled_at: string;
    available_points: number;
    lifetime_points: number;
    tier_code: string;
    tier_name: string;
  } | null;
  tier_progress: {
    next_tier_name?: string | null;
    next_tier_minimum?: number | null;
    remaining_to_next_tier?: number | null;
  } | null;
  recent_activity: Array<{
    id: number;
    member_id: number;
    transaction_type: string;
    points_delta: number;
    balance_after: number;
    description?: string | null;
    created_at: string;
  }>;
}

const TIER_CODE_TO_LEVEL: Record<string, number> = {
  bronze: 1,
  silver: 2,
  gold: 3,
  platinum: 4,
};

function mapLoyaltyMeToMembership(data: BackendLoyaltyMeResponse): UserLoyaltyMembership | null {
  if (!data.enrolled || !data.member) {
    return null;
  }
  const tierLevel = TIER_CODE_TO_LEVEL[data.member.tier_code.toLowerCase()] ?? 1;
  const nextName = data.tier_progress?.next_tier_name;
  return {
    id: data.member.id,
    membership_number: data.member.member_number,
    points_balance: data.member.available_points,
    lifetime_points: data.member.lifetime_points,
    tier_level: tierLevel,
    tier_name: data.member.tier_name,
    status: data.member.status,
    enrolled_date: data.member.enrolled_at,
    ...(nextName && data.tier_progress
      ? {
          next_tier: {
            tier_level: Math.min(tierLevel + 1, 4),
            tier_name: nextName,
            minimum_points: Math.round(data.tier_progress.next_tier_minimum ?? 0),
            benefits: [],
            points_multiplier: 1,
          },
          points_to_next_tier: Math.max(0, Math.round(data.tier_progress.remaining_to_next_tier ?? 0)),
        }
      : {}),
    current_tier_benefits: [],
    recent_transactions: (data.recent_activity ?? []).map((tx) => ({
      id: String(tx.id),
      membership_id: tx.member_id,
      transaction_type:
        tx.transaction_type === 'redeem' || tx.transaction_type === 'expire' || tx.transaction_type === 'adjust'
          ? tx.transaction_type
          : 'earn',
      points_amount: tx.points_delta,
      balance_after: tx.balance_after,
      description: tx.description ?? undefined,
      created_at: tx.created_at,
    })),
  };
}

function mapBackendRewardToLoyaltyReward(raw: LoyaltyReward): LoyaltyReward {
  const inferredTierLevel =
    raw.minimum_tier_level ??
    (raw.minimum_tier_name ? TIER_CODE_TO_LEVEL[raw.minimum_tier_name.toLowerCase()] : undefined) ??
    raw.minimum_tier_id ??
    1;
  return {
    ...raw,
    minimum_tier_level: inferredTierLevel,
    stock_quantity: raw.stock_quantity ?? raw.inventory_count ?? undefined,
  };
}

function toBackendRewardPayload(
  data: Partial<RewardInput & RewardUpdateInput>,
): Record<string, unknown> {
  const minimumTierId = data.minimum_tier_id ?? data.minimum_tier_level;
  const inventoryCount = data.inventory_count ?? data.stock_quantity;
  const payload: Record<string, unknown> = {};
  if (data.name !== undefined) payload.name = data.name;
  if (data.description !== undefined) payload.description = data.description;
  if (data.category !== undefined) payload.category = data.category;
  if (data.points_cost !== undefined) payload.points_cost = data.points_cost;
  if (minimumTierId !== undefined) payload.minimum_tier_id = minimumTierId;
  if (data.requires_approval !== undefined) payload.requires_approval = data.requires_approval;
  if (data.is_active !== undefined) payload.is_active = data.is_active;
  if (inventoryCount !== undefined) payload.inventory_count = inventoryCount;
  if (data.terms_conditions !== undefined) payload.terms_conditions = data.terms_conditions;
  return payload;
}

export class LoyaltyService {
  // User Loyalty Operations
  static async getUserLoyaltyMembership(): Promise<UserLoyaltyMembership | null> {
    const data = await api.get('loyalty/me').json<BackendLoyaltyMeResponse>();
    return mapLoyaltyMeToMembership(data);
  }

  static async getLoyaltyRewards(): Promise<LoyaltyReward[]> {
    const rewards = await api.get('loyalty/rewards').json<LoyaltyReward[]>();
    return rewards.map(mapBackendRewardToLoyaltyReward);
  }

  static async redeemReward(input: RedeemRewardInput): Promise<any> {
    return await api.post(`loyalty/rewards/${input.reward_id}/redeem`, {
      json: { notes: input.notes }
    }).json();
  }

  // Rewards Management (Admin)
  static async getRewards(category?: string): Promise<LoyaltyReward[]> {
    const searchParams: Record<string, string | boolean> = {
      include_inactive: true,
      ...(category ? { category } : {}),
    };
    const rewards = await api.get('admin/loyalty/rewards', { searchParams }).json<LoyaltyReward[]>();
    return rewards.map(mapBackendRewardToLoyaltyReward);
  }

  static async createReward(data: RewardInput): Promise<LoyaltyReward> {
    try {
      const created = await api
        .post('admin/loyalty/rewards', { json: toBackendRewardPayload(data) })
        .json<LoyaltyReward>();
      return mapBackendRewardToLoyaltyReward(created);
    } catch (error) {
      throw toApiError(error, t('generic', undefined, 'errors'));
    }
  }

  static async updateReward(id: number, data: RewardUpdateInput): Promise<LoyaltyReward> {
    try {
      const updated = await api
        .put(`admin/loyalty/rewards/${id}`, { json: toBackendRewardPayload(data) })
        .json<LoyaltyReward>();
      return mapBackendRewardToLoyaltyReward(updated);
    } catch (error) {
      throw toApiError(error, t('generic', undefined, 'errors'));
    }
  }

  static async deleteReward(id: number): Promise<void> {
    try {
      await api.put(`admin/loyalty/rewards/${id}`, { json: { is_active: false } });
    } catch (error) {
      throw toApiError(error, t('generic', undefined, 'errors'));
    }
  }

  static async getRewardRedemptions(status?: string): Promise<RewardRedemption[]> {
    try {
      return await (status
        ? api.get('admin/loyalty/redemptions', { searchParams: { status } })
        : api.get('admin/loyalty/redemptions')
      ).json<RewardRedemption[]>();
    } catch (error) {
      throw toApiError(error, t('generic', undefined, 'errors'));
    }
  }
}
