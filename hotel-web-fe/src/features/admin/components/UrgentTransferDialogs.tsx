import React from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { PendingPaymentEntry } from '../../../api';
import { useTranslation } from '../../../i18n';
import { formatCurrency } from '../../../utils/currency';
import { formatHotelDateTime } from '../../../utils/date';
import { canRejectPaymentClaim, hasPaymentReceipt } from '../utils/paymentApprovals';
import ApprovePaymentDialog from './ApprovePaymentDialog';

const REJECT_REASON_MAX = 1_000;

interface UrgentTransferDialogsProps {
  items: PendingPaymentEntry[];
  total: number;
  now: number;
  isPhone: boolean;
  modalOpen: boolean;
  approveTarget: PendingPaymentEntry | null;
  rejectTarget: PendingPaymentEntry | null;
  reason: string;
  error: string | null;
  busy: boolean;
  isApproving: boolean;
  isRejecting: boolean;
  refLabel: (entry: PendingPaymentEntry) => string;
  formatWaiting: (createdAt: string, now: number, t: (key: string, opts?: Record<string, number>) => string) => string;
  onMinimise: () => void;
  onClearError: () => void;
  onOpenReceipt: (entry: PendingPaymentEntry) => void;
  onSelectApprove: (entry: PendingPaymentEntry) => void;
  onCancelApprove: () => void;
  onConfirmApprove: (entry: PendingPaymentEntry) => void;
  onSelectReject: (entry: PendingPaymentEntry) => void;
  onCancelReject: () => void;
  onReasonChange: (value: string) => void;
  onConfirmReject: () => void;
}

/** Review queue modal, approval confirmation, and rejection dialog for {@link UrgentTransferAlert}. */
export const UrgentTransferDialogs: React.FC<UrgentTransferDialogsProps> = ({
  items,
  total,
  now,
  isPhone,
  modalOpen,
  approveTarget,
  rejectTarget,
  reason,
  error,
  busy,
  isApproving,
  isRejecting,
  refLabel,
  formatWaiting,
  onMinimise,
  onClearError,
  onOpenReceipt,
  onSelectApprove,
  onCancelApprove,
  onConfirmApprove,
  onSelectReject,
  onCancelReject,
  onReasonChange,
  onConfirmReject,
}) => {
  const { t } = useTranslation('admin');

  return (
    <>
      <Dialog
        open={modalOpen && !approveTarget && !rejectTarget}
        // No backdrop / Escape close: only the explicit minimise button.
        onClose={() => undefined}
        fullScreen={isPhone}
        maxWidth="md"
        fullWidth
        aria-labelledby="urgent-transfer-title"
      >
        <DialogTitle id="urgent-transfer-title" sx={{ bgcolor: 'error.main', color: 'error.contrastText' }}>
          {t('urgentTransfer.title', { count: total })}
        </DialogTitle>
        <DialogContent dividers>
          <Alert severity="error" sx={{ mb: 2 }}>
            {t('urgentTransfer.explain')}
          </Alert>
          {error ? (
            <Alert severity="error" sx={{ mb: 2 }} onClose={onClearError}>
              {error}
            </Alert>
          ) : null}
          <Stack spacing={1.5}>
            {items.map((entry) => {
              const receipt = hasPaymentReceipt(entry);
              const rejectable = canRejectPaymentClaim(entry.booking_status);
              return (
                <Box
                  key={entry.id}
                  data-testid="urgent-transfer-item"
                  sx={{ border: 1, borderColor: 'error.light', borderRadius: 1, p: 1.5 }}
                >
                  <Box
                    sx={{
                      display: 'grid',
                      gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)' },
                      gap: 1,
                      mb: 1.5,
                    }}
                  >
                    {[
                      [t('paymentApprovals.col.booking'), refLabel(entry)],
                      [t('paymentApprovals.col.guest'), entry.guest_name ?? '—'],
                      [t('common:field.amount'), formatCurrency(entry.amount)],
                      [t('urgentTransfer.submitted'), formatHotelDateTime(entry.created_at)],
                      [t('urgentTransfer.waiting'), formatWaiting(entry.created_at, now, t)],
                    ].map(([label, value]) => (
                      <Box key={label}>
                        <Typography variant="caption" color="text.secondary">
                          {label}
                        </Typography>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {value}
                        </Typography>
                      </Box>
                    ))}
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        {t('paymentApprovals.receipt')}
                      </Typography>
                      <Box>
                        {receipt && entry.receipt_file_available ? (
                          <Button
                            size="small"
                            startIcon={<ReceiptLongOutlinedIcon />}
                            onClick={() => onOpenReceipt(entry)}
                            sx={{ p: 0, minWidth: 0 }}
                          >
                            {t('urgentTransfer.viewReceipt')}
                          </Button>
                        ) : (
                          <Chip size="small" color="warning" label={t('urgentTransfer.noReceipt')} />
                        )}
                      </Box>
                    </Box>
                  </Box>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                    <Button
                      variant="contained"
                      color="success"
                      disabled={busy}
                      onClick={() => onSelectApprove(entry)}
                    >
                      {t('urgentTransfer.confirmApprove')}
                    </Button>
                    <Button
                      variant="outlined"
                      color="error"
                      disabled={busy || !rejectable}
                      onClick={() => onSelectReject(entry)}
                    >
                      {t('paymentApprovals.reject')}
                    </Button>
                  </Stack>
                </Box>
              );
            })}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Typography variant="caption" color="text.secondary" sx={{ flex: 1, px: 1 }}>
            {t('urgentTransfer.minimiseHint')}
          </Typography>
          <Button onClick={onMinimise}>{t('urgentTransfer.minimise')}</Button>
        </DialogActions>
      </Dialog>

      <ApprovePaymentDialog
        entry={approveTarget}
        isApproving={isApproving}
        onCancel={onCancelApprove}
        onConfirm={onConfirmApprove}
      />

      <Dialog
        open={Boolean(rejectTarget)}
        onClose={() => !isRejecting && onCancelReject()}
        fullScreen={isPhone}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{t('paymentApprovals.rejectDialog.title')}</DialogTitle>
        <DialogContent>
          {error ? (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          ) : null}
          <Typography sx={{ mb: 2 }}>
            {t('paymentApprovals.rejectDialog.bodyStart')} <strong>{rejectTarget ? refLabel(rejectTarget) : ''}</strong>
            {t('paymentApprovals.rejectDialog.bodyEnd')}
          </Typography>
          <TextField
            autoFocus
            fullWidth
            required
            multiline
            minRows={3}
            label={t('paymentApprovals.rejectDialog.labelReject')}
            placeholder={t('paymentApprovals.rejectDialog.placeholderReject')}
            value={reason}
            onChange={(event) => onReasonChange(event.target.value.slice(0, REJECT_REASON_MAX))}
            helperText={t('paymentApprovals.rejectDialog.charsLeft', { count: REJECT_REASON_MAX - reason.length })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onCancelReject} disabled={isRejecting}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            color="error"
            variant="contained"
            disabled={!reason.trim() || isRejecting}
            startIcon={isRejecting ? <CircularProgress size={16} color="inherit" /> : undefined}
            onClick={onConfirmReject}
          >
            {t('paymentApprovals.reject')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
