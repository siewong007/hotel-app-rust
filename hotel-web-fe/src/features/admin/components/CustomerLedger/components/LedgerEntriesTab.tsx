// "Ledger entries" tab body of the detail pane: search/status toolbar plus the
// paginated entries table with per-row actions.

import React from 'react';
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Button,
  Chip,
  IconButton,
  InputAdornment,
  Stack,
  LinearProgress,
  Tooltip,
} from '@mui/material';
import {
  Close as CloseIcon,
  Edit as EditIcon,
  Payment as PaymentIcon,
  Print as PrintIcon,
  Search as SearchIcon,
  Block as VoidIcon,
  OpenInNew as OpenInNewIcon,
} from '@mui/icons-material';
import type { CustomerLedger, CustomerLedgerPayment } from '../../../../../types';
import type { EntryStatusFilter } from '../types';
import { formatDateForDisplay, getLedgerUiStatus, isLedgerVoided } from '../helpers';
import { LedgerStatusBadge } from '../StatusPill';
import { isPositiveMoney, toMoneyNumber } from '../../../../../utils/money';
import { useIsPhone } from '../../../../../hooks/useIsPhone';
import { LogoLoader } from '../../../../../components';
import { MobileCardRow } from '../../../../../components/data-table/MobileCardRow';
import { ActionsMenu } from '../../../../../components/common/ActionsMenu';
import { useTranslation } from '../../../../../i18n';

interface LedgerEntriesTabProps {
  search: string;
  onSearchChange: (value: string) => void;
  statusFilter: EntryStatusFilter;
  onStatusFilterChange: (value: EntryStatusFilter) => void;
  loading: boolean;
  entries: CustomerLedger[];
  entryCount: number;
  payments: Record<number, CustomerLedgerPayment[]>;
  total: number;
  page: number;
  onPageChange: (page: number) => void;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
  loadingInvoice: boolean;
  onRecordPayment: (entry: CustomerLedger) => void;
  onViewInvoice: (entry: CustomerLedger) => void;
  onEdit: (entry: CustomerLedger) => void;
  onPrintReceipt: (entry: CustomerLedger) => void;
  onVoid: (entry: CustomerLedger) => void;
  formatCurrency: (value: number) => string;
}

// Allow recording a payment whenever there is still an outstanding balance
// (and the entry isn't voided). Keyed off the balance-driven UI status so a
// settled entry locks at zero balance but automatically reopens — the action
// reappears — the moment its balance returns.
// Invoice-backed (booking) entries record payments inside the invoice itself,
// so the standalone Record Payment action is only offered for manual entries
// that have no invoice to collect against.
const canRecordPayment = (ledger: CustomerLedger) => {
  if (ledger.booking_id) return false;
  const uiStatus = getLedgerUiStatus(ledger);
  return uiStatus !== 'paid' && uiStatus !== 'voided';
};
const canViewInvoice = (ledger: CustomerLedger) => !!ledger.booking_id;
const canVoid = (ledger: CustomerLedger) => !isLedgerVoided(ledger);

const ENTRY_STATUS_OPTIONS = [
  { key: 'all', labelKey: 'common:field.all' },
  { key: 'draft', labelKey: 'status:ledger.draft' },
  { key: 'uninvoiced', labelKey: 'status:ledger.uninvoiced' },
  { key: 'outstanding', labelKey: 'status:ledger.outstanding' },
  { key: 'invoiced', labelKey: 'status:ledger.invoiced' },
  { key: 'paid', labelKey: 'status:ledger.paid' },
  { key: 'overdue', labelKey: 'status:ledger.overdue' },
  { key: 'voided', labelKey: 'status:ledger.voided' },
] as const;

