import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import RoomPaymentMethodSelect from './RoomPaymentMethodSelect';

const methods = ['Cash', 'Visa Card', 'Bank Transfer', 'Paypal', 'Booking.com'];

describe('RoomPaymentMethodSelect', () => {
  afterEach(() => cleanup());

  it('presets and disables the tender on an OTA booking and shows the settled-via chip', () => {
    const onChange = vi.fn();
    render(
      <RoomPaymentMethodSelect
        booking={{ source: 'online', booking_channel_name: 'Booking.com', booking_channel_type: 'ota', booking_channel_settlement_methods: ['Booking.com'] }}
        paymentMethods={methods}
        value="Cash"
        onChange={onChange}
        label="Payment Method"
      />,
    );
    expect(onChange).toHaveBeenCalledWith('Booking.com');
    expect(screen.getByTestId('settled-via-chip').textContent).toContain('Settled via Booking.com');
    expect(screen.getByRole('combobox').getAttribute('aria-disabled')).toBe('true');
  });

  it('keeps the full list for a walk-in booking', () => {
    const onChange = vi.fn();
    render(
      <RoomPaymentMethodSelect
        booking={{ source: 'walk_in' }}
        paymentMethods={methods}
        value="Cash"
        onChange={onChange}
        label="Payment Method"
      />,
    );
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.queryByTestId('settled-via-chip')).toBeNull();
    expect(screen.getByRole('combobox').getAttribute('aria-disabled')).toBeNull();
  });

  it('warns when an online booking has no channel', () => {
    render(
      <RoomPaymentMethodSelect
        booking={{ source: 'online' }}
        paymentMethods={methods}
        value="Cash"
        onChange={vi.fn()}
        label="Payment Method"
      />,
    );
    expect(screen.getByText(/has no booking channel/)).toBeDefined();
  });

  it('offers desk tenders and the upgrade hint once the channel payout is recorded', () => {
    const onChange = vi.fn();
    render(
      <RoomPaymentMethodSelect
        booking={{ source: 'online', booking_channel_name: 'Booking.com', booking_channel_type: 'ota', booking_channel_settlement_methods: ['Booking.com'], channel_payout_recorded: true }}
        paymentMethods={methods}
        value="Cash"
        onChange={onChange}
        label="Payment Method"
      />,
    );
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole('combobox').getAttribute('aria-disabled')).toBeNull();
    expect(screen.getByTestId('desk-remainder-hint').textContent).toContain('Upgrade difference: collect at the desk');
  });

  it('has no upgrade hint before the channel payout', () => {
    render(
      <RoomPaymentMethodSelect
        booking={{ source: 'online', booking_channel_name: 'Booking.com', booking_channel_type: 'ota', booking_channel_settlement_methods: ['Booking.com'] }}
        paymentMethods={methods}
        value="Booking.com"
        onChange={vi.fn()}
        label="Payment Method"
      />,
    );
    expect(screen.queryByTestId('desk-remainder-hint')).toBeNull();
  });
});

