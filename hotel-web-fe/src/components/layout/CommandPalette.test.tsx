import React from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SearchGroup } from '../../api/search.service';

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  searchGroups: [] as SearchGroup[],
  searchLoading: false,
  onThemeModeChange: vi.fn(),
  themeMode: 'dark' as 'light' | 'dark',
}));

vi.mock('../../router', () => ({
  useNavigate: () => mocks.navigate,
}));

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    hasPermission: () => true,
    hasRole: () => false,
    getRoutePolicy: (routeId: string) => ({
      route_id: routeId,
      path: `/${routeId}`,
      required_permissions: ['bookings:read'],
      required_roles: [],
      excluded_roles: [],
      nav_permissions: ['bookings:read'],
      nav_roles: [],
      nav_excluded_roles: [],
      is_navigation: true,
    }),
    user: { id: 1, username: 'frontdesk', user_type: 'admin' },
  }),
}));

vi.mock('../../hooks/useGlobalSearch', () => ({
  useGlobalSearch: () => ({
    groups: mocks.searchGroups,
    loading: mocks.searchLoading,
  }),
}));

vi.mock('../../features/help/hooks/useHelpArticles', () => ({
  useHelpArticles: () => [],
}));

import { ThemeModeContext } from '../../router/ThemeModeContext';
import { CommandPaletteProvider, useCommandPalette } from './CommandPalette';

const Trigger: React.FC = () => {
  const { open } = useCommandPalette();
  return (
    <button type="button" onClick={open}>
      Open palette
    </button>
  );
};

function renderPalette() {
  return render(
    <ThemeModeContext.Provider
      value={{
        themeMode: mocks.themeMode,
        onThemeModeChange: mocks.onThemeModeChange,
      }}
    >
      <CommandPaletteProvider>
        <Trigger />
      </CommandPaletteProvider>
    </ThemeModeContext.Provider>
  );
}

describe('CommandPalette', () => {
  beforeEach(() => {
    mocks.navigate.mockReset();
    mocks.searchGroups = [];
    mocks.searchLoading = false;
    mocks.onThemeModeChange.mockReset();
    mocks.themeMode = 'dark';
    window.localStorage.clear();
  });

  afterEach(cleanup);

  it('opens with Cmd/Ctrl+K and surfaces front-desk quick actions', () => {
    renderPalette();

    fireEvent.keyDown(window, { key: 'k', ctrlKey: true });

    const input = screen.getByPlaceholderText('Search bookings, guests, rooms, pages…');
    expect(input).toBeTruthy();
    expect(screen.getByText('New booking')).toBeTruthy();
    expect(screen.getByText("Today's arrivals")).toBeTruthy();
    expect(screen.getByText("Today's departures")).toBeTruthy();
    expect(screen.getByText('Vacant rooms')).toBeTruthy();
    expect(screen.getByText('Dirty rooms')).toBeTruthy();
    expect(screen.getByText('Switch to light mode')).toBeTruthy();
  });

  it('cycles search scopes with Tab and Shift+Tab', () => {
    renderPalette();
    fireEvent.click(screen.getByRole('button', { name: 'Open palette' }));

    const input = screen.getByPlaceholderText('Search bookings, guests, rooms, pages…');
    fireEvent.keyDown(input, { key: 'Tab' });
    // After cycling from 'all' -> 'bookings', pages/actions are hidden and scope switches.
    expect(screen.queryByText("Today's arrivals")).toBeNull();

    fireEvent.keyDown(input, { key: 'Tab', shiftKey: true });
    expect(screen.getByText("Today's arrivals")).toBeTruthy();
  });

  it('deep-links room server hits to /room-management?search=<room_number>', () => {
    mocks.searchGroups = [
      {
        type: 'rooms',
        label: 'Rooms',
        results: [
          {
            id: 204,
            title: 'Room 204',
            subtitle: 'Deluxe King · available',
            route: '/room-management',
          },
        ],
      },
    ];
    renderPalette();
    fireEvent.click(screen.getByRole('button', { name: 'Open palette' }));

    const input = screen.getByPlaceholderText('Search bookings, guests, rooms, pages…');
    fireEvent.change(input, { target: { value: '204' } });

    fireEvent.click(screen.getByText('Room 204'));
    expect(mocks.navigate).toHaveBeenCalledWith('/room-management?search=204');
  });

  it('toggles theme mode via action item without persisting to recents', () => {
    renderPalette();
    fireEvent.click(screen.getByRole('button', { name: 'Open palette' }));

    fireEvent.click(screen.getByText('Switch to light mode'));
    expect(mocks.onThemeModeChange).toHaveBeenCalledWith('light');
    expect(mocks.navigate).not.toHaveBeenCalled();
    expect(window.localStorage.getItem('cmdRecents')).toBeNull();
  });
});
