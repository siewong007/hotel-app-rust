import React from 'react';
import {
  Box,
  Button,
  Divider,
  Pagination,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  SwapVert as SwapVertIcon,
  Bed as BedIcon,
  Login as LoginIcon,
  ExitToApp as CheckOutIcon,
  Payment as PaymentIcon,
  Receipt as ReceiptIcon,
} from '@mui/icons-material';
import { BookingChannelChip, BillingChip, GroupChip, NightAuditChip } from './BookingMetaChips';
import type { BookingWithDetails } from '../../../../types';
import { useCurrency } from '../../../../hooks/useCurrency';
import { useIsPhone } from '../../../../hooks/useIsPhone';
import { statusLabel, useTranslation } from '../../../../i18n';
import { getHotelSettings } from '../../../../utils/hotelSettings';
import { isPositiveMoney } from '../../../../utils/money';
import { getBookingChannelInfo } from '../../utils/bookingChannel';
import {
  canCheckIn,
  canCheckOut,
  formatShortDate,
  getBillingChipLabel,
  getBookingBalance,
  getBookingTotal,
  getGuestInitials,
  getNights,
  isEarlyCheckIn,
  isNightAuditInvolved,
  statusDotColor,
  type BookingView,
} from '../../utils/bookingPageUtils';
import type { SortField } from '../../hooks/useBookings';

interface BookingListPagination {
  hasMultiplePages: boolean;
  startItem: number;
  endItem: number;
  totalItems: number;
  totalPages: number;
  currentPage: number;
}

interface BookingListPanelProps {
  bookings: BookingWithDetails[];
  loading: boolean;
  totalBookings: number;
  bookingView: BookingView;
  /** Row click — the page opens the booking drawer. */
  onOpenBooking: (booking: BookingWithDetails) => void;
  sortField: SortField;
  onToggleSort: () => void;
  pagination: BookingListPagination;
  onPageChange: (page: number) => void;
  onCheckIn?: (booking: BookingWithDetails) => void;
  onCheckOut?: (booking: BookingWithDetails) => void;
  onPayment?: (booking: BookingWithDetails) => void;
  onInvoice?: (booking: BookingWithDetails) => void;
}

interface PhoneBookingRowProps {
  booking: BookingWithDetails;
  balance: number;
  isPaid: boolean;
  formatCurrency: (value: number) => string;
  onOpenBooking: (booking: BookingWithDetails) => void;
}

/**
 * Compact two-line row for phone widths: identity + status on line 1, stay
 * facts on line 2, money on the right. Channel/billing/night-audit chips and
 * the folio reference are intentionally dropped — they stay reachable on the
 * booking detail page. The whole row is the single tap target.
 */
const PhoneBookingRow: React.FC<PhoneBookingRowProps> = ({
  booking,
  balance,
  isPaid,
  formatCurrency,
  onOpenBooking,
}) => {
  const { t } = useTranslation('bookings');
  return (
  <Box
    onClick={() => onOpenBooking(booking)}
    sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1.5,
      px: 2,
      py: 1.25,
      cursor: 'pointer',
      bgcolor: 'background.paper',
      opacity: booking.status === 'voided' ? 0.55 : 1,
      '&:hover': { bgcolor: 'action.hover' },
      '&:active': { bgcolor: 'action.selected' },
    }}
  >
    <Box sx={{ width: 40, height: 40, borderRadius: '50%', flexShrink: 0, bgcolor: 'var(--hotel-primary-subtle)', color: 'var(--hotel-primary-text)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900 }}>
      {getGuestInitials(booking.guest_name)}
    </Box>
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75, minWidth: 0 }}>
        <Typography variant="subtitle2" noWrap sx={{ fontWeight: 800, lineHeight: 1.3, minWidth: 0 }}>
          {booking.guest_name}
        </Typography>
        <Typography component="span" variant="caption" noWrap sx={{ color: statusDotColor(booking.status), fontWeight: 800, flexShrink: 0 }}>
          • {statusLabel(t, 'booking', booking.status)}
        </Typography>
      </Box>
      <Typography variant="body2" noWrap sx={{ color: 'text.secondary' }}>
        {t('list.roomNumber', { number: booking.room_number || '-' })} · {booking.room_type || t('list.roomFallback')} · {formatShortDate(booking.check_in_date)} → {formatShortDate(booking.check_out_date)} · {t('list.nightsAbbrev', { count: getNights(booking) })}{booking.group_number ? ` · ${t('list.groupChip', { number: booking.group_number })}` : ''}
      </Typography>
    </Box>
    <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{formatCurrency(getBookingTotal(booking))}</Typography>
      {isPositiveMoney(balance) ? (
        <Typography variant="caption" sx={{ color: 'error.main', fontWeight: 800 }}>{t('list.due', { amount: formatCurrency(balance) })}</Typography>
      ) : (
        <Typography variant="caption" sx={{ color: 'success.main', fontWeight: 800 }}>✓ {isPaid ? statusLabel(t, 'payment', 'paid') : statusLabel(t, 'payment', booking.payment_status)}</Typography>
      )}
    </Box>
  </Box>
  );
};

