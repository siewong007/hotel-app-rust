import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrl,
  getApiBaseUrl,
  getDesktopClientToken,
  getDesktopStatus,
  resolveApiRequestUrl,
  restartApp,
  setDesktopClientToken,
  setRuntimeApiBaseUrl,
} from './runtimeApi';

describe('runtime API base resolution', () => {
  afterEach(() => {
    window.history.replaceState({}, '', '/');
    setRuntimeApiBaseUrl('');
    window.sessionStorage.clear();
    setDesktopClientToken(null);
    delete (window as unknown as { __TAURI_INTERNALS__?: unknown }).__TAURI_INTERNALS__;
    vi.unstubAllEnvs();
  });

  it('stores desktop_client_token in memory on getDesktopStatus without touching storage', async () => {
    vi.stubEnv('VITE_APP_TARGET', 'tauri');
    const invoke = vi.fn().mockResolvedValue({
      backend_running: true,
      backend_starting: false,
      backend_url: 'http://127.0.0.1:3030',
      data_directory: '/tmp/hotel',
      version: '0.3.0',
      desktop_client_token: 'secret-desktop-tok',
    });
    (window as unknown as { __TAURI_INTERNALS__: unknown }).__TAURI_INTERNALS__ = {
      invoke,
      transformCallback: vi.fn(),
    };

    await getDesktopStatus();
    expect(getDesktopClientToken()).toBe('secret-desktop-tok');
    expect(window.sessionStorage.getItem('desktopClientToken')).toBeNull();
    expect(window.localStorage.length).toBe(0);
  });

  it('passes accessToken to restart_app IPC command', async () => {
    vi.stubEnv('VITE_APP_TARGET', 'tauri');
    const invoke = vi.fn().mockResolvedValue(undefined);
    (window as unknown as { __TAURI_INTERNALS__: unknown }).__TAURI_INTERNALS__ = {
      invoke,
      transformCallback: vi.fn(),
    };

    await restartApp();
    expect(invoke).toHaveBeenCalledWith('restart_app', { accessToken: null });
  });

  it('uses the browser origin dynamically for a production web build without an override', () => {
    vi.stubEnv('VITE_APP_TARGET', 'web');
    vi.stubEnv('VITE_API_URL', '');
    vi.stubEnv('PROD', true);
    window.history.replaceState({}, '', '/admin');

    expect(getApiBaseUrl()).toBe('');
    expect(apiUrl('auth/refresh')).toBe('/api/auth/refresh');

    const resolvedUrl = new URL(resolveApiRequestUrl('/auth/refresh'));
    expect(resolvedUrl.origin).toBe(window.location.origin);
    expect(resolvedUrl.pathname).toBe('/api/auth/refresh');
  });

  it('keeps a normalized explicit API override', () => {
    vi.stubEnv('VITE_APP_TARGET', 'web');
    vi.stubEnv('VITE_API_URL', ' https://api.example.com/// ');

    expect(getApiBaseUrl()).toBe('https://api.example.com');
    expect(apiUrl('health')).toBe('https://api.example.com/health');
  });

  it('leaves root-level gRPC service paths untouched', () => {
    vi.stubEnv('VITE_APP_TARGET', 'web');
    vi.stubEnv('VITE_API_URL', '');

    // The whole service name is the first segment — the 'hotel.' entry in
    // ROOT_API_PREFIXES must match by prefix, not exact equality.
    expect(apiUrl('hotel.rooms.v1.RoomService/ListRooms')).toBe(
      '/hotel.rooms.v1.RoomService/ListRooms',
    );
    // A bare navigation path still gets /api/ prepended.
    expect(apiUrl('bookings/123')).toBe('/api/bookings/123');
  });
});
