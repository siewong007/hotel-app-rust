import { BookingWithDetails } from '../../../types';
import { addLocalDays, formatLocalDate, parseLocalDate } from '../../../utils/date';
import { HotelSettings } from '../../../utils/hotelSettings';
import { divideMoney, isPositiveMoney, multiplyMoney, subtractMoney, sumMoney, toMoneyNumber } from '../../../utils/money';

export interface ChargesBreakdown {
  roomCharges: number;
  roomCardDeposit: number;
  serviceTax: number;
  tourismTax: number;
  extraBedCharge: number;
  extraBedServiceTax: number;
  discountAmount: number;
  complimentaryDeduction: number;
  subtotal: number;
  depositRefund: number;
  grandTotal: number;
}

export const emptyCharges: ChargesBreakdown = {
  roomCharges: 0,
  roomCardDeposit: 50,
  serviceTax: 0,
  tourismTax: 0,
  extraBedCharge: 0,
  extraBedServiceTax: 0,
  discountAmount: 0,
  complimentaryDeduction: 0,
  subtotal: 0,
  depositRefund: 0,
  grandTotal: 0,
};

export function calculateChargesFromInputs(
  booking: BookingWithDetails,
  roomPrice: number,
  hotelSettings: HotelSettings,
  editableDailyRates: Record<string, number>
): ChargesBreakdown {
  const checkIn = parseLocalDate(booking.check_in_date);
  const checkOut = parseLocalDate(booking.check_out_date);
  const rawNights = Math.round((checkOut.getTime() - checkIn.getTime()) / (1000 * 60 * 60 * 24));
  const isHourly = booking.post_type === 'hourly' || rawNights <= 0;
  const nights = isHourly ? 1 : rawNights;

  let bookingPricePerNight = toMoneyNumber(booking.price_per_night);

  const taxRate = hotelSettings.service_tax_rate / 100;
  const taxMultiplier = 1 + taxRate;

  let taxInclusivePrice = bookingPricePerNight;
  if (!taxInclusivePrice || taxInclusivePrice === 0) {
    taxInclusivePrice = roomPrice;
  }
  if (!taxInclusivePrice || taxInclusivePrice === 0) {
    const totalAmount = toMoneyNumber(booking.total_amount);
    taxInclusivePrice = nights > 0 ? divideMoney(totalAmount, nights) : 0;
  }

  let grossRoomSubtotal: number;
  const dailyRatesMap = Object.keys(editableDailyRates).length > 0
    ? editableDailyRates
    : (booking.daily_rates && typeof booking.daily_rates === 'object' && Object.keys(booking.daily_rates).length > 0
        ? booking.daily_rates
        : null);

  if (dailyRatesMap) {
    grossRoomSubtotal = sumMoney(Object.values(dailyRatesMap));
  } else {
    grossRoomSubtotal = multiplyMoney(taxInclusivePrice, nights);
  }

  // Complimentary nights deduction: full gross room subtotal for fully_complimentary,
  // date-range sum when complimentary_start_date/end_date + daily rates are available,
  // or proportional nightly deduction otherwise.
  const complimentaryNights = Math.max(0, Number(booking.complimentary_nights ?? 0));
  let complimentaryDeduction = 0;
  if (booking.status === 'fully_complimentary' || (booking.is_complimentary && complimentaryNights >= nights)) {
    complimentaryDeduction = grossRoomSubtotal;
  } else if (complimentaryNights > 0) {
    if (dailyRatesMap && booking.complimentary_start_date && booking.complimentary_end_date) {
      const compStart = formatLocalDate(parseLocalDate(booking.complimentary_start_date));
      const compEnd = formatLocalDate(parseLocalDate(booking.complimentary_end_date));
      let rangedSum = 0;
      let matched = 0;
      for (let i = 0; i < nights; i++) {
        const key = formatLocalDate(addLocalDays(checkIn, i));
        if (key >= compStart && key < compEnd && dailyRatesMap[key] !== undefined) {
          rangedSum = sumMoney([rangedSum, dailyRatesMap[key]]);
          matched++;
        }
      }
      complimentaryDeduction = matched > 0
        ? rangedSum
        : multiplyMoney(divideMoney(grossRoomSubtotal, nights), complimentaryNights);
    } else {
      complimentaryDeduction = multiplyMoney(divideMoney(grossRoomSubtotal, nights), complimentaryNights);
    }
  }
  complimentaryDeduction = Math.min(grossRoomSubtotal, Math.max(0, complimentaryDeduction));

  const discountAmount = Math.min(
    Math.max(0, subtractMoney(grossRoomSubtotal, complimentaryDeduction)),
    Math.max(0, toMoneyNumber(booking.discount_amount)),
  );
  const roomSubtotal = Math.max(
    0,
    subtractMoney(subtractMoney(grossRoomSubtotal, complimentaryDeduction), discountAmount),
  );

  const roomCharges = divideMoney(roomSubtotal, taxMultiplier);
  const serviceTax = subtractMoney(roomSubtotal, roomCharges);

  const roomCardDeposit = booking.deposit_paid
    ? toMoneyNumber(booking.deposit_amount)
    : 0;

  const isForeignTourist = booking.guest_tourism_type === 'foreign' || booking.is_tourist === true;
  let tourismTax = 0;
  if (isForeignTourist && booking.status !== 'fully_complimentary') {
    const billableNights = Math.max(0, nights - complimentaryNights);
    tourismTax = toMoneyNumber(booking.tourism_tax_amount) > 0
      ? toMoneyNumber(booking.tourism_tax_amount)
      : multiplyMoney(hotelSettings.tourism_tax_rate, billableNights);
  }

  const extraBedChargeInclTax = toMoneyNumber(booking.extra_bed_charge);
  const extraBedCharge = isPositiveMoney(extraBedChargeInclTax) ? divideMoney(extraBedChargeInclTax, taxMultiplier) : 0;
  const extraBedServiceTax = subtractMoney(extraBedChargeInclTax, extraBedCharge);

  const subtotal = sumMoney([roomCharges, serviceTax, tourismTax, extraBedCharge, extraBedServiceTax]);
  const depositRefund = roomCardDeposit;
  const grandTotal = subtotal;

  return {
    roomCharges,
    roomCardDeposit,
    serviceTax,
    tourismTax,
    extraBedCharge,
    extraBedServiceTax,
    discountAmount,
    complimentaryDeduction,
    subtotal,
    depositRefund,
    grandTotal,
  };
}
