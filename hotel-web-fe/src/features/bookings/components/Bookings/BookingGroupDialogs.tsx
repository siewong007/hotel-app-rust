import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  TextField,
  Typography,
} from '@mui/material';
import { GROUP_PAY_METHODS, type GroupAction, type GroupPaymentMethod } from '../../api';
import { useTranslation } from '../../../../i18n';

interface BookingGroupDialogsProps {
  groupNumber: string;
  pending: GroupAction | null;
  reason: string;
  refundWarning: boolean;
  payOpen: boolean;
  payMethod: GroupPaymentMethod;
  payReference: string;
  payNotes: string;
  reactivateOpen: boolean;
  busy: boolean;
  error: string | null;
  onClosePending: () => void;
  onReasonChange: (value: string) => void;
  onConfirmAction: () => void;
  onClosePay: () => void;
  onPayMethodChange: (value: GroupPaymentMethod) => void;
  onPayReferenceChange: (value: string) => void;
  onPayNotesChange: (value: string) => void;
  onSubmitPay: () => void;
  onCloseReactivate: () => void;
  onConfirmReactivate: () => void;
}

/** Confirmation, group payment, and reactivation dialogs for {@link BookingGroupSection}. */
export function BookingGroupDialogs({
  groupNumber,
  pending,
  reason,
  refundWarning,
  payOpen,
  payMethod,
  payReference,
  payNotes,
  reactivateOpen,
  busy,
  error,
  onClosePending,
  onReasonChange,
  onConfirmAction,
  onClosePay,
  onPayMethodChange,
  onPayReferenceChange,
  onPayNotesChange,
  onSubmitPay,
  onCloseReactivate,
  onConfirmReactivate,
}: BookingGroupDialogsProps) {
  const { t } = useTranslation('bookings');

  const methodLabel = (method: GroupPaymentMethod) => {
    if (method === 'cash') return t('details.group.actions.pay.methods.cash');
    if (method === 'card') return t('details.group.actions.pay.methods.card');
    if (method === 'bank_transfer') return t('details.group.actions.pay.methods.bank_transfer');
    if (method === 'duitnow') return t('details.group.actions.pay.methods.duitnow');
    return t('details.group.actions.pay.methods.online_banking');
  };

  return (
    <>
      <Dialog open={pending !== null} onClose={() => { if (!busy) onClosePending(); }} fullWidth maxWidth="xs">
        {pending && (
          <>
            <DialogTitle>{t(`details.group.actions.${pending}.title`, { number: groupNumber })}</DialogTitle>
            <DialogContent>
              <Typography variant="body2" sx={{ mb: pending === 'cancel' ? 2 : 0 }}>
                {t(`details.group.actions.${pending}.body`)}
              </Typography>
              {pending === 'cancel' && refundWarning && (
                <Alert severity="warning" sx={{ mb: 2 }}>{t('details.group.actions.cancel.refundWarning')}</Alert>
              )}
              {pending === 'cancel' && (
                <TextField
                  fullWidth
                  size="small"
                  label={t('details.group.actions.reason')}
                  value={reason}
                  onChange={(event) => onReasonChange(event.target.value)}
                />
              )}
              {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
            </DialogContent>
            <DialogActions>
              <Button onClick={onClosePending} disabled={busy}>{t('details.group.actions.back')}</Button>
              <Button
                variant="contained"
                color={pending === 'cancel' ? 'error' : 'primary'}
                onClick={onConfirmAction}
                disabled={busy}
              >
                {t(`details.group.actions.${pending}.confirm`)}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>

      <Dialog open={payOpen} onClose={() => { if (!busy) onClosePay(); }} fullWidth maxWidth="xs">
        <DialogTitle>{t('details.group.actions.pay.title', { number: groupNumber })}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>{t('details.group.actions.pay.body')}</Typography>
          <TextField
            select
            fullWidth
            size="small"
            label={t('details.group.actions.pay.method')}
            value={payMethod}
            onChange={(event) => onPayMethodChange(event.target.value as GroupPaymentMethod)}
          >
            {GROUP_PAY_METHODS.map((method) => (
              <MenuItem key={method} value={method}>{methodLabel(method)}</MenuItem>
            ))}
          </TextField>
          <TextField
            fullWidth
            size="small"
            sx={{ mt: 2 }}
            label={t('details.group.actions.pay.reference')}
            value={payReference}
            onChange={(event) => onPayReferenceChange(event.target.value)}
          />
          <TextField
            fullWidth
            size="small"
            sx={{ mt: 2 }}
            label={t('details.group.actions.pay.notes')}
            value={payNotes}
            onChange={(event) => onPayNotesChange(event.target.value)}
          />
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClosePay} disabled={busy}>{t('details.group.actions.back')}</Button>
          <Button variant="contained" onClick={onSubmitPay} disabled={busy}>
            {t('details.group.actions.pay.confirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={reactivateOpen} onClose={() => { if (!busy) onCloseReactivate(); }} fullWidth maxWidth="xs">
        <DialogTitle>{t('details.group.actions.reactivate.title', { number: groupNumber })}</DialogTitle>
        <DialogContent>
          <Typography variant="body2">{t('details.group.actions.reactivate.body')}</Typography>
          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onCloseReactivate} disabled={busy}>{t('details.group.actions.back')}</Button>
          <Button variant="contained" onClick={onConfirmReactivate} disabled={busy}>
            {t('details.group.actions.reactivate.confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
