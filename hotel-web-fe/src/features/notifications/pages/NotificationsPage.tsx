import React from 'react';
import {
  Box,
  Button,
  Chip,
  IconButton,
  MenuItem,
  Pagination,
  Paper,
  Select,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import DoneIcon from '@mui/icons-material/Done';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import SearchIcon from '@mui/icons-material/Search';

import PageHeader from '../../../components/common/PageHeader';
import { useAuth } from '../../../auth/AuthContext';
import { useResetOnChange } from '../../../hooks/useResetOnChange';
import { useTranslation, statusLabel } from '../../../i18n';
import { Link, useBrowserSearchParams } from '../../../router/compat';
import {
  useMarkAllStaffNotificationsRead,
  useMarkStaffNotificationRead,
  useStaffNotifications,
} from '../../admin/system/hooks';
import type {
  StaffNotificationCategory,
  StaffNotificationPriority,
} from '../../admin/system/types';
import { useDeliveryFeed } from '../hooks/useDeliveryFeed';
import { DeliveryTabs, TIER_TAB_KEYS } from '../components/DeliveryTabs';
import { StaffNotificationIcon } from '../components/StaffNotificationIcon';
import { formatRelativeMs } from '../utils/relativeTime';
import {
  categoryLabel,
  priorityLabel,
  resolveActionLabel,
  resolveActionPending,
  resolveActionUrl,
  resolveCategory,
  resolvePriority,
  resolveRequiresAction,
  resolveTitleAndBody,
} from '../utils/staffNotificationPresentation';
import type { TierFilter } from '../types';

const DELIVERY_STATUSES = ['queued', 'sending', 'sent', 'failed', 'suppressed', 'cancelled'];
const CATEGORY_OPTIONS: Array<'all' | StaffNotificationCategory> = [
  'all',
  'payments',
  'support',
  'bookings',
  'ekyc',
  'maintenance',
  'system',
];

type PageSection = 'inbox' | 'deliveries';
type InboxStatusFilter = 'action_needed' | 'unread' | 'all';
type InboxPriorityFilter = 'all' | StaffNotificationPriority;

/**
 * Full-page notifications hub: every staff member gets the filterable Staff
 * Action Inbox; holders of `communications:read` also get the outbound Guest
 * Deliveries tab.
 */
const NotificationsPage: React.FC = () => {
  const { user, hasPermission } = useAuth();
  const { t } = useTranslation('notifications');
  const searchParams = useBrowserSearchParams();
  const urlFilter = searchParams.get('filter');
  const initialUrgent = urlFilter === 'urgent';

  const canReadFeed = hasPermission('communications:read');
  const isStaff = Boolean(!user || (user as { user_type?: string }).user_type !== 'guest');

  const [section, setSection] = React.useState<PageSection>('inbox');
  const [statusFilter, setStatusFilter] = React.useState<InboxStatusFilter>('action_needed');
  const [priorityFilter, setPriorityFilter] = React.useState<InboxPriorityFilter>(
    initialUrgent ? 'urgent' : 'all',
  );
  const [categoryFilter, setCategoryFilter] = React.useState<'all' | StaffNotificationCategory>('all');
  const [searchQuery, setSearchQuery] = React.useState('');

  useResetOnChange([urlFilter], () => {
    if (urlFilter === 'urgent') {
      setSection('inbox');
      setPriorityFilter('urgent');
      setStatusFilter('action_needed');
    }
  });

  const [tier, setTier] = React.useState<TierFilter>('all');
  const [deliveryStatus, setDeliveryStatus] = React.useState('');
  const [page, setPage] = React.useState(1);

  const staffQuery = useStaffNotifications(isStaff, true);
  const markOneRead = useMarkStaffNotificationRead();
  const markAllRead = useMarkAllStaffNotificationsRead();

  const feed = useDeliveryFeed(
    { tier, status: deliveryStatus || undefined, page },
    canReadFeed,
    canReadFeed && section === 'deliveries',
  );
  const totalPages = Math.max(1, Math.ceil((feed.data?.total ?? 0) / (feed.data?.page_size ?? 20)));

  const staffUnread = staffQuery.data?.unread ?? 0;

  const filteredItems = React.useMemo(() => {
    const staffItems = staffQuery.data?.items ?? [];
    const q = searchQuery.trim().toLowerCase();
    return staffItems.filter((item) => {
      const pending = resolveActionPending(item);
      if (statusFilter === 'action_needed' && !pending) return false;
      if (statusFilter === 'unread' && item.read_at !== null) return false;

      const priority = resolvePriority(item);
      if (priorityFilter !== 'all' && priority !== priorityFilter) return false;

      const category = resolveCategory(item);
      if (categoryFilter !== 'all' && category !== categoryFilter) return false;

      if (q) {
        const { title, body } = resolveTitleAndBody(item, t);
        const haystack = `${title} ${body ?? ''} ${item.subject ?? ''}`.toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      return true;
    });
  }, [staffQuery.data?.items, statusFilter, priorityFilter, categoryFilter, searchQuery, t]);

  const headerTitle = section === 'deliveries' && canReadFeed ? t('pageTitle') : t('inboxTitle');
  const headerSubtitle =
    section === 'deliveries' && canReadFeed ? t('pageSubtitle') : t('inboxSubtitle');

  return (
    <Box sx={{ p: { xs: 2, sm: 3 } }}>
      <PageHeader
        title={headerTitle}
        subtitle={headerSubtitle}
        actions={
          section === 'inbox' ? (
            <Button
              size="small"
              variant="outlined"
              startIcon={<DoneAllIcon />}
              disabled={staffUnread === 0 || markAllRead.isPending}
              onClick={() => markAllRead.mutate()}
              sx={{ textTransform: 'none' }}
            >
              {t('markAllRead')}
            </Button>
          ) : (
            <Select
              size="small"
              value={deliveryStatus}
              onChange={(e) => {
                setDeliveryStatus(e.target.value);
                setPage(1);
              }}
              displayEmpty
              aria-label={t('common:filters.byStatus')}
              sx={{ minWidth: 140 }}
            >
              <MenuItem value="">{t('common:filters.allStatuses')}</MenuItem>
              {DELIVERY_STATUSES.map((s) => (
                <MenuItem key={s} value={s}>
                  {statusLabel(t, 'email_delivery', s)}
                </MenuItem>
              ))}
            </Select>
          )
        }
      />

      {canReadFeed && (
        <Tabs
          value={section}
          onChange={(_, next: PageSection) => setSection(next)}
          sx={{ mb: 2, borderBottom: '1px solid', borderColor: 'divider' }}
        >
          <Tab
            value="inbox"
            label={
              staffUnread > 0
                ? `${t('tabs.actionInbox')} (${staffUnread})`
                : t('tabs.actionInbox')
            }
          />
          <Tab value="deliveries" label={t('tabs.guestDeliveries')} />
        </Tabs>
      )}

      {section === 'inbox' ? (
        <Stack spacing={2}>
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 2 }}>
            <Stack spacing={1.5}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
              >
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 0.75 }}>
                  {(
                    [
                      ['action_needed', t('filters.actionNeeded')],
                      ['unread', t('filters.unread')],
                      ['all', t('filters.all')],
                    ] as Array<[InboxStatusFilter, string]>
                  ).map(([value, label]) => (
                    <Chip
                      key={value}
                      clickable
                      label={label}
                      color={statusFilter === value ? 'primary' : 'default'}
                      variant={statusFilter === value ? 'filled' : 'outlined'}
                      onClick={() => setStatusFilter(value)}
                    />
                  ))}
                </Stack>

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ minWidth: { sm: 340 } }}>
                  <Select
                    size="small"
                    value={priorityFilter}
                    onChange={(e) => setPriorityFilter(e.target.value as InboxPriorityFilter)}
                    aria-label={t('filters.allPriorities')}
                    sx={{ minWidth: 140 }}
                  >
                    <MenuItem value="all">{t('filters.allPriorities')}</MenuItem>
                    <MenuItem value="urgent">{t('staffPriority.urgent')}</MenuItem>
                    <MenuItem value="high">{t('staffPriority.high')}</MenuItem>
                    <MenuItem value="normal">{t('staffPriority.normal')}</MenuItem>
                  </Select>

                  <TextField
                    size="small"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={t('filters.searchPlaceholder')}
                    slotProps={{
                      htmlInput: {
                        'aria-label': t('filters.searchPlaceholder'),
                      },
                      input: {
                        startAdornment: (
                          <SearchIcon fontSize="small" sx={{ mr: 0.75, color: 'text.secondary' }} />
                        ),
                      },
                    }}
                    sx={{ flex: 1 }}
                  />
                </Stack>
              </Stack>

              <Stack direction="row" spacing={0.75} sx={{ flexWrap: 'wrap', gap: 0.75 }}>
                {CATEGORY_OPTIONS.map((category) => (
                  <Chip
                    key={category}
                    size="small"
                    clickable
                    label={category === 'all' ? t('categories.all') : categoryLabel(category, t)}
                    color={categoryFilter === category ? 'secondary' : 'default'}
                    variant={categoryFilter === category ? 'filled' : 'outlined'}
                    onClick={() => setCategoryFilter(category)}
                  />
                ))}
              </Stack>
            </Stack>
          </Paper>

          {filteredItems.length === 0 ? (
            <Paper variant="outlined" sx={{ p: 5, textAlign: 'center', borderRadius: 2 }}>
              <NotificationsNoneIcon sx={{ fontSize: 38, color: 'text.disabled', mb: 1 }} />
              <Typography color="text.secondary">
                {staffQuery.isPending ? t('common:state.loading') : t('center.noActionItems')}
              </Typography>
            </Paper>
          ) : (
            <Stack spacing={1.25}>
              {filteredItems.map((item) => {
                const priority = resolvePriority(item);
                const category = resolveCategory(item);
                const pending = resolveActionPending(item);
                const requiresAction = resolveRequiresAction(item);
                const actionUrl = resolveActionUrl(item);
                const ctaLabel = resolveActionLabel(item, t);
                const { title, body } = resolveTitleAndBody(item, t);
                const unreadPending = item.read_at === null && pending;

                return (
                  <Paper
                    key={item.id}
                    variant="outlined"
                    sx={{
                      p: 2,
                      borderRadius: 2,
                      borderLeftWidth: 4,
                      borderLeftColor: !pending
                        ? 'divider'
                        : priority === 'urgent'
                          ? 'error.main'
                          : priority === 'high'
                            ? 'warning.main'
                            : 'info.main',
                      bgcolor: unreadPending ? 'action.hover' : 'background.paper',
                      opacity: pending ? 1 : 0.78,
                    }}
                  >
                    <Box
                      sx={{
                        display: 'flex',
                        alignItems: { xs: 'flex-start', sm: 'center' },
                        flexDirection: { xs: 'column', sm: 'row' },
                        gap: 1.5,
                      }}
                    >
                      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5, flex: 1, minWidth: 0 }}>
                        <Box sx={{ mt: 0.25 }}>
                          <StaffNotificationIcon category={category} priority={priority} pending={pending} />
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap', mb: 0.5 }}>
                            {priority !== 'normal' && (
                              <Chip
                                size="small"
                                label={priorityLabel(priority, t)}
                                color={priority === 'urgent' ? 'error' : 'warning'}
                                variant={pending ? 'filled' : 'outlined'}
                                sx={{ height: 20, fontSize: '0.7rem', fontWeight: 700 }}
                              />
                            )}
                            <Chip
                              size="small"
                              label={categoryLabel(category, t)}
                              variant="outlined"
                              sx={{ height: 20, fontSize: '0.7rem' }}
                            />
                            {requiresAction && (
                              <Chip
                                size="small"
                                label={pending ? t('center.actionNeeded') : t('center.handled')}
                                color={pending ? 'primary' : 'success'}
                                variant="outlined"
                                sx={{ height: 20, fontSize: '0.7rem' }}
                              />
                            )}
                            <Typography variant="caption" color="text.secondary">
                              {formatRelativeMs(Date.parse(item.created_at), t)}
                            </Typography>
                          </Box>

                          <Typography variant="subtitle2" component="h2" sx={{ fontWeight: unreadPending ? 700 : 500 }}>
                            {title}
                          </Typography>
                          {body ? (
                            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                              {body}
                            </Typography>
                          ) : null}
                        </Box>
                      </Box>

                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
                        {actionUrl ? (
                          <Button
                            size="small"
                            component={Link}
                            to={actionUrl}
                            onClick={() => {
                              if (item.read_at === null) markOneRead.mutate(item.id);
                            }}
                            variant={pending ? 'contained' : 'outlined'}
                            color={priority === 'urgent' && pending ? 'error' : 'primary'}
                            sx={{ textTransform: 'none' }}
                          >
                            {ctaLabel}
                          </Button>
                        ) : null}
                        {item.read_at === null && (
                          <Tooltip title={t('center.markRead')}>
                            <IconButton
                              size="small"
                              aria-label={t('center.markRead')}
                              onClick={() => markOneRead.mutate(item.id)}
                            >
                              <DoneIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Box>
                    </Box>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Stack>
      ) : (
        <>
          <Box
            sx={{
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 2,
              overflow: 'hidden',
              bgcolor: 'background.paper',
            }}
          >
            <DeliveryTabs
              tier={tier}
              onTierChange={(next) => {
                setTier(next);
                setPage(1);
              }}
              items={feed.data?.items ?? []}
              emptyMessage={
                feed.isPending
                  ? t('common:state.loading')
                  : t('tierEmpty', { tier: t(TIER_TAB_KEYS[tier]) })
              }
            />
          </Box>

          {(feed.data?.total ?? 0) > (feed.data?.page_size ?? 20) ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Pagination
                count={totalPages}
                page={page}
                onChange={(_, next) => setPage(next)}
                color="primary"
              />
            </Box>
          ) : (
            (feed.data?.total ?? 0) === 0 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
                <Button startIcon={<NotificationsNoneIcon />} disabled sx={{ textTransform: 'none' }}>
                  {t('noDeliveries')}
                </Button>
              </Box>
            )
          )}
        </>
      )}
    </Box>
  );
};

export default NotificationsPage;
