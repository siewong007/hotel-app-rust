import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserProfile } from '../../../types';

const mocks = vi.hoisted(() => ({
  profile: {
    data: undefined as UserProfile | undefined,
    isPending: false,
    isError: false,
  },
  passkeys: { data: [] as unknown[] },
  sessions: { data: [] as unknown[] },
  twoFactorStatus: { data: { enabled: false, backup_codes_remaining: 0 } },
  addPasskeyMutateAsync: vi.fn(),
}));

const emptyMutation = { mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false, error: null };

vi.mock('../../../router', () => ({
  useSearchParams: () => [new URLSearchParams(), vi.fn()],
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../../components/common/ConfirmProvider', () => ({
  useConfirm: () => vi.fn(),
}));

vi.mock('../../../auth/AuthContext', () => ({
  useAuth: () => ({
    hasPermission: () => true,
    user: { id: '1', username: 'reception' },
    roles: ['receptionist'],
  }),
}));

vi.mock('../hooks/useProfileQueries', () => ({
  useProfileQuery: () => mocks.profile,
  usePasskeysQuery: () => mocks.passkeys,
  useSessionsQuery: () => mocks.sessions,
  useUpdateProfileMutation: () => emptyMutation,
  useUpdatePasswordMutation: () => emptyMutation,
  useDeletePasskeyMutation: () => emptyMutation,
  useRenamePasskeyMutation: () => emptyMutation,
  useRegisterPasskeyMutation: () => ({
    ...emptyMutation,
    mutateAsync: mocks.addPasskeyMutateAsync,
  }),
  useRevokeSessionMutation: () => emptyMutation,
}));

vi.mock('../../auth/hooks/useTwoFactorQueries', () => ({
  useTwoFactorStatus: () => mocks.twoFactorStatus,
}));

vi.mock('../../auth/components/TwoFactorSetup', () => ({
  default: () => <div data-testid="twofactor-setup" />,
}));

import UserProfilePage from './UserProfilePage';

const renderPage = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <UserProfilePage />
    </QueryClientProvider>,
  );
import { expectNoCriticalAxeViolations } from '../../../test/axe';

const profile: UserProfile = {
  id: 1,
  username: 'reception',
  email: 'desk@hotel.test',
  email_configured: true,
  is_verified: true,
  user_type: 'staff',
  full_name: 'Front Desk',
  created_at: '2026-01-01T00:00:00Z',
  updated_at: '2026-01-01T00:00:00Z',
};

describe('UserProfilePage', () => {
  beforeEach(() => {
    mocks.profile = { data: profile, isPending: false, isError: false };
    mocks.passkeys = { data: [] };
    mocks.sessions = { data: [] };
    mocks.twoFactorStatus = { data: { enabled: false, backup_codes_remaining: 0 } };
    mocks.addPasskeyMutateAsync.mockReset().mockResolvedValue(undefined);
  });

  afterEach(cleanup);

  it('renders the identity header and every section on one page', () => {
    renderPage();
    expect(screen.queryByRole('tab')).toBeNull();
    expect(screen.getByText('@reception')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Account details' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Security' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Change Password' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /Registered Passkeys/ })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Signed-in devices' })).toBeTruthy();
    expect(screen.getByTestId('twofactor-setup')).toBeTruthy();
  });

  it('shows account details read-only until Edit Profile is pressed', () => {
    renderPage();
    expect(screen.queryByRole('textbox', { name: 'Full Name' })).toBeNull();
    expect(screen.getAllByText('desk@hotel.test').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: 'Edit Profile' }));
    expect((screen.getByRole('textbox', { name: 'Full Name' }) as HTMLInputElement).value).toBe(
      'Front Desk',
    );
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
  });

  it('shows each role as a chip in the header', () => {
    renderPage();
    expect(screen.getByText('Receptionist')).toBeTruthy();
  });

  it('shows an error when the profile fails to load', () => {
    mocks.profile = { data: undefined, isPending: false, isError: true };
    renderPage();
    expect(screen.getByText(/Failed to load user profile/)).toBeTruthy();
  });

  it('reports no critical axe violations', async () => {
    const { container } = renderPage();
    await expectNoCriticalAxeViolations(container);
  });

  it('opens a step-up confirmation dialog before registering a passkey with password', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Register Your First Passkey' }));

    expect(await screen.findByText('Confirm it is you')).toBeTruthy();
    expect(screen.queryByLabelText('6-digit code')).toBeNull();

    fireEvent.change(screen.getByLabelText('Your password'), { target: { value: 'Secret123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() =>
      expect(mocks.addPasskeyMutateAsync).toHaveBeenCalledWith({
        username: 'reception',
        stepUp: { password: 'Secret123!', totpCode: undefined },
      }),
    );
  });

  it('allows TOTP step-up verification when 2FA is enabled', async () => {
    mocks.twoFactorStatus = { data: { enabled: true, backup_codes_remaining: 5 } };
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Register Your First Passkey' }));

    const codeInput = await screen.findByLabelText('6-digit code');
    fireEvent.change(codeInput, { target: { value: '654321' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await waitFor(() =>
      expect(mocks.addPasskeyMutateAsync).toHaveBeenCalledWith({
        username: 'reception',
        stepUp: { password: undefined, totpCode: '654321' },
      }),
    );
  });
});