const MD_GRID_WITH_ACTIONS = '54px 1fr auto auto auto';
const MD_GRID_DEFAULT = '54px 1fr auto auto';

const BookingListPanel: React.FC<BookingListPanelProps> = ({
  bookings,
  loading,
  totalBookings,
  bookingView,
  onOpenBooking,
  sortField,
  onToggleSort,
  pagination,
  onPageChange,
  onCheckIn,
  onCheckOut,
  onPayment,
  onInvoice,
}) => {
  const { format: formatCurrency } = useCurrency();
  const isPhone = useIsPhone();
  const { t } = useTranslation('bookings');
  const hasRowActions = Boolean(onCheckIn || onCheckOut || onPayment || onInvoice);
  const desktopGridColumns = hasRowActions ? MD_GRID_WITH_ACTIONS : MD_GRID_DEFAULT;
  const checkInTime = getHotelSettings().check_in_time;

  return (
    <>
      <Box sx={{ px: 2, py: 1.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid', borderColor: 'divider' }}>
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
            fontWeight: 800
          }}>
          {t('list.count', { count: bookings.length })}
        </Typography>
        <Button size="small" endIcon={<SwapVertIcon />} onClick={onToggleSort} sx={{ color: 'text.primary' }}>
          {t('list.sortButton', { field: t(sortField === 'guest_name' ? 'list.sortGuest' : 'list.sortPriority') })}
        </Button>
      </Box>

      {loading ? (
        <Stack divider={<Divider />} sx={{ minHeight: 420 }} aria-busy="true" aria-label={t('list.loadingAria')}>
          {Array.from({ length: 6 }).map((_, index) => (
            <Box
              key={index}
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '44px 1fr', md: desktopGridColumns },
                gap: 1.75,
                alignItems: 'center',
                px: 2,
                py: 1.75,
              }}
            >
              <Skeleton variant="circular" width={46} height={46} />
              <Box sx={{ minWidth: 0 }}>
                <Skeleton width="55%" height={24} />
                <Skeleton width="80%" height={18} />
              </Box>
              <Box sx={{ gridColumn: { xs: '2 / span 1', md: 'auto' } }}>
                <Skeleton width={90} height={22} />
                <Skeleton width={70} height={18} />
              </Box>
              <Box sx={{ gridColumn: { xs: '2 / span 1', md: 'auto' } }}>
                <Skeleton width={110} height={18} />
              </Box>
              {hasRowActions && (
                <Box sx={{ gridColumn: { xs: '2 / span 1', md: 'auto' } }}>
                  <Skeleton width={88} height={30} />
                </Box>
              )}
            </Box>
          ))}
        </Stack>
      ) : bookings.length === 0 ? (
        <Box
          sx={{
            textAlign: "center",
            py: 6
          }}>
          <Typography variant="h6" sx={{
            color: "text.secondary"
          }}>
            {totalBookings === 0 ? t('list.emptyTitle') : t('list.emptyFilteredTitle')}
          </Typography>
          <Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              mt: 1
            }}>
            {totalBookings === 0 ? t('list.emptyHint') : t('list.emptyFilteredHint')}
          </Typography>
        </Box>
      ) : (
        <Stack divider={<Divider />} sx={{ maxHeight: { lg: 'calc(100vh - 430px)' }, minHeight: 420, overflow: 'auto' }}>
          {bookings.map((booking) => {
            const balance = getBookingBalance(booking);
            const isPaid = !isPositiveMoney(balance) && ['paid', 'paid_rate'].includes(String(booking.payment_status || '').toLowerCase());

            if (isPhone) {
              return (
                <PhoneBookingRow
                  key={booking.id}
                  booking={booking}
                  balance={balance}
                  isPaid={isPaid}
                  formatCurrency={formatCurrency}
                  onOpenBooking={onOpenBooking}
                />
              );
            }

            const channelInfo = getBookingChannelInfo(booking);
            const billingChipLabel = getBillingChipLabel(booking, t);
            const showCheckIn = Boolean(onCheckIn && canCheckIn(booking));
            const showCheckOut = !showCheckIn && Boolean(onCheckOut && canCheckOut(booking));
            const showPayment = !showCheckIn && !showCheckOut && Boolean(
              onPayment &&
              !booking.group_number &&
              !booking.is_complimentary &&
              isPositiveMoney(balance) &&
              !['checked_in', 'checked_out', 'completed'].includes(booking.status)
            );
            const showInvoice = !showCheckIn && !showCheckOut && !showPayment && Boolean(
              onInvoice && ['checked_out', 'completed'].includes(booking.status)
            );

            let rowAction: React.ReactNode = null;
            if (showCheckIn && onCheckIn) {
              const btn = (
                <Button
                  size="small"
                  variant="outlined"
                  color="success"
                  startIcon={<LoginIcon fontSize="small" />}
                  aria-label={t('list.quickCheckInAria', { guest: booking.guest_name })}
                  onClick={(e) => {
                    e.stopPropagation();
                    onCheckIn(booking);
                  }}
                  sx={{ minHeight: 32, whiteSpace: 'nowrap', fontWeight: 700 }}
                >
                  {t('details.checkInAction')}
                </Button>
              );
              rowAction = isEarlyCheckIn(booking, checkInTime) ? (
                <Tooltip title={t('details.earlyCheckInTooltip', { time: checkInTime || '15:00' })} arrow>
                  {btn}
                </Tooltip>
              ) : btn;
            } else if (showCheckOut && onCheckOut) {
              rowAction = (
                <Button
                  size="small"
                  variant="outlined"
                  color="warning"
                  startIcon={<CheckOutIcon fontSize="small" />}
                  aria-label={t('list.quickCheckOutAria', { guest: booking.guest_name })}
                  onClick={(e) => {
                    e.stopPropagation();
                    onCheckOut(booking);
                  }}
                  sx={{ minHeight: 32, whiteSpace: 'nowrap', fontWeight: 700 }}
                >
                  {t('details.checkOutAction')}
                </Button>
              );
            } else if (showPayment && onPayment) {
              rowAction = (
                <Button
                  size="small"
                  variant="outlined"
                  color="success"
                  startIcon={<PaymentIcon fontSize="small" />}
                  aria-label={t('list.quickPaymentAria', { guest: booking.guest_name })}
                  onClick={(e) => {
                    e.stopPropagation();
                    onPayment(booking);
                  }}
                  sx={{ minHeight: 32, whiteSpace: 'nowrap', fontWeight: 700 }}
                >
                  {t('details.payment')}
                </Button>
              );
            } else if (showInvoice && onInvoice) {
              rowAction = (
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<ReceiptIcon fontSize="small" />}
                  aria-label={t('list.quickInvoiceAria', { guest: booking.guest_name })}
                  onClick={(e) => {
                    e.stopPropagation();
                    onInvoice(booking);
                  }}
                  sx={{ minHeight: 32, whiteSpace: 'nowrap', fontWeight: 700 }}
                >
                  {t('details.invoice')}
                </Button>
              );
            }

            return (
              <Box
                key={booking.id}
                onClick={() => onOpenBooking(booking)}
                sx={{
                  display: 'grid',
                  gridTemplateColumns: { xs: '44px 1fr', md: desktopGridColumns },
                  gap: 1.75,
                  alignItems: 'center',
                  px: 2,
                  py: 1.75,
                  cursor: 'pointer',
                  bgcolor: 'background.paper',
                  opacity: booking.status === 'voided' ? 0.55 : 1,
                  transition: 'background-color 120ms ease',
                  '&:hover': { bgcolor: 'action.hover' },
                }}
              >
                <Box sx={{ width: 46, height: 46, borderRadius: '50%', bgcolor: 'var(--hotel-primary-subtle)', color: 'var(--hotel-primary-text)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900 }}>
                  {getGuestInitials(booking.guest_name)}
                </Box>
                <Box sx={{ minWidth: 0 }}>
                  <Stack
                    direction="row"
                    spacing={1}
                    useFlexGap
                    sx={{
                      alignItems: "center",
                      flexWrap: "wrap"
                    }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 900, lineHeight: 1.15 }}>{booking.guest_name}</Typography>
                    {channelInfo && <BookingChannelChip channel={channelInfo} />}
                    {billingChipLabel && <BillingChip label={billingChipLabel} />}
                    {booking.group_number && <GroupChip groupNumber={booking.group_number} />}
                    <Typography variant="body2" sx={{ color: statusDotColor(booking.status), fontWeight: 800 }}>
                      • {statusLabel(t, 'booking', booking.status)}
                    </Typography>
                    {isNightAuditInvolved(booking) && <NightAuditChip />}
                  </Stack>
                  <Typography
                    variant="body2"
                    sx={{
                      color: "text.secondary",
                      mt: 0.35
                    }}>
                    <BedIcon sx={{ fontSize: 16, verticalAlign: 'text-bottom', mr: 0.5 }} />
                    {t('list.roomNumber', { number: booking.room_number || '-' })} · {booking.room_type || t('list.roomFallback')} · {formatShortDate(booking.check_in_date)} → {formatShortDate(booking.check_out_date)} · {t('list.nightsAbbrev', { count: getNights(booking) })}
                  </Typography>
                </Box>
                <Box sx={{ textAlign: { xs: 'left', md: 'right' }, gridColumn: { xs: '2 / span 1', md: 'auto' } }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>{formatCurrency(getBookingTotal(booking))}</Typography>
                  {isPositiveMoney(balance) ? (
                    <Typography
                      variant="body2"
                      sx={{
                        color: "error.main",
                        fontWeight: 800
                      }}>{t('list.due', { amount: formatCurrency(balance) })}</Typography>
                  ) : (
                    <Typography
                      variant="body2"
                      sx={{
                        color: "success.main",
                        fontWeight: 800
                      }}>✓ {isPaid ? statusLabel(t, 'payment', 'paid') : statusLabel(t, 'payment', booking.payment_status)}</Typography>
                  )}
                </Box>
                <Typography
                  variant="body2"
                  sx={{
                    color: "text.secondary",
                    fontFamily: 'monospace',
                    textAlign: { xs: 'left', md: 'right' },
                    gridColumn: { xs: '2 / span 1', md: 'auto' }
                  }}>
                  {booking.invoice_number || booking.folio_number || `#${booking.id}`}
                </Typography>
                {hasRowActions && (
                  <Box
                    sx={{
                      display: 'flex',
                      justifyContent: { xs: 'flex-start', md: 'flex-end' },
                      minWidth: { md: 108 },
                      gridColumn: { xs: '2 / span 1', md: 'auto' },
                    }}
                  >
                    {rowAction}
                  </Box>
                )}
              </Box>
            );
          })}
        </Stack>
      )}

      {bookingView === 'all' && pagination.hasMultiplePages && (
        <Stack
          direction="row"
          sx={{
            justifyContent: "space-between",
            alignItems: "center",
            px: 2,
            py: 1.5,
            borderTop: '1px solid',
            borderColor: 'divider'
          }}>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {t('list.showing', { from: pagination.startItem, to: pagination.endItem, total: pagination.totalItems })}
          </Typography>
          <Pagination
            count={pagination.totalPages}
            page={pagination.currentPage}
            onChange={(_, page) => onPageChange(page)}
            color="primary"
            size="small"
            showFirstButton
            showLastButton
          />
        </Stack>
      )}
    </>
  );
};

export default BookingListPanel;