const LedgerEntriesTab: React.FC<LedgerEntriesTabProps> = ({
  search,
  onSearchChange,
  statusFilter,
  onStatusFilterChange,
  loading,
  entries,
  entryCount,
  payments,
  total,
  page,
  onPageChange,
  pageSize,
  onPageSizeChange,
  loadingInvoice,
  onRecordPayment,
  onViewInvoice,
  onEdit,
  onPrintReceipt,
  onVoid,
  formatCurrency,
}) => {
  const isPhone = useIsPhone();
  const { t } = useTranslation('finance');

  // Phone card footer: Record Payment keeps a dedicated button; the other
  // four row actions collapse into the shared ActionsMenu (BottomSheet on
  // phone). The same condition gates map to `hidden`/`disabled`/`destructive`
  // so nothing is offered that the desktop row would hide.
  const entryCardFooter = (entry: CustomerLedger, busy: boolean) => (
    <>
      {canRecordPayment(entry) && (
        <Button
          size="small"
          variant="contained"
          color="success"
          startIcon={<PaymentIcon sx={{ fontSize: 16 }} />}
          onClick={() => onRecordPayment(entry)}
          sx={{ textTransform: 'none', fontWeight: 600 }}
        >
          {t('ledger.entries.pay')}
        </Button>
      )}
      <ActionsMenu
        title={entry.description}
        triggerLabel={t('ledger.entries.actionsFor', { ref: entry.folio_number || `entry ${entry.id}` })}
        actions={[
          {
            id: 'view-invoice',
            label: t('ledger.entries.viewInvoice'),
            icon: <OpenInNewIcon fontSize="small" />,
            onClick: () => onViewInvoice(entry),
            disabled: busy,
            hidden: !canViewInvoice(entry),
          },
          {
            id: 'edit',
            label: t('ledger.entries.editEntry'),
            icon: <EditIcon fontSize="small" />,
            onClick: () => onEdit(entry),
          },
          {
            id: 'print',
            label: t('ledger.entries.printReceipt'),
            icon: <PrintIcon fontSize="small" />,
            onClick: () => onPrintReceipt(entry),
          },
          {
            id: 'void',
            label: t('ledger.entries.voidEntry'),
            icon: <VoidIcon fontSize="small" />,
            onClick: () => onVoid(entry),
            destructive: true,
            hidden: !canVoid(entry),
          },
        ]}
      />
    </>
  );

  const paginationEl = (
    <TablePagination
      component="div"
      count={total}
      page={page}
      onPageChange={(_, nextPage) => onPageChange(nextPage)}
      rowsPerPage={pageSize}
      rowsPerPageOptions={[10, 25, 50, 100]}
      onRowsPerPageChange={(event) => {
        onPageSizeChange(parseInt(event.target.value, 10));
      }}
      labelRowsPerPage={t('ledger.entries.perPage')}
    />
  );

  return (
    <>
      {/* Toolbar: search + status segment */}
      <Box
        sx={{
          px: 1.5,
          py: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 0.75,
          flexWrap: 'wrap',
          bgcolor: 'action.hover',
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <TextField
          size="small"
          placeholder={t('ledger.entries.searchPlaceholder')}
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Escape' && search) {
              e.preventDefault();
              onSearchChange('');
            }
          }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                </InputAdornment>
              ),
              endAdornment: search ? (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    aria-label={t('ledger.entries.clearSearchAria')}
                    onClick={() => onSearchChange('')}
                    sx={{ p: 0.25 }}
                  >
                    <CloseIcon sx={{ fontSize: 14 }} />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
              sx: { fontSize: 12 },
            },
          }}
          sx={{
            width: { xs: '100%', sm: 290 },
            flex: { sm: '1 1 275px' },
            maxWidth: { sm: 340 },
            bgcolor: 'background.paper',
          }}
        />
        {isPhone ? (
          // Phone: the 7 status filters become one horizontally scrollable
          // chip row (same pattern as BookingFiltersBar's chipsRow).
          <Stack
            direction="row"
            spacing={0.75}
            useFlexGap
            sx={{
              width: '100%',
              flexWrap: 'nowrap',
              overflowX: 'auto',
              mx: -2.5,
              px: 2.5,
              scrollbarWidth: 'none',
              '&::-webkit-scrollbar': { display: 'none' },
              '& .MuiChip-root': { flexShrink: 0 },
            }}
          >
            {ENTRY_STATUS_OPTIONS.map(s => (
              <Chip
                key={s.key}
                size="small"
                label={t(s.labelKey)}
                onClick={() => onStatusFilterChange(s.key as EntryStatusFilter)}
                variant={statusFilter === s.key ? 'filled' : 'outlined'}
                sx={{
                  height: 26,
                  fontSize: 11.5,
                  fontWeight: 600,
                  bgcolor: statusFilter === s.key ? 'text.primary' : 'background.paper',
                  color: statusFilter === s.key ? 'background.paper' : 'text.secondary',
                  borderColor: 'divider',
                  '&:hover, &:focus, &.Mui-focusVisible': {
                    bgcolor: statusFilter === s.key ? 'text.primary' : 'action.hover',
                    color: statusFilter === s.key ? 'background.paper' : 'text.primary',
                  },
                }}
              />
            ))}
          </Stack>
        ) : (
          <Box
            sx={{
              display: 'inline-flex',
              flexWrap: 'wrap',
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              p: 0.25,
            }}
          >
            {ENTRY_STATUS_OPTIONS.map(s => (
              <Button
                key={s.key}
                size="small"
                onClick={() => onStatusFilterChange(s.key as EntryStatusFilter)}
                sx={{
                  minWidth: 0,
                  px: 0.7,
                  py: 0.25,
                  fontSize: 11,
                  fontWeight: 600,
                  color: statusFilter === s.key ? 'background.paper' : 'text.secondary',
                  bgcolor: statusFilter === s.key ? 'text.primary' : 'transparent',
                  borderRadius: 0.75,
                  '&:hover, &:focus, &.Mui-focusVisible': {
                    bgcolor: statusFilter === s.key ? 'text.primary' : 'action.hover',
                    color: statusFilter === s.key ? 'background.paper' : 'text.primary',
                  },
                }}
              >
                {t(s.labelKey)}
              </Button>
            ))}
          </Box>
        )}
      </Box>
      {loading && <LinearProgress sx={{ height: 2 }} />}
      {entries.length === 0 && loading ? (
        <LogoLoader variant="page" minHeight={160} />
      ) : entries.length === 0 ? (
        <Box sx={{ py: 8, px: 4, textAlign: 'center' }}>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {entryCount === 0
              ? t('ledger.entries.emptyNone')
              : t('ledger.entries.emptyNoMatch')}
          </Typography>
        </Box>
      ) : isPhone ? (
        <Box>
          {entries.map((entry) => {
            const balance = toMoneyNumber(entry.balance_due);
            const paid = toMoneyNumber(entry.paid_amount);
            const voided = isLedgerVoided(entry);
            const uiStatus = getLedgerUiStatus(entry);
            return (
              <Box key={entry.id} sx={{ opacity: voided ? 0.6 : 1, borderBottom: '1px solid', borderColor: 'divider' }}>
                <MobileCardRow
                  title={entry.description}
                  subtitle={`${entry.folio_number || `#${entry.id}`}${entry.room_number ? ` · ${t('ledger.roomWithNumber', { number: entry.room_number })}` : ''} · ${formatDateForDisplay(entry.posting_date || entry.created_at)}`}
                  meta={`${entry.invoice_number || t('ledger.notInvoiced')} · ${isPositiveMoney(balance) ? t('ledger.metaDue', { amount: formatCurrency(balance) }) : t('ledger.metaPaid', { amount: formatCurrency(paid) })}`}
                  status={<LedgerStatusBadge status={uiStatus} />}
                  footer={entryCardFooter(entry, loadingInvoice)}
                />
              </Box>
            );
          })}
          {paginationEl}
        </Box>
      ) : (
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table
            size="small"
            stickyHeader
            sx={{
              minWidth: 620,
              '& .MuiTableCell-root': { px: 0.75 },
            }}
          >
            <TableHead>
              <TableRow
                sx={{
                  '& .MuiTableCell-root': {
                    fontSize: 10,
                    fontWeight: 700,
                    color: 'text.secondary',
                    letterSpacing: 0.5,
                    textTransform: 'uppercase',
                    bgcolor: 'action.hover',
                    py: 1,
                  },
                }}
              >
                <TableCell sx={{ pl: 1.5, width: '21%' }}>{t('common:field.description')}</TableCell>
                <TableCell>{t('ledger.field.stayLedgerDate')}</TableCell>
                <TableCell>{t('ledger.field.invoiceNumber')}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{t('common:field.status')}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>{t('common:field.amount')}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>{t('ledger.col.paid')}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>{t('ledger.col.balance')}</TableCell>
                <TableCell sx={{ width: 92, pr: 1 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {entries.map((entry) => {
                const amount = toMoneyNumber(entry.amount);
                const paid = toMoneyNumber(entry.paid_amount);
                const balance = toMoneyNumber(entry.balance_due);
                const voided = isLedgerVoided(entry);
                const uiStatus = getLedgerUiStatus(entry);
                const receiptNumber = (payments[entry.id] || [])
                  .map(payment => payment.receipt_number)
                  .filter(Boolean)[0];
                return (
                  <TableRow
                    key={entry.id}
                    hover
                    sx={{
                      '&:hover .ledger-row-actions, &:focus-within .ledger-row-actions': { opacity: 1 },
                      opacity: voided ? 0.6 : 1,
                    }}
                  >
                    <TableCell sx={{ pl: 1.5, py: 1 }}>
                      <Typography
                        sx={{ fontWeight: 600, fontSize: 12.5, lineHeight: 1.3 }}
                      >
                        {entry.description}
                      </Typography>
                      <Typography
                        sx={{
                          fontSize: 10.5,
                          color: 'text.secondary',
                          mt: 0.25,
                          fontFamily: '"JetBrains Mono", ui-monospace, monospace',
                        }}
                      >
                        {entry.folio_number || `#${entry.id}`}
                        {entry.room_number ? ` / Room ${entry.room_number}` : ''}
                        {entry.expense_type ? ` / ${entry.expense_type}` : ''}
                        {receiptNumber ? ` / Receipt ${receiptNumber}` : ''}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ color: 'text.secondary', fontSize: 11.5, whiteSpace: 'nowrap' }}>
                      {formatDateForDisplay(entry.posting_date || entry.created_at)}
                    </TableCell>
                    <TableCell sx={{ color: entry.invoice_number ? 'text.primary' : 'text.disabled', fontSize: 11.5 }}>
                      {entry.invoice_number || t('ledger.notInvoiced')}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      <LedgerStatusBadge status={uiStatus} />
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={{
                        fontFamily: '"JetBrains Mono", ui-monospace, monospace',
                        fontVariantNumeric: 'tabular-nums',
                        fontWeight: 700,
                        fontSize: 12,
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {formatCurrency(amount)}
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={{
                        fontFamily: '"JetBrains Mono", ui-monospace, monospace',
                        fontVariantNumeric: 'tabular-nums',
                        fontWeight: 700,
                        fontSize: 12,
                        whiteSpace: 'nowrap',
                        color: isPositiveMoney(paid) ? 'success.main' : 'text.secondary',
                      }}
                    >
                      {formatCurrency(paid)}
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={{
                        fontFamily: '"JetBrains Mono", ui-monospace, monospace',
                        fontVariantNumeric: 'tabular-nums',
                        fontWeight: 700,
                        fontSize: 12,
                        whiteSpace: 'nowrap',
                        color: isPositiveMoney(balance) ? 'error.main' : 'success.main',
                      }}
                    >
                      {formatCurrency(balance)}
                    </TableCell>
                    <TableCell sx={{ pr: 1, whiteSpace: 'nowrap' }}>
                      <Box
                        className="ledger-row-actions"
                        sx={{
                          display: 'inline-flex',
                          gap: 0.25,
                          opacity: { xs: 1, md: 0 },
                          transition: 'opacity 120ms',
                          '& .MuiIconButton-root': { p: 0.375 },
                        }}
                      >
                        {canRecordPayment(entry) && (
                          <Tooltip title={t('ledger.payment.record')} arrow>
                            <IconButton
                              size="small"
                              color="success"
                              aria-label={t('ledger.payment.record')}
                              onClick={() => onRecordPayment(entry)}
                            >
                              <PaymentIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </Tooltip>
                        )}
                        {canViewInvoice(entry) && (
                          <Tooltip title={t('ledger.entries.viewInvoice')} arrow>
                            <span>
                              <IconButton
                                size="small"
                                aria-label={t('ledger.entries.viewInvoice')}
                                onClick={() => onViewInvoice(entry)}
                                disabled={loadingInvoice}
                              >
                                <OpenInNewIcon sx={{ fontSize: 16 }} />
                              </IconButton>
                            </span>
                          </Tooltip>
                        )}
                        <Tooltip title={t('ledger.entries.editEntry')} arrow>
                          <IconButton
                            size="small"
                            aria-label={t('ledger.entries.editEntry')}
                            onClick={() => onEdit(entry)}
                          >
                            <EditIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={t('ledger.entries.printReceipt')} arrow>
                          <IconButton
                            size="small"
                            aria-label={t('ledger.entries.printReceipt')}
                            onClick={() => onPrintReceipt(entry)}
                          >
                            <PrintIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        </Tooltip>
                        {canVoid(entry) && (
                          <Tooltip title={t('ledger.entries.voidEntry')} arrow>
                            <IconButton
                              size="small"
                              aria-label={t('ledger.entries.voidEntry')}
                              color="error"
                              onClick={() => onVoid(entry)}
                            >
                              <VoidIcon sx={{ fontSize: 16 }} />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          {paginationEl}
        </TableContainer>
      )}
    </>
  );
};

export default LedgerEntriesTab;
