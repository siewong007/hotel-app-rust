import React, { useCallback, useEffect, useRef, useState } from 'react';
import { HTTPError } from 'ky';
import { useSearchParams } from '../../../router';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { LogoLoader } from '../../../components';
import { useAuth } from '../../../auth/AuthContext';
import type { UserProfileUpdate, UserSessionInfo } from '../../../types';
import { ApiNotificationSeverity, emitApiNotification } from '../../../utils/apiNotifications';
import TwoFactorSetup from '../../auth/components/TwoFactorSetup';
import { useTwoFactorStatus } from '../../auth/hooks/useTwoFactorQueries';
import { useAutoFocusError } from '../../../hooks/useAutoFocusError';
import {
  useDeletePasskeyMutation,
  usePasskeysQuery,
  useProfileQuery,
  useRegisterPasskeyMutation,
  useRenamePasskeyMutation,
  useRevokeSessionMutation,
  useSessionsQuery,
  useUpdatePasswordMutation,
  useUpdateProfileMutation,
} from '../hooks/useProfileQueries';
import ProfileHeaderCard from './profile/ProfileHeaderCard';
import ProfileTab from './profile/ProfileTab';
import SecurityTab from './profile/SecurityTab';
import PasskeysTab, { MAX_PASSKEYS } from './profile/PasskeysTab';
import DevicesTab from './profile/DevicesTab';
import { useConfirm } from '../../../components/common/ConfirmProvider';
import { useTranslation } from '../../../i18n';

// MUI's visuallyHidden recipe, inlined (@mui/utils is not a direct dependency).
const VISUALLY_HIDDEN = {
  border: 0,
  clip: 'rect(0 0 0 0)',
  height: '1px',
  margin: '-1px',
  overflow: 'hidden',
  padding: 0,
  position: 'absolute',
  whiteSpace: 'nowrap',
  width: '1px',
} as const;

const TOTP_CODE_LENGTH = 6;

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

