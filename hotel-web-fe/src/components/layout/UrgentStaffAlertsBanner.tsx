import React from 'react';
import { Badge, Box, Button, IconButton, Tooltip, Typography } from '@mui/material';
import { keyframes } from '@mui/system';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import PriorityHighIcon from '@mui/icons-material/PriorityHigh';
import { useAuth } from '../../auth/AuthContext';
import { useTranslation } from '../../i18n';
import { useNavigate } from '../../router/compat';
import {
  useMarkStaffNotificationRead,
  useStaffNotifications,
} from '../../features/admin/system/hooks';
import {
  resolveActionLabel,
  resolveActionPending,
  resolveActionUrl,
  resolvePriority,
  resolveTitleAndBody,
} from '../../features/notifications/utils/staffNotificationPresentation';

const pulse = keyframes`
  0% { box-shadow: 0 0 0 0 rgba(183, 28, 28, 0.65); }
  70% { box-shadow: 0 0 0 8px rgba(183, 28, 28, 0); }
  100% { box-shadow: 0 0 0 0 rgba(183, 28, 28, 0); }
`;

/**
 * Sticky urgent operational banner for staff alerts other than payments:approve
 * bank-transfer claims (which UrgentTransferAlert already owns with inline
 * approve/reject dialogs). Remains visible until handled in the workflow or
 * acknowledged by the signed-in staff user.
 */
export const UrgentStaffAlertsBanner: React.FC = () => {
  const { user, hasPermission } = useAuth();
  const { t } = useTranslation('notifications');
  const navigate = useNavigate();

  const isStaff = Boolean(user && user.user_type !== 'guest');
  const canApprovePayments = hasPermission('payments:approve');
  const query = useStaffNotifications(isStaff, false);
  const markRead = useMarkStaffNotificationRead();

  const urgentItems = React.useMemo(() => {
    const items = query.data?.items ?? [];
    return items.filter((item) => {
      if (resolvePriority(item) !== 'urgent') return false;
      if (!resolveActionPending(item)) return false;
      if (item.read_at !== null) return false;
      // Avoid duplicating UrgentTransferAlert for staff with payments:approve.
      if (item.kind === 'bank_transfer_pending' && canApprovePayments) return false;
      return true;
    });
  }, [query.data?.items, canApprovePayments]);

  if (!isStaff || urgentItems.length === 0) return null;

  const top = urgentItems[0];
  const { title, body } = resolveTitleAndBody(top, t);
  const actionUrl = resolveActionUrl(top);
  const ctaLabel = resolveActionLabel(top, t);

  const handleAction = () => {
    markRead.mutate(top.id);
    if (actionUrl) {
      navigate(actionUrl);
    } else {
      navigate('/notifications?filter=urgent');
    }
  };

  return (
    <Box
      role="alert"
      aria-live="polite"
      data-testid="urgent-staff-alerts-banner"
      sx={{
        bgcolor: 'error.dark',
        color: 'error.contrastText',
        px: { xs: 1.5, sm: 2.5 },
        py: { xs: 1, sm: 0.75 },
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 1,
        borderTop: '1px solid rgba(255,255,255,0.16)',
        animation: `${pulse} 2s infinite`,
        '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
      }}
    >
      <Badge
        badgeContent={urgentItems.length}
        max={99}
        sx={{
          '& .MuiBadge-badge': {
            bgcolor: 'common.white',
            color: 'error.dark',
            fontWeight: 700,
          },
        }}
      >
        <PriorityHighIcon fontSize="small" />
      </Badge>

      <Box sx={{ flex: '1 1 240px', minWidth: 0, ml: 1 }}>
        <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.25 }}>
          {urgentItems.length > 1
            ? t('center.urgentBannerCount', { count: urgentItems.length, title })
            : title}
        </Typography>
        {body ? (
          <Typography
            variant="caption"
            sx={{
              display: 'block',
              opacity: 0.92,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {body}
          </Typography>
        ) : null}
      </Box>

      <Button
        size="small"
        variant="contained"
        onClick={handleAction}
        sx={{
          bgcolor: 'common.white',
          color: 'error.dark',
          fontWeight: 700,
          textTransform: 'none',
          '&:hover': { bgcolor: 'grey.100' },
          flexShrink: 0,
        }}
      >
        {ctaLabel}
      </Button>

      {urgentItems.length > 1 && (
        <Button
          size="small"
          variant="outlined"
          onClick={() => navigate('/notifications?filter=urgent')}
          sx={{
            borderColor: 'rgba(255,255,255,0.65)',
            color: 'common.white',
            textTransform: 'none',
            flexShrink: 0,
            '&:hover': { borderColor: 'common.white', bgcolor: 'rgba(255,255,255,0.1)' },
          }}
        >
          {t('common:actions.viewAll')}
        </Button>
      )}

      <Tooltip title={t('center.acknowledge')}>
        <IconButton
          size="small"
          onClick={() => markRead.mutate(top.id)}
          aria-label={t('center.acknowledge')}
          sx={{ color: 'common.white', flexShrink: 0 }}
        >
          <CheckCircleOutlineIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  );
};

export default UrgentStaffAlertsBanner;
