import React, { useEffect, useMemo } from 'react';
import { Alert, Box, Chip, FormControl, InputLabel, MenuItem, Select, Typography } from '@mui/material';
import { Lock as LockIcon } from '@mui/icons-material';
import { useTranslation } from '../../../../i18n';
import { getHotelSettings } from '../../../../utils/hotelSettings';
import {
  getRoomMethodOptions,
  getSettlementLock,
  isDeskRemainderOpen,
  paymentMethodsMatch,
  presetRoomMethod,
  type RoomMethodContext,
  type SettlementLock,
} from '../../utils/settlementLock';

export interface RoomPaymentMethodSelectProps {
  booking: Parameters<typeof getSettlementLock>[0];
  paymentMethods: readonly string[];
  value: string;
  onChange: (method: string) => void;
  label: string;
  size?: 'small' | 'medium';
  /**
   * Channel payout already recorded. Defaults to the booking's
   * `channel_payout_recorded`; a caller holding the payments list may pass a
   * fresher value.
   */
  channelPaid?: boolean;
}

/** Desk-remainder context for a booking: payout flag + booking channels' names. */
export const getRoomMethodContext = (
  booking: RoomPaymentMethodSelectProps['booking'],
  channelPaid?: boolean,
): RoomMethodContext => ({
  channelPaid: channelPaid ?? Boolean(booking?.channel_payout_recorded),
  channelNames: (getHotelSettings().booking_channels ?? []).map((c) => c.name),
});

const useRoomMethodContext = (
  booking: RoomPaymentMethodSelectProps['booking'],
  channelPaid?: boolean,
): RoomMethodContext => useMemo(() => getRoomMethodContext(booking, channelPaid), [booking, channelPaid]);

/** "Upgrade difference: collect at the desk" hint for a locked booking whose channel has paid. */
export const DeskRemainderHint: React.FC<{ lock: SettlementLock; ctx: RoomMethodContext }> = ({ lock, ctx }) => {
  const { t } = useTranslation('bookings');
  if (!isDeskRemainderOpen(lock, ctx) || lock.kind !== 'locked') return null;
  return (
    <Typography variant="caption" sx={{ color: 'text.secondary' }} data-testid="desk-remainder-hint">
      {t('settlementLock.deskRemainder', { platform: lock.platform })}
    </Typography>
  );
};

/** "Settled via {platform}" chip shown next to a locked tender. */
export const SettledViaChip: React.FC<{ lock: SettlementLock; size?: 'small' | 'medium' }> = ({ lock, size = 'small' }) => {
  const { t } = useTranslation('bookings');
  if (lock.kind !== 'locked') return null;
  return (
    <Chip
      icon={<LockIcon />}
      size={size}
      color="info"
      variant="outlined"
      label={t('settlementLock.settledVia', { platform: lock.platform })}
      data-testid="settled-via-chip"
      sx={{ maxWidth: '100%' }}
    />
  );
};

/** Alert for an online booking whose room charge cannot be recorded yet. */
export const SettlementLockAlert: React.FC<{ lock: SettlementLock }> = ({ lock }) => {
  const { t } = useTranslation('bookings');
  if (lock.kind === 'missing_channel') {
    return <Alert severity="error" sx={{ mb: 1 }}>{t('settlementLock.missingChannel')}</Alert>;
  }
  if (lock.kind === 'unconfigured') {
    return <Alert severity="error" sx={{ mb: 1 }}>{t('settlementLock.unconfigured', { platform: lock.platform })}</Alert>;
  }
  return null;
};

/**
 * Room-charge tender picker. On an online booking it offers only the
 * channel's settlement tender(s), presets the value to an allowed one, and is
 * disabled when there is a single choice. Once the channel payout is
 * recorded, the hotel's desk tenders are offered too for the remainder (e.g. a
 * front-desk upgrade). Walk-in/phone bookings get the full configured list,
 * unchanged.
 */
const RoomPaymentMethodSelect: React.FC<RoomPaymentMethodSelectProps> = ({
  booking,
  paymentMethods,
  value,
  onChange,
  label,
  size,
  channelPaid,
}) => {
  const lock = useMemo(() => getSettlementLock(booking), [booking]);
  const ctx = useRoomMethodContext(booking, channelPaid);
  const options = useMemo(() => getRoomMethodOptions(lock, paymentMethods, ctx), [lock, paymentMethods, ctx]);
  const locked = lock.kind !== 'unlocked';

  useEffect(() => {
    if (!locked) return;
    const preset = presetRoomMethod(lock, paymentMethods, value, ctx);
    if (preset && preset !== value) onChange(preset);
  }, [lock, locked, paymentMethods, value, onChange, ctx]);

  const selectValue = locked ? (options.find((m) => paymentMethodsMatch(m, value)) ?? '') : value;
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, minWidth: 0 }}>
      <SettlementLockAlert lock={lock} />
      <FormControl fullWidth size={size} disabled={locked && options.length <= 1}>
        <InputLabel>{label}</InputLabel>
        <Select
          value={selectValue}
          label={label}
          onChange={(e) => onChange(String(e.target.value))}
          inputProps={{ 'data-testid': 'room-payment-method' }}
        >
          {options.map((m) => (
            <MenuItem key={m} value={m}>{m}</MenuItem>
          ))}
        </Select>
      </FormControl>
      {lock.kind === 'locked' && (
        <Box>
          <SettledViaChip lock={lock} />
        </Box>
      )}
      <DeskRemainderHint lock={lock} ctx={ctx} />
    </Box>
  );
};

export default RoomPaymentMethodSelect;
