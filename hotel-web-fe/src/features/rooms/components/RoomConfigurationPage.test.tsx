import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  rooms: { data: [] as unknown[], isLoading: false, isPending: false, error: null as unknown },
  roomTypes: { data: [] as unknown[], isLoading: false, isPending: false, error: null as unknown },
}));

const emptyMutation = { mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false };

vi.mock('../../../auth/AuthContext', () => ({
  useAuth: () => ({ hasPermission: () => true }),
}));

vi.mock('../../../hooks/useCurrency', () => ({
  useCurrency: () => ({ format: (n: number) => `RM${n}`, symbol: 'RM', currency: 'MYR' }),
}));

vi.mock('../hooks/useRoomQueries', () => ({
  useRooms: () => mocks.rooms,
  useAllRoomTypes: () => mocks.roomTypes,
  useCreateRoom: () => emptyMutation,
  useUpdateRoom: () => emptyMutation,
  useDeleteRoom: () => emptyMutation,
  useCreateRoomType: () => emptyMutation,
  useUpdateRoomType: () => emptyMutation,
  useDeleteRoomType: () => emptyMutation,
  useUploadRoomTypeImage: () => emptyMutation,
}));

import RoomConfigurationPage from './RoomConfigurationPage';
import { expectNoCriticalAxeViolations } from '../../../test/axe';

describe('RoomConfigurationPage', () => {
  beforeEach(() => {
    mocks.rooms = { data: [], isLoading: false, isPending: false, error: null };
    mocks.roomTypes = { data: [], isLoading: false, isPending: false, error: null };
  });

  afterEach(cleanup);

  it('renders the room configuration workspace', () => {
    render(<RoomConfigurationPage />);
    expect(document.body.textContent?.length).toBeGreaterThan(0);
  });

  it('flags active room types with fewer than three photos', () => {
    const base = { base_price: 100, max_occupancy: 2, allows_extra_bed: false, max_extra_beds: 0, extra_bed_charge: 0, sort_order: 0, created_at: '', updated_at: '' };
    mocks.roomTypes = {
      data: [
        { ...base, id: 1, name: 'Deluxe', code: 'DLX', is_active: true, images: ['/a.jpg'] },
        { ...base, id: 2, name: 'Superior', code: 'SUP', is_active: true, images: ['/a.jpg', '/b.jpg', '/c.jpg'] },
        { ...base, id: 3, name: 'Old', code: 'OLD', is_active: false, images: [] },
      ],
      isLoading: false, isPending: false, error: null,
    };
    render(<RoomConfigurationPage />);
    expect(screen.getByTestId('photos-low-1').textContent).toBe('1/3 PHOTOS');
    expect(screen.queryByTestId('photos-low-2')).toBeNull();
    expect(screen.queryByTestId('photos-low-3')).toBeNull();
  });

  it('reports no critical axe violations', async () => {
    const { container } = render(<RoomConfigurationPage />);
    await expectNoCriticalAxeViolations(container);
  });
});