const UserProfilePage: React.FC = () => {
  const { t } = useTranslation('auth');
  const [searchParams, setSearchParams] = useSearchParams();
  const confirm = useConfirm();
  const [editing, setEditing] = useState(false);
  const [stepUpAction, setStepUpAction] = useState<
    | { kind: 'addPasskey' }
    | { kind: 'deletePasskey'; id: string }
    | { kind: 'updateEmail'; data: UserProfileUpdate }
    | null
  >(null);
  const [stepUpPassword, setStepUpPassword] = useState('');
  const [stepUpCode, setStepUpCode] = useState('');
  const [stepUpError, setStepUpError] = useState<string | null>(null);
  const stepUpErrorRef = useAutoFocusError(stepUpError);
  const { registerPasskey, roles, logout } = useAuth();

  const profileQuery = useProfileQuery();
  const passkeysQuery = usePasskeysQuery();
  const sessionsQuery = useSessionsQuery();
  const { data: twoFactorStatus } = useTwoFactorStatus({ suppressApiNotification: true });
  const twoFactorEnabled = Boolean(twoFactorStatus?.enabled);

  const updateProfile = useUpdateProfileMutation();
  const updatePassword = useUpdatePasswordMutation();
  const deletePasskey = useDeletePasskeyMutation();
  const renamePasskey = useRenamePasskeyMutation();
  const addPasskey = useRegisterPasskeyMutation(registerPasskey);
  const revokeSession = useRevokeSessionMutation();

  // Phone-only jump links: the page is one scrolling column there, so these
  // replace the old tab bar (whose last two tabs were scrolled out of view).
  const SECTIONS = [
    { id: 'profile-account', label: t('profile.sections.accountDetails') },
    { id: 'profile-security', label: t('profile.tabs.security') },
    { id: 'profile-devices', label: t('profile.tabs.devices') },
  ];
  const jumpTo = (id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const notify = useCallback((message: string, severity: ApiNotificationSeverity) => {
    emitApiNotification({ message, severity });
  }, []);

  // Auto-enter edit mode from a one-time `?edit=true` URL param, then strip it.
  // Guarded so it only acts once (setSearchParams below would otherwise
  // re-trigger this effect via the new searchParams identity).
  const hasCheckedEditParamRef = useRef(false);
  useEffect(() => {
    if (hasCheckedEditParamRef.current) return;
    hasCheckedEditParamRef.current = true;

    if (searchParams.get('edit') === 'true') {
      setEditing(true);
      searchParams.delete('edit');
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const profile = profileQuery.data;

  const handleSaveProfile = async (data: UserProfileUpdate) => {
    const isStaffEmailChange =
      profile?.user_type !== 'guest' &&
      Boolean(data.email) &&
      Boolean(profile?.email) &&
      data.email!.trim().toLowerCase() !== profile!.email.trim().toLowerCase();
    if (isStaffEmailChange) {
      setStepUpError(null);
      setStepUpPassword('');
      setStepUpCode('');
      setStepUpAction({ kind: 'updateEmail', data });
      return;
    }

    const isAddingGuestEmail =
      profile?.user_type === 'guest' && !profile.email_configured && Boolean(data.email);
    try {
      await updateProfile.mutateAsync(data);
      setEditing(false);
      notify(
        isAddingGuestEmail
          ? t('profile.emailAddedVerify')
          : t('profile.updated'),
        'success'
      );
    } catch (error) {
      notify(errorMessage(error, t('profile.updateFailed')), 'error');
    }
  };

  const handleUpdatePassword = async (data: {
    current_password: string;
    new_password: string;
  }) => {
    try {
      await updatePassword.mutateAsync(data);
      notify(t('security.passwordUpdated'), 'success');
      // Password changes revoke all active sessions & passkeys server-side;
      // transition cleanly to /login instead of trapping the user in a revoked session.
      void logout({ redirectTo: '/login' });
    } catch (error) {
      // The backend returns 401 when the current password is wrong; the
      // generic coded-error message would misread that as a sign-in prompt.
      const isWrongCurrentPassword =
        error instanceof HTTPError && error.response.status === 401;
      notify(
        isWrongCurrentPassword
          ? t('security.currentPasswordIncorrect')
          : errorMessage(error, t('security.passwordUpdateFailed')),
        isWrongCurrentPassword ? 'warning' : 'error'
      );
      throw error;
    }
  };

  const closeStepUp = () => {
    setStepUpAction(null);
    setStepUpPassword('');
    setStepUpCode('');
    setStepUpError(null);
  };

  const handleAddPasskey = () => {
    const passkeys = passkeysQuery.data ?? [];
    if (passkeys.length >= MAX_PASSKEYS) {
      notify(t('passkeys.limitReached', { max: MAX_PASSKEYS }), 'warning');
      return;
    }
    if (!profile) return;
    setStepUpError(null);
    setStepUpPassword('');
    setStepUpCode('');
    setStepUpAction({ kind: 'addPasskey' });
  };

  const stepUpPending = addPasskey.isPending || deletePasskey.isPending || updateProfile.isPending;

  const submitStepUp = async () => {
    if (!profile || !stepUpAction) return;
    setStepUpError(null);
    try {
      if (stepUpAction.kind === 'addPasskey') {
        await addPasskey.mutateAsync({
          username: profile.username,
          stepUp: {
            password: stepUpPassword || undefined,
            totpCode: stepUpCode || undefined,
          },
        });
        closeStepUp();
        notify(t('passkeys.registered'), 'success');
      } else if (stepUpAction.kind === 'deletePasskey') {
        await deletePasskey.mutateAsync({
          id: stepUpAction.id,
          stepUp: {
            password: stepUpPassword || undefined,
            totpCode: stepUpCode || undefined,
          },
        });
        closeStepUp();
        notify(t('passkeys.deleted'), 'success');
      } else {
        await updateProfile.mutateAsync({
          ...stepUpAction.data,
          current_password: stepUpPassword || undefined,
          totp_code: stepUpCode || undefined,
        });
        closeStepUp();
        setEditing(false);
        notify(t('profile.updated'), 'success');
      }
    } catch (error) {
      const fallback =
        stepUpAction.kind === 'addPasskey'
          ? t('passkeys.registerFailed')
          : stepUpAction.kind === 'deletePasskey'
            ? t('passkeys.deleteFailed')
            : t('profile.updateFailed');
      setStepUpError(errorMessage(error, fallback));
    }
  };

  const handleDeletePasskey = async (id: string) => {
    const accepted = await confirm({
      title: t('passkeys.delete'),
      message: t('passkeys.deleteMessage'),
      confirmText: t('passkeys.delete'),
      severity: 'error',
    });
    if (!accepted) return;
    setStepUpError(null);
    setStepUpPassword('');
    setStepUpCode('');
    setStepUpAction({ kind: 'deletePasskey', id });
  };

  const handleRenamePasskey = async (id: string, deviceName: string) => {
    try {
      await renamePasskey.mutateAsync({ id, deviceName });
      notify(t('passkeys.nameUpdated'), 'success');
    } catch (error) {
      notify(errorMessage(error, t('passkeys.nameUpdateFailed')), 'error');
    }
  };

  const handleRevokeSession = async (session: UserSessionInfo) => {
    const accepted = await confirm({
      title: t('devices.revokeTitle'),
      message: t('devices.revokeMessage'),
      confirmText: t('devices.revoke'),
      severity: 'warning',
    });
    if (!accepted) return;
    try {
      await revokeSession.mutateAsync(session.id);
      notify(t('devices.revoked'), 'success');
    } catch (error) {
      notify(errorMessage(error, t('devices.revokeFailed')), 'error');
    }
  };

  if (profileQuery.isPending) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '60vh',
        }}
      >
        <LogoLoader variant="inline" size={48} />
      </Box>
    );
  }

  if (!profile) {
    return <Alert severity="error">{t('profile.loadFailed')}</Alert>;
  }

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto' }}>
      {/* The phone top bar already names the page, so the heading is kept for
          screen readers only there. */}
      <Typography
        variant="h4"
        component="h1"
        sx={theme => ({
          fontWeight: 700,
          mb: 3,
          color: 'primary.main',
          [theme.breakpoints.down('sm')]: VISUALLY_HIDDEN,
        })}
      >
        {t('profile.title')}
      </Typography>

      <ProfileHeaderCard profile={profile} roles={roles} />

      <Stack
        component="nav"
        aria-label={t('common:actions.sections')}
        direction="row"
        spacing={1}
        sx={{
          display: { xs: 'flex', md: 'none' },
          mt: 2,
          overflowX: 'auto',
          scrollbarWidth: 'none',
          '&::-webkit-scrollbar': { display: 'none' },
        }}
      >
        {SECTIONS.map(section => (
          <Chip
            key={section.id}
            label={section.label}
            variant="outlined"
            onClick={() => jumpTo(section.id)}
            sx={{ height: 44, borderRadius: 22, px: 0.5, flexShrink: 0, fontWeight: 600 }}
          />
        ))}
      </Stack>

      {/* One column on phones (account, security, devices); on desktop the
          account and device cards sit left and the security stack right. */}
      <Box
        sx={{
          display: 'grid',
          gap: { xs: 2, md: 3 },
          mt: { xs: 2, md: 3 },
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))' },
          // The security stack spans both rows; `1fr` hands its extra height
          // to the second row so Devices sits right under Account details.
          gridTemplateRows: { md: 'auto 1fr' },
          gridTemplateAreas: {
            xs: '"account" "security" "devices"',
            md: '"account security" "devices security"',
          },
          alignItems: 'start',
          // Jump links land below the sticky top bar, not under it.
          '& > section': { scrollMarginTop: 80 },
        }}
      >
        <Box component="section" id="profile-account" sx={{ gridArea: 'account', minWidth: 0 }}>
          <ProfileTab
            profile={profile}
            editing={editing}
            onEditingChange={setEditing}
            onSave={handleSaveProfile}
            notify={notify}
          />
        </Box>

        <Box
          component="section"
          id="profile-security"
          aria-labelledby="profile-security-heading"
          sx={{ gridArea: 'security', minWidth: 0 }}
        >
          <Typography
            id="profile-security-heading"
            variant="overline"
            component="h2"
            sx={{ display: 'block', color: 'text.secondary', fontWeight: 700, lineHeight: 1.5, mb: 1, px: 0.5 }}
          >
            {t('profile.tabs.security')}
          </Typography>
          <Stack spacing={{ xs: 2, md: 3 }}>
            <SecurityTab onUpdatePassword={handleUpdatePassword} notify={notify} />
            <TwoFactorSetup />
            <PasskeysTab
              passkeys={passkeysQuery.data ?? []}
              onAdd={handleAddPasskey}
              onDelete={handleDeletePasskey}
              onRename={handleRenamePasskey}
              notify={notify}
            />
          </Stack>
        </Box>

        <Box component="section" id="profile-devices" sx={{ gridArea: 'devices', minWidth: 0 }}>
          <DevicesTab sessions={sessionsQuery.data ?? []} onRevoke={handleRevokeSession} />
        </Box>
      </Box>

      <Dialog open={stepUpAction !== null} onClose={closeStepUp} maxWidth="xs" fullWidth>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void submitStepUp();
          }}
        >
          <DialogTitle>{t('passkeys.stepUpTitle')}</DialogTitle>
          <DialogContent>
            <Typography sx={{ mb: 2 }}>
              {stepUpAction?.kind === 'deletePasskey'
                ? t('passkeys.deleteMessage')
                : stepUpAction?.kind === 'updateEmail'
                  ? t('security.currentPasswordHint')
                  : t('passkeys.stepUpBody')}
            </Typography>
            {stepUpError ? (
              <Alert severity="error" role="alert" ref={stepUpErrorRef} tabIndex={-1} sx={{ mb: 2 }}>
                {stepUpError}
              </Alert>
            ) : null}
            <TextField
              fullWidth
              autoFocus
              type="password"
              label={t('passkeys.passwordLabel')}
              autoComplete="current-password"
              value={stepUpPassword}
              onChange={(event) => setStepUpPassword(event.target.value)}
            />
            {twoFactorEnabled ? (
              <>
                <Typography variant="body2" sx={{ color: 'text.secondary', my: 1.5 }}>
                  {t('passkeys.orAuthenticator')}
                </Typography>
                <TextField
                  fullWidth
                  label={t('passkeys.codeLabel')}
                  value={stepUpCode}
                  onChange={(event) =>
                    setStepUpCode(event.target.value.replace(/\D/g, '').slice(0, TOTP_CODE_LENGTH))
                  }
                  slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: TOTP_CODE_LENGTH } }}
                />
              </>
            ) : null}
          </DialogContent>
          <DialogActions>
            <Button onClick={closeStepUp}>{t('common:actions.cancel')}</Button>
            <Button
              type="submit"
              variant="contained"
              disabled={
                (twoFactorEnabled ? stepUpCode.length !== TOTP_CODE_LENGTH : !stepUpPassword) ||
                stepUpPending
              }
            >
              {stepUpPending ? t('passkeys.waiting') : t('passkeys.continue')}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
};

export default UserProfilePage;
