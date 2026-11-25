import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RoomMedia } from './RoomMedia';
import type { GuestBookingOffer } from './types';

const offer = (images: string[]) =>
  ({ room_type_name: 'Deluxe King', room_type_code: 'DLX', images }) as unknown as GuestBookingOffer;

describe('RoomMedia', () => {
  it('has no 3D toggle, only photos', () => {
    render(<RoomMedia offer={offer(['https://cdn/a.jpg'])} />);
    expect(screen.queryByRole('button', { name: /3D/i })).toBeNull();
    expect(screen.getByRole('img', { name: 'Deluxe King room' }).getAttribute('src')).toBe('https://cdn/a.jpg');
  });

  it('opens the photos full screen and steps through them', () => {
    render(<RoomMedia offer={offer(['https://cdn/a.jpg', 'https://cdn/b.jpg'])} />);
    fireEvent.click(screen.getByRole('button', { name: 'View photos full screen' }));
    const dialog = screen.getByRole('dialog');
    const image = within(dialog).getByTestId('room-lightbox-image');
    expect(image.getAttribute('src')).toBe('https://cdn/a.jpg');
    expect(within(dialog).getByText('1 / 2')).toBeTruthy();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Next photo' }));
    expect(within(dialog).getByTestId('room-lightbox-image').getAttribute('src')).toBe('https://cdn/b.jpg');

    fireEvent.keyDown(dialog, { key: 'ArrowRight' });
    expect(within(dialog).getByTestId('room-lightbox-image').getAttribute('src')).toBe('https://cdn/a.jpg');

    fireEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
  });

  it('falls back to the bundled photo and still opens it', () => {
    render(<RoomMedia offer={offer([])} />);
    fireEvent.click(screen.getByRole('button', { name: 'View photos full screen' }));
    expect(within(screen.getByRole('dialog')).getByTestId('room-lightbox-image').getAttribute('src')).toBe('/salim-inn/rooms/deluxe-king.jpg');
  });
});
