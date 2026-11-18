import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OfflineBanner } from './OfflineBanner';

describe('OfflineBanner', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('renders nothing while the browser is online', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);
    const { container } = render(<OfflineBanner />);
    expect(container.innerHTML).toBe('');
    expect(screen.queryByTestId('offline-banner')).toBeNull();
  });

  it('announces offline status with role="status" and hides again once online', () => {
    let online = false;
    vi.spyOn(navigator, 'onLine', 'get').mockImplementation(() => online);

    render(<OfflineBanner />);
    const banner = screen.getByRole('status');
    expect(banner.getAttribute('data-testid')).toBe('offline-banner');
    expect(banner.getAttribute('aria-live')).toBe('polite');
    expect(banner.textContent).toContain('offline');

    online = true;
    act(() => {
      window.dispatchEvent(new Event('online'));
    });
    expect(screen.queryByTestId('offline-banner')).toBeNull();

    online = false;
    act(() => {
      window.dispatchEvent(new Event('offline'));
    });
    expect(screen.getByTestId('offline-banner')).toBeTruthy();
  });
});
