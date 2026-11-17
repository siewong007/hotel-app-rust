import React, { useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import type { BookingWithDetails } from '../../../../types';
import { emitApiNotification } from '../../../../utils/apiNotifications';
import { addLocalDays, formatLocalDate, parseLocalDate } from '../../../../utils/date';
import { getErrorMessage } from '../../utils/bookingPageUtils';
import { useTranslation } from '../../../../i18n';
import { useUpdateBooking } from '../../hooks/useBookingQueries';

interface QuickEditFields {
  check_in_date: string;
  check_out_date: string;
  remarks: string;
  special_requests: string;
}

const initialFields = (booking: BookingWithDetails): QuickEditFields => ({
  check_in_date: booking.check_in_date.split('T')[0],
  check_out_date: booking.check_out_date.split('T')[0],
  remarks: booking.remarks ?? '',
  special_requests: booking.special_requests ?? '',
});

/**
 * Everything else (status, channel, company, rate, room, extra beds) stays in
 * the full Edit dialog one row of actions below; unlike that dialog this does
 * NOT re-run the room-availability picker on date changes — the backend still
 * validates conflicts. Remounted per booking via `key` so field state always
 * starts from that booking's current values.
 */
/**
 * Inline edit for the fields front-desk staff change most — stay dates and the
 * free-text notes. Shared by the /bookings drawer and the /bookings/$bookingId
 * page so the full page is not a strict subset of the preview that opens it.
 */
const BookingQuickEditSection: React.FC<{
  booking: BookingWithDetails;
  onError: (message: string) => void;
  onCompleted: () => Promise<void> | void;
}> = ({ booking, onError, onCompleted }) => {
  const { t } = useTranslation('bookings');
  const updateBooking = useUpdateBooking();
  const baseline = initialFields(booking);
  const [fields, setFields] = useState<QuickEditFields>(() => baseline);
  const [saving, setSaving] = useState(false);

  const isDirty =
    fields.check_in_date !== baseline.check_in_date ||
    fields.check_out_date !== baseline.check_out_date ||
    fields.remarks !== baseline.remarks ||
    fields.special_requests !== baseline.special_requests;

  const hasInvalidDateOrder = Boolean(
    fields.check_in_date && fields.check_out_date && fields.check_out_date <= fields.check_in_date
  );

  const minCheckOutDate = fields.check_in_date
    ? formatLocalDate(addLocalDays(parseLocalDate(fields.check_in_date), 1))
    : '';

  const canShorten = Boolean(
    fields.check_out_date && minCheckOutDate && fields.check_out_date > minCheckOutDate
  );

  const shiftCheckOut = (deltaDays: number) => {
    if (!fields.check_out_date) return;
    const nextDate = formatLocalDate(addLocalDays(parseLocalDate(fields.check_out_date), deltaDays));
    if (minCheckOutDate && nextDate < minCheckOutDate) return;
    setFields((prev) => ({ ...prev, check_out_date: nextDate }));
  };

  const handleSave = async () => {
    if (hasInvalidDateOrder) return;
    try {
      setSaving(true);
      await updateBooking.mutateAsync({
        bookingId: booking.id,
        data: {
          check_in_date: fields.check_in_date,
          check_out_date: fields.check_out_date,
          remarks: fields.remarks,
          special_requests: fields.special_requests,
        },
      });
      emitApiNotification({ severity: 'success', message: t('details.quickEditSuccess') });
      await onCompleted();
    } catch (err: unknown) {
      onError(getErrorMessage(err) || t('details.quickEditFailed'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box sx={{ p: 2.5, borderBottom: '1px solid', borderColor: 'divider' }}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="overline" sx={{ color: 'text.secondary', fontWeight: 900 }}>
          {t('details.quickEdit')}
        </Typography>
        {isDirty && (
          <Chip
            size="small"
            color="warning"
            variant="outlined"
            label={t('details.unsaved')}
            sx={{ height: 20, fontSize: '0.68rem', fontWeight: 700 }}
          />
        )}
      </Stack>
      <Stack spacing={1.5} sx={{ mt: 1 }}>
        <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: 'wrap', minWidth: 0 }}>
          <TextField
            fullWidth
            size="small"
            label={t('edit.checkInDate')}
            type="date"
            value={fields.check_in_date}
            onChange={(e) => setFields((prev) => ({ ...prev, check_in_date: e.target.value }))}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ flex: '1 1 9rem', minWidth: 0 }}
          />
          <TextField
            fullWidth
            size="small"
            label={t('edit.checkOutDate')}
            type="date"
            value={fields.check_out_date}
            onChange={(e) => setFields((prev) => ({ ...prev, check_out_date: e.target.value }))}
            error={hasInvalidDateOrder}
            helperText={hasInvalidDateOrder ? t('details.invalidDateOrder') : undefined}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ flex: '1 1 9rem', minWidth: 0 }}
          />
        </Stack>
        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', justifyContent: 'flex-end' }}>
          <Button
            size="small"
            variant="outlined"
            disabled={!canShorten || saving}
            aria-label={t('details.shortenOneNightAria')}
            onClick={() => shiftCheckOut(-1)}
            sx={{ minWidth: 0, px: 1.25, py: 0.25, fontSize: '0.75rem', fontWeight: 700 }}
          >
            {t('details.minusOneNight')}
          </Button>
          <Button
            size="small"
            variant="outlined"
            disabled={!fields.check_out_date || saving}
            aria-label={t('details.extendOneNightAria')}
            onClick={() => shiftCheckOut(1)}
            sx={{ minWidth: 0, px: 1.25, py: 0.25, fontSize: '0.75rem', fontWeight: 700 }}
          >
            {t('details.plusOneNight')}
          </Button>
        </Stack>
        <TextField
          fullWidth
          size="small"
          label={t('edit.remarks')}
          value={fields.remarks}
          onChange={(e) => setFields((prev) => ({ ...prev, remarks: e.target.value }))}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          fullWidth
          size="small"
          label={t('edit.specialRequests')}
          value={fields.special_requests}
          onChange={(e) => setFields((prev) => ({ ...prev, special_requests: e.target.value }))}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
          {isDirty && (
            <Button
              size="small"
              onClick={() => setFields(baseline)}
              disabled={saving}
            >
              {t('common:actions.reset')}
            </Button>
          )}
          <Button
            size="small"
            variant="contained"
            onClick={handleSave}
            disabled={saving || hasInvalidDateOrder}
            startIcon={saving ? <CircularProgress size={14} /> : undefined}
          >
            {saving ? t('common:state.saving') : t('common:actions.saveChanges')}
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
};

export default BookingQuickEditSection;
