import React from 'react';
import { Alert, Button, Snackbar, Typography } from '@mui/material';
import { useAuth } from '../../auth/AuthContext';
import { useTranslation } from '../../i18n';
import { useNavigate } from '../../router/compat';
import {
  useMarkStaffNotificationRead,
  useStaffNotifications,
} from '../../features/admin/system/hooks';
import type { StaffNotificationItem } from '../../features/admin/system/types';
import {
  resolveActionLabel,
  resolveActionPending,
  resolveActionUrl,
  resolvePriority,
  resolveTitleAndBody,
} from '../../features/notifications/utils/staffNotificationPresentation';

/**
 * Displays a live, clickable toast whenever a new actionable staff
 * notification arrives after initial mount (no toast flood on page load).
 */
export const StaffAlertToaster: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation('notifications');
  const navigate = useNavigate();

  const isStaff = Boolean(user && user.user_type !== 'guest');
  const query = useStaffNotifications(isStaff, false);
  const markRead = useMarkStaffNotificationRead();

  const seenIdsRef = React.useRef<Set<number> | null>(null);
  const lastUserIdRef = React.useRef<number | undefined>(undefined);
  const [queue, setQueue] = React.useState<StaffNotificationItem[]>([]);

  React.useEffect(() => {
    const uid = user?.id;
    if (lastUserIdRef.current !== uid) {
      lastUserIdRef.current = uid;
      seenIdsRef.current = null;
      setQueue([]);
    }
  }, [user?.id]);

  React.useEffect(() => {
    if (!isStaff || !query.data) return;
    const items = query.data.items ?? [];
    if (seenIdsRef.current === null) {
      seenIdsRef.current = new Set(items.map((item) => item.id));
      return;
    }

    const fresh: StaffNotificationItem[] = [];
    for (const item of items) {
      if (!seenIdsRef.current.has(item.id)) {
        seenIdsRef.current.add(item.id);
        if (item.read_at === null && resolveActionPending(item)) {
          fresh.push(item);
        }
      }
    }

    if (fresh.length > 0) {
      setQueue((prev) => [...prev, ...fresh]);
    }
  }, [isStaff, query.data]);

  const current = queue[0] ?? null;
  if (!isStaff || !current) return null;

  const priority = resolvePriority(current);
  const severity = priority === 'urgent' ? 'error' : priority === 'high' ? 'warning' : 'info';
  const { title, body } = resolveTitleAndBody(current, t);
  const actionUrl = resolveActionUrl(current);
  const ctaLabel = resolveActionLabel(current, t);

  const dismissCurrent = () => {
    setQueue((prev) => prev.slice(1));
  };

  const handleOpenAction = () => {
    markRead.mutate(current.id);
    dismissCurrent();
    navigate(actionUrl ?? '/notifications');
  };

  return (
    <Snackbar
      open
      autoHideDuration={priority === 'normal' ? 7_000 : 10_000}
      onClose={(_, reason) => {
        if (reason === 'clickaway') return;
        dismissCurrent();
      }}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
    >
      <Alert
        severity={severity}
        variant="filled"
        onClose={dismissCurrent}
        sx={{
          width: '100%',
          maxWidth: 420,
          alignItems: 'center',
          boxShadow: 6,
        }}
        action={
          <Button
            color="inherit"
            size="small"
            variant="outlined"
            onClick={handleOpenAction}
            sx={{
              borderColor: 'rgba(255,255,255,0.6)',
              fontWeight: 700,
              textTransform: 'none',
              whiteSpace: 'nowrap',
            }}
          >
            {ctaLabel}
          </Button>
        }
      >
        <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.25 }}>
          {title}
        </Typography>
        {body ? (
          <Typography variant="caption" sx={{ display: 'block', opacity: 0.94, mt: 0.25 }}>
            {body}
          </Typography>
        ) : null}
      </Alert>
    </Snackbar>
  );
};

export default StaffAlertToaster;
