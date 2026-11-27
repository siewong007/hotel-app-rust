import React, { useState } from 'react';
import { useNavigate } from '../../../router';
import {
  Container,
  Paper,
  Typography,
  TextField,
  Button,
  Box,
  Alert,
  CircularProgress,
} from '@mui/material';
import { GuestPortalService } from '../../../api';
import { setBookingAccessToken } from '../../guest-portal/api/bookingAccessTokenStore';
import { guestErrorMessage } from '../../guest-portal/utils/feedback';
import { useTranslation } from '../../../i18n';
import { useAutoFocusError } from '../../../hooks/useAutoFocusError';
import { useTurnstile } from '../../auth/turnstile/useTurnstile';
import { turnstileErrorMessage } from '../../auth/turnstile/turnstileError';

export const GuestCheckInLanding: React.FC = () => {
  const { t } = useTranslation('guestPortal');
  const { t: tAuth } = useTranslation('auth');
  const navigate = useNavigate();
  const { setContainer: setTurnstileContainer, ...turnstile } = useTurnstile('guest_portal_verify');
  const awaitingTurnstile = turnstile.enabled && !turnstile.token && !turnstile.error;
  const [bookingNumber, setBookingNumber] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const errorRef = useAutoFocusError(error);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!bookingNumber.trim() || !name.trim()) {
      setError(t('checkin.landing.errors.missingFields'));
      return;
    }
    if (turnstile.enabled && !turnstile.token) {
      setError(
        turnstile.error
          ? turnstileErrorMessage(new Error(turnstile.error), tAuth)
          : tAuth('turnstile.incomplete'),
      );
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const payload: { booking_number: string; name: string; email?: string } = {
        booking_number: bookingNumber.trim(),
        name: name.trim(),
      };
      if (email.trim()) {
        payload.email = email.trim();
      }
      const response = turnstile.token
        ? await GuestPortalService.verify(payload, turnstile.token)
        : await GuestPortalService.verify(payload);

      setBookingAccessToken(response.token);
      navigate('/guest-checkin/verify');
    } catch (err) {
      turnstile.reset();
      setError(guestErrorMessage(err, t('checkin.landing.errors.verifyFailed')));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: { xs: 3, sm: 8 } }}>
      <Paper elevation={3} sx={{ p: 4 }}>
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <Typography variant="h4" component="h1" gutterBottom>
            {t('checkin.landing.title')}
          </Typography>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            {t('checkin.landing.subtitle')}
          </Typography>
        </Box>

        {error && (
          <Alert severity="error" role="alert" ref={errorRef} tabIndex={-1} sx={{ mb: 3 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label={t('checkin.landing.bookingNumber')}
            value={bookingNumber}
            onChange={(e) => setBookingNumber(e.target.value)}
            margin="normal"
            required
            placeholder={t('checkin.landing.bookingNumberPlaceholder')}
            disabled={loading}
          />

          <TextField
            fullWidth
            label={t('checkin.landing.guestName')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            margin="normal"
            required
            placeholder={t('checkin.landing.guestNamePlaceholder')}
            autoComplete="name"
            disabled={loading}
          />

          <TextField
            fullWidth
            type="email"
            label={t('checkin.account.email')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            margin="normal"
            autoComplete="email"
            disabled={loading}
          />

          {turnstile.enabled && (
            <Box
              ref={setTurnstileContainer}
              sx={{ display: 'flex', justifyContent: 'center', mt: 2, minHeight: 65 }}
            />
          )}

          <Button
            type="submit"
            variant="contained"
            fullWidth
            size="large"
            disabled={loading || awaitingTurnstile}
            sx={{ mt: 3 }}
            startIcon={loading && <CircularProgress size={20} />}
          >
            {loading ? t('checkin.landing.verifying') : t('checkin.continue')}
          </Button>
        </form>

        <Box sx={{ mt: 3, textAlign: 'center' }}>
          <Typography variant="caption" sx={{
            color: "text.secondary"
          }}>
            {t('checkin.landing.availabilityNote')}
          </Typography>
        </Box>
      </Paper>
    </Container>
  );
};

export default GuestCheckInLanding;
