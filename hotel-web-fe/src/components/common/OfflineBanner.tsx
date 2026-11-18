import React from 'react';
import { Alert } from '@mui/material';
import { useOnlineStatus } from '../../hooks/useOnlineStatus';
import { useTranslation } from '../../i18n';

/** Accessible top banner shown whenever the browser loses network connectivity. */
export const OfflineBanner: React.FC = () => {
  const isOnline = useOnlineStatus();
  const { t } = useTranslation('common');

  if (isOnline) {
    return null;
  }

  return (
    <Alert
      severity="warning"
      role="status"
      aria-live="polite"
      data-testid="offline-banner"
      sx={{ borderRadius: 0, py: 0.5 }}
    >
      {t('state.offlineBanner')}
    </Alert>
  );
};
