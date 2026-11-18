import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Chip, Stack, Typography } from '@mui/material';
import { toApiError } from '../../../../api/client';
import {
  availableGroupActions,
  fetchBookingGroup,
  payBookingGroup,
  reactivateBookingGroup,
  runBookingGroupAction,
  type GroupAction,
  type GroupPaymentMethod,
  type StaffBookingGroup,
  type StaffBookingGroupRoom,
} from '../../api';
import { BookingGroupDialogs } from './BookingGroupDialogs';
import { statusLabel, useTranslation } from '../../../../i18n';
import { useCurrency } from '../../../../hooks/useCurrency';
import { toMoneyNumber } from '../../../../utils/money';

export { availableGroupActions, fetchBookingGroup };
export type { StaffBookingGroup, StaffBookingGroupRoom };

interface BookingGroupSectionProps {
  bookingId: number | string;
  /** Staff with bookings:update — shows the group action buttons. */
  canManage?: boolean;
  /** Called after a group action succeeds so the caller can refresh the booking. */
  onChanged?: () => void | Promise<void>;
  /** True once this booking is known to belong to a group. */
  onGroup?: (inGroup: boolean) => void;
}

/** Staff view of the multi-room booking this room belongs to; renders nothing for a single-room booking. */
export function BookingGroupSection({ bookingId, canManage = false, onChanged, onGroup }: BookingGroupSectionProps) {
  const { t } = useTranslation('bookings');
  const { format: formatCurrency } = useCurrency();
  const [data, setData] = useState<{ id: number | string; value: StaffBookingGroup | null } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [pending, setPending] = useState<GroupAction | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [payOpen, setPayOpen] = useState(false);
  const [payMethod, setPayMethod] = useState<GroupPaymentMethod>('cash');
  const [payReference, setPayReference] = useState('');
  const [payNotes, setPayNotes] = useState('');
  const [reactivateOpen, setReactivateOpen] = useState(false);
  const onGroupRef = useRef(onGroup);
  useEffect(() => {
    onGroupRef.current = onGroup;
  }, [onGroup]);
  useEffect(() => {
    let live = true;
    fetchBookingGroup(bookingId)
      .then((value) => {
        if (!live) return;
        setData({ id: bookingId, value });
        onGroupRef.current?.(Boolean(value?.group));
      })
      .catch(() => {
        if (!live) return;
        setData({ id: bookingId, value: null });
        onGroupRef.current?.(false);
      });
    return () => { live = false; };
  }, [bookingId, reloadKey]);

  const confirm = useCallback(async () => {
    if (!pending) return;
    setBusy(true);
    setError(null);
    try {
      const result = await runBookingGroupAction(bookingId, pending, reason.trim() || undefined);
      setNotice(t(`details.group.actions.${pending}.done`, { count: result.processed.length }));
      setPending(null);
      setReason('');
      setReloadKey((key) => key + 1);
      await onChanged?.();
    } catch (err) {
      setError(toApiError(err, t('details.group.actions.failed')).message);
    } finally {
      setBusy(false);
    }
  }, [bookingId, onChanged, pending, reason, t]);

  const value = data?.id === bookingId ? data.value : null;
  if (!value?.group) return null;

  const { group, rooms } = value;
  const actions = canManage ? availableGroupActions(rooms) : [];
  const liveRooms = rooms.filter((room) => room.status !== 'voided');
  const owesMoney = liveRooms.some((room) => ['unpaid', 'unpaid_deposit', 'partial'].includes(room.payment_status));
  const claimOpen = liveRooms.some((room) => room.status === 'pending_confirmation');
  const canPay = canManage && owesMoney && !claimOpen;
  const canReactivateGroup = canManage && rooms.length > 0 && rooms.every((room) => room.status === 'voided');
  const refundWarning = liveRooms.some((room) => room.payment_status === 'paid' || room.payment_status === 'partial');

  const takePayment = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await payBookingGroup(bookingId, {
        payment_method: payMethod,
        transaction_reference: payReference.trim() || null,
        notes: payNotes.trim() || null,
      });
      setNotice(t('details.group.actions.pay.done', { amount: formatCurrency(toMoneyNumber(result.amount)) }));
      setPayOpen(false);
      setPayReference('');
      setPayNotes('');
      setReloadKey((key) => key + 1);
      await onChanged?.();
    } catch (err) {
      setError(toApiError(err, t('details.group.actions.failed')).message);
    } finally {
      setBusy(false);
    }
  };

  const reactivate = async () => {
    setBusy(true);
    setError(null);
    try {
      await reactivateBookingGroup(bookingId);
      setNotice(t('details.group.actions.reactivate.done'));
      setReactivateOpen(false);
      setReloadKey((key) => key + 1);
      await onChanged?.();
    } catch (err) {
      setError(toApiError(err, t('details.group.actions.failed')).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box data-testid="staff-booking-group" sx={{ p: 2.5, borderBottom: '1px solid', borderColor: 'divider' }}>
      <Typography variant="overline" sx={{ color: 'text.secondary', fontWeight: 900 }}>
        {t('details.group.title', { number: group.group_number })}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {t('details.group.summary', { count: group.room_count, total: formatCurrency(toMoneyNumber(group.total_amount)) })}
      </Typography>
      <Stack spacing={1} sx={{ mt: 1 }}>
        {rooms.map((room) => (
          <Stack key={room.booking_id} direction="row" spacing={1} sx={{ justifyContent: 'space-between', alignItems: 'center', opacity: room.status === 'voided' ? 0.6 : 1 }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ fontWeight: String(room.booking_id) === String(bookingId) ? 800 : 600 }}>
                {room.booking_number}
                {room.room_number ? ` · ${t('details.roomNumber', { number: room.room_number })}` : ''}
              </Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {[room.room_type_name, t('details.group.guests', { adults: room.adults, children: room.children }), room.extra_bed_count > 0 ? t('details.group.extraBeds', { count: room.extra_bed_count }) : null].filter(Boolean).join(' · ')}
              </Typography>
            </Box>
            <Stack sx={{ alignItems: 'flex-end' }}>
              <Chip size="small" label={statusLabel(t, 'booking', room.status)} />
              <Typography variant="body2">{formatCurrency(toMoneyNumber(room.total_amount))}</Typography>
            </Stack>
          </Stack>
        ))}
      </Stack>
      {notice && <Alert severity="success" sx={{ mt: 1.5 }} onClose={() => setNotice(null)}>{notice}</Alert>}
      {(actions.length > 0 || canPay || canReactivateGroup) && (
        <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: 'wrap', rowGap: 1 }} data-testid="staff-booking-group-actions">
          {canPay && (
            <Button size="small" variant="contained" onClick={() => { setError(null); setPayOpen(true); }}>
              {t('details.group.actions.pay.button')}
            </Button>
          )}
          {canReactivateGroup && (
            <Button size="small" variant="outlined" onClick={() => { setError(null); setReactivateOpen(true); }}>
              {t('details.group.actions.reactivate.button')}
            </Button>
          )}
          {actions.map((action) => (
            <Button
              key={action}
              size="small"
              variant="outlined"
              color={action === 'cancel' ? 'error' : 'primary'}
              onClick={() => { setError(null); setPending(action); }}
            >
              {t(`details.group.actions.${action}.button`)}
            </Button>
          ))}
        </Stack>
      )}
      <BookingGroupDialogs
        groupNumber={group.group_number}
        pending={pending}
        reason={reason}
        refundWarning={refundWarning}
        payOpen={payOpen}
        payMethod={payMethod}
        payReference={payReference}
        payNotes={payNotes}
        reactivateOpen={reactivateOpen}
        busy={busy}
        error={error}
        onClosePending={() => setPending(null)}
        onReasonChange={setReason}
        onConfirmAction={() => { void confirm(); }}
        onClosePay={() => setPayOpen(false)}
        onPayMethodChange={setPayMethod}
        onPayReferenceChange={setPayReference}
        onPayNotesChange={setPayNotes}
        onSubmitPay={() => { void takePayment(); }}
        onCloseReactivate={() => setReactivateOpen(false)}
        onConfirmReactivate={() => { void reactivate(); }}
      />
    </Box>
  );
}
