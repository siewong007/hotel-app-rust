import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APIError } from '../../../../api/client';

const mocks = vi.hoisted(() => ({
  getEkycStatus: vi.fn(),
  uploadEkycDocument: vi.fn(),
  submitEkycVerification: vi.fn(),
}));

vi.mock('../../api/guestPortalDashboard.service', () => ({
  GuestPortalDashboardService: {
    getEkycStatus: (...args: unknown[]) => mocks.getEkycStatus(...args),
    uploadEkycDocument: (...args: unknown[]) => mocks.uploadEkycDocument(...args),
    submitEkycVerification: (...args: unknown[]) => mocks.submitEkycVerification(...args),
  },
}));

import { IdentitySection } from './IdentitySection';

describe('IdentitySection', () => {
  beforeEach(() => {
    mocks.getEkycStatus.mockReset();
    mocks.uploadEkycDocument.mockReset();
    mocks.submitEkycVerification.mockReset();
  });

  afterEach(cleanup);

  it('shows the capture form when the guest has never submitted', async () => {
    mocks.getEkycStatus.mockResolvedValue(null);

    render(<IdentitySection token="guest-token" />);

    expect(await screen.findByRole('button', { name: /Submit for verification/ })).toBeTruthy();
    expect(mocks.getEkycStatus).toHaveBeenCalledWith('guest-token');
  });

  it('hides the form while a submission is still under review, because a second one would be rejected', async () => {
    mocks.getEkycStatus.mockResolvedValue({
      id: 1,
      status: 'pending_manual_review',
      submitted_at: '2026-07-20T08:00:00Z',
    });

    render(<IdentitySection token="guest-token" />);

    expect(await screen.findByText('Under review')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Submit for verification/ })).toBeNull();
  });

  it('shows the verified state without a form', async () => {
    mocks.getEkycStatus.mockResolvedValue({ id: 2, status: 'approved' });

    render(<IdentitySection token="guest-token" />);

    expect(await screen.findByText('Verified')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Submit for verification/ })).toBeNull();
  });

  it('lets the guest resubmit when more information is required, and surfaces the reviewer note', async () => {
    mocks.getEkycStatus.mockResolvedValue({
      id: 3,
      status: 'additional_information_required',
      customer_message: 'The photo of your passport was too blurry to read.',
    });

    render(<IdentitySection token="guest-token" />);

    expect(await screen.findByText('More information needed')).toBeTruthy();
    expect(screen.getByText('The photo of your passport was too blurry to read.')).toBeTruthy();
    // The whole point of this status is that the guest can act on it.
    expect(screen.getByRole('button', { name: /Submit for verification/ })).toBeTruthy();
  });

  // exists_open_for_guest excludes 'rejected', so the API accepts a fresh
  // submission. The UI must not be stricter than the endpoint behind it.
  it('lets the guest try again after a rejection, matching what the API allows', async () => {
    mocks.getEkycStatus.mockResolvedValue({ id: 4, status: 'rejected' });

    render(<IdentitySection token="guest-token" />);

    expect(await screen.findByText('Not accepted')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Submit for verification/ })).toBeTruthy();
  });

  it('does not demand an ID back photo when the ID type is a passport', async () => {
    mocks.getEkycStatus.mockResolvedValue(null);

    render(<IdentitySection token="guest-token" />);

    // Passport is the default: submitting surfaces the missing fields, but the
    // ID-back photo must not be among them.
    fireEvent.click(await screen.findByRole('button', { name: /Submit for verification/ }));
    await waitFor(() => {
      expect(screen.getByText('Please complete the highlighted fields before submitting.')).toBeTruthy();
    });
    // Field-level errors render as resolved translations, not raw keys.
    expect(await screen.findByText('ID front photo is required.')).toBeTruthy();
    expect(screen.getByText('Full name is required.')).toBeTruthy();
    expect(screen.queryByText('ID back photo is required.')).toBeNull();
    expect(mocks.submitEkycVerification).not.toHaveBeenCalled();
  });

  it('uploads a document as soon as it is picked and marks the slot', async () => {
    mocks.getEkycStatus.mockResolvedValue(null);
    mocks.uploadEkycDocument.mockResolvedValue({
      success: true,
      file_path: 'private_uploads/ekyc/42_id_front_x.jpg',
      filename: 'private_uploads/ekyc/42_id_front_x.jpg',
      document_type: 'id_front',
    });

    render(<IdentitySection token="guest-token" />);
    await screen.findByRole('button', { name: /Submit for verification/ });

    const file = new File(['bytes'], 'front.jpg', { type: 'image/jpeg' });
    const inputs = document.querySelectorAll('input[type="file"]');
    fireEvent.change(inputs[0] as HTMLInputElement, { target: { files: [file] } });

    await waitFor(() => {
      expect(mocks.uploadEkycDocument).toHaveBeenCalledWith(file, 'id_front', 'guest-token');
    });
    expect(await screen.findByText('Uploaded')).toBeTruthy();
  });

  // A 429 from the guest_portal_ekyc limiter and a 403 from a deactivated
  // account both land in the upload catch. Telling the guest to "try a
  // different photo" would be wrong and unactionable for both.
  it('surfaces the server message when an upload is refused', async () => {
    mocks.getEkycStatus.mockResolvedValue(null);
    mocks.uploadEkycDocument.mockRejectedValue(
      new APIError('Too many verification attempts. Please try again in 540 seconds.', 429),
    );

    render(<IdentitySection token="guest-token" />);
    await screen.findByRole('button', { name: /Submit for verification/ });

    const inputs = document.querySelectorAll('input[type="file"]');
    fireEvent.change(inputs[0] as HTMLInputElement, {
      target: { files: [new File(['b'], 'f.jpg', { type: 'image/jpeg' })] },
    });

    expect(
      await screen.findByText('Too many verification attempts. Please try again in 540 seconds.'),
    ).toBeTruthy();
    expect(screen.queryByText(/try a different photo/)).toBeNull();
  });

  it('offers a retry when the status cannot be loaded', async () => {
    mocks.getEkycStatus.mockRejectedValue(new Error('network down'));

    render(<IdentitySection token="guest-token" />);

    expect(await screen.findByText('Unable to load your verification status right now.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy();
  });

  it('requires PDPA identity verification consent before submitting and includes consents on submit', async () => {
    mocks.getEkycStatus.mockResolvedValue(null);
    mocks.uploadEkycDocument
      .mockResolvedValueOnce({
        success: true,
        file_path: 'private_uploads/ekyc/42_id_front_x.jpg',
        filename: '42_id_front_x.jpg',
        document_type: 'id_front',
      })
      .mockResolvedValueOnce({
        success: true,
        file_path: 'private_uploads/ekyc/42_selfie_x.jpg',
        filename: '42_selfie_x.jpg',
        document_type: 'selfie',
      });
    mocks.submitEkycVerification.mockResolvedValue({ id: 99, status: 'pending_manual_review' });

    render(<IdentitySection token="guest-token" />);
    const submit = await screen.findByRole('button', { name: /Submit for verification/ });

    fireEvent.change(screen.getByLabelText(/Full name \(as on your ID\)/), { target: { value: 'Lin Wei' } });
    fireEvent.change(screen.getByLabelText(/Date of birth/), { target: { value: '1992-05-14' } });
    fireEvent.change(screen.getByLabelText(/Nationality/), { target: { value: 'Malaysian' } });
    fireEvent.change(screen.getByLabelText(/ID number/), { target: { value: 'A12345678' } });
    fireEvent.change(screen.getByLabelText(/ID expiry date/), { target: { value: '2032-12-31' } });
    fireEvent.change(screen.getByLabelText(/Issuing country/), { target: { value: 'Malaysia' } });
    fireEvent.change(screen.getByLabelText(/^Phone/), { target: { value: '+60123456789' } });
    fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: 'lin@example.com' } });
    fireEvent.change(screen.getByLabelText(/Current address/), { target: { value: '12 Jalan Test, Sibu' } });

    const inputs = document.querySelectorAll('input[type="file"]');
    fireEvent.change(inputs[0] as HTMLInputElement, {
      target: { files: [new File(['front'], 'front.jpg', { type: 'image/jpeg' })] },
    });
    await waitFor(() => expect(mocks.uploadEkycDocument).toHaveBeenCalledTimes(1));
    fireEvent.change(inputs[2] as HTMLInputElement, {
      target: { files: [new File(['selfie'], 'selfie.jpg', { type: 'image/jpeg' })] },
    });
    await waitFor(() => expect(mocks.uploadEkycDocument).toHaveBeenCalledTimes(2));

    // Attempt without ticking consent checkbox
    fireEvent.click(submit);
    expect(await screen.findByText('You need to agree to this before you can continue.')).toBeTruthy();
    expect(mocks.submitEkycVerification).not.toHaveBeenCalled();

    // Tick every consent checkbox and resubmit
    for (const box of screen.getAllByRole('checkbox')) {
      fireEvent.click(box);
    }
    fireEvent.click(submit);

    await waitFor(() => expect(mocks.submitEkycVerification).toHaveBeenCalledTimes(1));
    const [payload, tok] = mocks.submitEkycVerification.mock.calls[0];
    expect(tok).toBe('guest-token');
    expect(Array.isArray(payload.consents)).toBe(true);
    expect(payload.consents.length).toBeGreaterThan(0);
    expect(payload.consents.every((c: { granted: boolean }) => c.granted)).toBe(true);
  });
});
