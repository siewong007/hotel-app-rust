import React from 'react';
import {
  Badge,
  Box,
  Button,
  Chip,
  IconButton,
  Popover,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import { keyframes } from '@mui/system';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import DoneIcon from '@mui/icons-material/Done';
import CloseIcon from '@mui/icons-material/Close';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import type { ApiNotificationSeverity, ApiNotificationPriority } from '../../utils/apiNotifications';
import {
  clearAll,
  markAllRead,
  removeNotification,
  useNotifications,
} from '../../utils/notificationStore';
import { useAuth } from '../../auth/AuthContext';
import { useTranslation } from '../../i18n';
import { Link } from '../../router/compat';
import { useDeliveryFeed } from '../../features/notifications/hooks/useDeliveryFeed';
import { DeliveryTabs } from '../../features/notifications/components/DeliveryTabs';
import { StaffNotificationIcon } from '../../features/notifications/components/StaffNotificationIcon';
import { formatRelativeMs } from '../../features/notifications/utils/relativeTime';
import {
  priorityLabel,
  resolveActionLabel,
  resolveActionPending,
  resolveActionUrl,
  resolveCategory,
  resolvePriority,
  resolveRequiresAction,
  resolveTitleAndBody,
} from '../../features/notifications/utils/staffNotificationPresentation';
import type { TierFilter } from '../../features/notifications/types';
import {
  useMarkAllStaffNotificationsRead,
  useMarkStaffNotificationRead,
  useStaffNotifications,
} from '../../features/admin/system/hooks';

const SEVERITY_META: Record<
  ApiNotificationSeverity,
  { color: string; Icon: React.ComponentType<{ fontSize?: 'small' | 'inherit' | 'medium' | 'large' }> }
> = {
  error: { color: 'var(--hotel-danger)', Icon: ErrorOutlineIcon },
  warning: { color: 'var(--hotel-warning)', Icon: WarningAmberIcon },
  info: { color: 'var(--hotel-info)', Icon: InfoOutlinedIcon },
  success: { color: 'var(--hotel-success)', Icon: CheckCircleOutlineIcon },
};

/** Priority value → `notifications` translation key. */
const PRIORITY_KEYS: Record<ApiNotificationPriority, string> = {
  info: 'priority.info',
  warning: 'priority.warning',
  critical: 'priority.critical',
};

const bellPulse = keyframes`
  0% { box-shadow: 0 0 0 0 rgba(211, 47, 47, 0.55); }
  70% { box-shadow: 0 0 0 8px rgba(211, 47, 47, 0); }
  100% { box-shadow: 0 0 0 0 rgba(211, 47, 47, 0); }
`;

type CenterTab = 'action' | 'deliveries' | 'activity';

export const NotificationCenter: React.FC = () => {
  const { user, hasPermission } = useAuth();
  const { t } = useTranslation('notifications');
  const userId = user?.id;
  const { items, unreadCount } = useNotifications(userId);
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const [tab, setTab] = React.useState<CenterTab>('action');
  const [feedTier, setFeedTier] = React.useState<TierFilter>('all');
  const open = Boolean(anchorEl);

  const canReadFeed = hasPermission('communications:read');
  const feed = useDeliveryFeed(
    { tier: feedTier },
    canReadFeed,
    open && tab === 'deliveries',
  );

  // Every authenticated staff member receives notifications addressed to
  // any of their effective RBAC permissions from `/api/system/notifications`.
  const isStaff = Boolean(user && (user as { user_type?: string }).user_type !== 'guest');
  const staffAlerts = useStaffNotifications(isStaff, open);
  const markOneStaffRead = useMarkStaffNotificationRead();
  const markAllStaffRead = useMarkAllStaffNotificationsRead();

  const staffItems = staffAlerts.data?.items ?? [];
  const staffUnread = isStaff ? (staffAlerts.data?.unread ?? 0) : 0;
  const hasUrgent = isStaff && (staffAlerts.data?.urgent_unread ?? 0) > 0;
  const badgeCount = staffUnread + unreadCount;

  const handleOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
    if (tab === 'activity') {
      markAllRead(userId);
    }
  };
  const handleClose = () => setAnchorEl(null);

  return (
    <>
      <Tooltip title={t('title')}>
        <IconButton
          onClick={handleOpen}
          aria-label={
            badgeCount > 0 ? t('center.unreadAria', { count: badgeCount }) : t('title')
          }
          sx={{
            flexShrink: 0,
            color: hasUrgent ? 'error.main' : 'inherit',
            borderRadius: '50%',
            animation: hasUrgent ? `${bellPulse} 1.8s infinite` : 'none',
            '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
            '&:hover': { bgcolor: 'action.hover' },
          }}
        >
          <Badge badgeContent={badgeCount} color="error" max={99} overlap="circular">
            {hasUrgent ? (
              <NotificationsActiveIcon fontSize="small" />
            ) : (
              <NotificationsNoneIcon fontSize="small" />
            )}
          </Badge>
        </IconButton>
      </Tooltip>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: { mt: 1, width: 'min(440px, 94vw)', borderRadius: 2, overflow: 'hidden' },
          },
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 2,
            py: 1.25,
            borderBottom: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography sx={{ fontSize: '0.92rem', fontWeight: 700 }}>{t('title')}</Typography>
            {staffUnread > 0 && (
              <Chip
                size="small"
                color={hasUrgent ? 'error' : 'primary'}
                label={staffUnread}
                sx={{ height: 20, fontSize: '0.7rem', fontWeight: 700 }}
              />
            )}
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {tab === 'action' && staffUnread > 0 && (
              <Button
                size="small"
                onClick={() => markAllStaffRead.mutate()}
                disabled={markAllStaffRead.isPending}
                sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              >
                {t('markAllRead')}
              </Button>
            )}
            {tab === 'activity' && items.length > 0 && (
              <Button
                size="small"
                onClick={() => clearAll(userId)}
                sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              >
                {t('center.clearAll')}
              </Button>
            )}
            <Button
              size="small"
              component={Link}
              to="/notifications"
              onClick={handleClose}
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              aria-label={t('center.viewAllAria')}
            >
              {t('common:actions.viewAll')}
            </Button>
          </Box>
        </Box>

        <Tabs
          value={tab}
          onChange={(_, next: CenterTab) => {
            setTab(next);
            if (next === 'activity') {
              markAllRead(userId);
            }
          }}
          variant="fullWidth"
          sx={{
            minHeight: 36,
            borderBottom: '1px solid',
            borderColor: 'divider',
            '& .MuiTab-root': { minHeight: 36, minWidth: 0, px: 1, fontSize: '0.75rem', textTransform: 'none' },
          }}
        >
          <Tab
            value="action"
            label={
              staffUnread > 0
                ? `${t('center.actionRequired')} (${staffUnread})`
                : t('center.actionRequired')
            }
          />
          {canReadFeed && <Tab value="deliveries" label={t('center.deliveries')} />}
          <Tab value="activity" label={t('center.activity')} />
        </Tabs>

        {tab === 'action' ? (
          <Box sx={{ maxHeight: 440, overflowY: 'auto' }}>
            {staffItems.length === 0 ? (
              <Box sx={{ px: 2, py: 5, textAlign: 'center', color: 'text.secondary' }}>
                <NotificationsNoneIcon sx={{ fontSize: 32, opacity: 0.4, mb: 1 }} />
                <Typography sx={{ fontSize: '0.85rem' }}>
                  {staffAlerts.isPending ? t('common:state.loading') : t('center.noActionItems')}
                </Typography>
              </Box>
            ) : (
              staffItems.map((item) => {
                const priority = resolvePriority(item);
                const category = resolveCategory(item);
                const pending = resolveActionPending(item);
                const requiresAction = resolveRequiresAction(item);
                const actionUrl = resolveActionUrl(item);
                const ctaLabel = resolveActionLabel(item, t);
                const { title, body } = resolveTitleAndBody(item, t);
                const unreadPending = item.read_at === null && pending;

                return (
                  <Box
                    key={item.id}
                    sx={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 1.25,
                      px: 2,
                      py: 1.25,
                      borderBottom: '1px solid',
                      borderColor: 'divider',
                      borderLeft: '3px solid',
                      borderLeftColor: !pending
                        ? 'transparent'
                        : priority === 'urgent'
                          ? 'error.main'
                          : priority === 'high'
                            ? 'warning.main'
                            : 'info.main',
                      bgcolor: unreadPending ? 'action.hover' : 'transparent',
                      opacity: pending ? 1 : 0.72,
                      '&:last-of-type': { borderBottom: 'none' },
                    }}
                  >
                    <Box sx={{ display: 'flex', mt: '3px' }}>
                      <StaffNotificationIcon category={category} priority={priority} pending={pending} />
                    </Box>

                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap', mb: 0.25 }}>
                        {priority !== 'normal' && (
                          <Chip
                            size="small"
                            label={priorityLabel(priority, t)}
                            color={priority === 'urgent' ? 'error' : 'warning'}
                            variant={pending ? 'filled' : 'outlined'}
                            sx={{ height: 18, fontSize: '0.65rem', fontWeight: 700 }}
                          />
                        )}
                        {requiresAction && (
                          <Chip
                            size="small"
                            label={pending ? t('center.actionNeeded') : t('center.handled')}
                            color={pending ? 'primary' : 'success'}
                            variant="outlined"
                            sx={{ height: 18, fontSize: '0.65rem' }}
                          />
                        )}
                        <Typography sx={{ fontSize: '0.68rem', color: 'text.secondary', ml: 'auto' }}>
                          {formatRelativeMs(Date.parse(item.created_at), t)}
                        </Typography>
                      </Box>

                      <Typography
                        sx={{
                          fontSize: '0.82rem',
                          lineHeight: 1.35,
                          fontWeight: unreadPending ? 700 : 500,
                        }}
                      >
                        {title}
                      </Typography>

                      {body ? (
                        <Typography sx={{ fontSize: '0.75rem', color: 'text.secondary', mt: 0.25 }}>
                          {body}
                        </Typography>
                      ) : null}

                      {(actionUrl || item.read_at === null) && (
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 0.75, gap: 1 }}>
                          {actionUrl ? (
                            <Button
                              size="small"
                              component={Link}
                              to={actionUrl}
                              onClick={() => {
                                if (item.read_at === null) markOneStaffRead.mutate(item.id);
                                handleClose();
                              }}
                              variant={pending ? 'outlined' : 'text'}
                              color={priority === 'urgent' && pending ? 'error' : 'primary'}
                              sx={{ textTransform: 'none', fontSize: '0.74rem', py: 0.25, px: pending ? 1 : 0 }}
                            >
                              {ctaLabel}
                            </Button>
                          ) : (
                            <span />
                          )}
                          {item.read_at === null && (
                            <Tooltip title={t('center.markRead')}>
                              <IconButton
                                size="small"
                                aria-label={t('center.markRead')}
                                onClick={() => markOneStaffRead.mutate(item.id)}
                              >
                                <DoneIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Box>
                      )}
                    </Box>
                  </Box>
                );
              })
            )}
          </Box>
        ) : canReadFeed && tab === 'deliveries' ? (
          <DeliveryTabs
            showTabs
            tier={feedTier}
            onTierChange={(next) => setFeedTier(next)}
            items={feed.data?.items ?? []}
            emptyMessage={
              feed.isPending ? t('common:state.loading') : t('center.allCaughtUp')
            }
          />
        ) : (
          <Box sx={{ maxHeight: 420, overflowY: 'auto' }}>
            {items.length === 0 ? (
              <Box sx={{ px: 2, py: 5, textAlign: 'center', color: 'text.secondary' }}>
                <NotificationsNoneIcon sx={{ fontSize: 32, opacity: 0.4, mb: 1 }} />
                <Typography sx={{ fontSize: '0.85rem' }}>{t('center.allCaughtUp')}</Typography>
              </Box>
            ) : (
              items.map((item) => {
                const meta = SEVERITY_META[item.severity] ?? SEVERITY_META.info;
                const { Icon } = meta;
                return (
                  <Box
                    key={item.id}
                    sx={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 1.25,
                      px: 2,
                      py: 1.25,
                      borderBottom: '1px solid',
                      borderColor: 'divider',
                      '&:last-of-type': { borderBottom: 'none' },
                      '&:hover .notification-dismiss': { opacity: 1 },
                    }}
                  >
                    <Box sx={{ color: meta.color, display: 'flex', mt: '2px' }}>
                      <Icon fontSize="small" />
                    </Box>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography sx={{ fontSize: '0.82rem', lineHeight: 1.35, wordBreak: 'break-word' }}>
                        {item.message}
                      </Typography>
                      <Typography sx={{ fontSize: '0.68rem', color: 'text.secondary', mt: 0.25 }}>
                        {t('meta', {
                          priority: t(PRIORITY_KEYS[item.priority]),
                          time: formatRelativeMs(item.timestamp, t),
                        })}
                      </Typography>
                    </Box>
                    <IconButton
                      className="notification-dismiss"
                      size="small"
                      onClick={() => removeNotification(item.id, userId)}
                      aria-label={t('center.dismiss')}
                      sx={{ opacity: 0, transition: 'opacity 0.15s', flexShrink: 0 }}
                    >
                      <CloseIcon sx={{ fontSize: 16 }} />
                    </IconButton>
                  </Box>
                );
              })
            )}
          </Box>
        )}
      </Popover>
    </>
  );
};
