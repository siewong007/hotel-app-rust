/**
 * Legal identity of the data controller / service provider.
 *
 * These are the details that must appear in a PDPA s.7 notice (Personal Data
 * Protection Act 2010 [Act 709], as amended by the Personal Data Protection
 * (Amendment) Act 2024 [Act A1727]), under the Electronic Commerce Act 2006
 * [Act 658], and under the Consumer Protection (Electronic Trade Transactions)
 * Regulations 2024 [P.U. (A) 170/2024]. With one exception they are
 * deliberately NOT read from `system_settings`: the operational settings a
 * manager can edit in the admin UI must not be able to silently rewrite the
 * identity of the legal entity a guest contracted with, or the address a PDPA
 * access request has to be sent to.
 *
 * The exception is `companyRegistrationNumber`, which `getHotelLegalIdentity()`
 * resolves from the `hotel_business_number` setting. It is the one field an
 * operator has a legitimate reason to correct without a release — in Sarawak,
 * unincorporated businesses register under the Business Names Ordinance
 * (Cap. 64) and Businesses, Professions and Trades Licensing Ordinance
 * (Cap. 33) (while companies incorporate under the Companies Act 2016
 * [Act 777] via SSM), and a reissued number is a disclosure obligation the
 * hotel must be able to update promptly. The constant below stays the
 * compiled-in fallback, so a blank or unreachable setting degrades to the real
 * number rather than to nothing.
 */
import { getHotelSettings } from '../../../utils/hotelSettings';

export interface HotelLegalIdentity {
  tradingName: string;
  registeredName: string;
  companyRegistrationNumber: string;
  addressLines: readonly string[];
  email: string;
  phone: string;
  dataProtectionContactEmail: string;
  receptionHours: string;
}

export const HOTEL_LEGAL_IDENTITY: HotelLegalIdentity = {
  tradingName: 'Salim Inn',
  /** Registered business name under Sarawak Business Names Ordinance (Cap. 64) / trade licence. */
  registeredName: 'Salim Inn',
  /**
   * Fallback only. The live value is the `hotel_business_number` setting —
   * read it through `getHotelLegalIdentity()`, never from this constant, so an
   * operator's correction is what a guest actually sees.
   */
  companyRegistrationNumber: 'SA2012724',
  addressLines: [
    'Lot 21-22, Lorong Salim 17',
    'Jalan Salim, Farley Commercial Centre',
    '96000 Sibu, Sarawak',
    'Malaysia',
  ],
  email: 'saliminnsibu@gmail.com',
  phone: '+60 11-1050 7083',
  /**
   * Personal Data Protection Act 2010 s.12A (inserted by the Personal Data
   * Protection (Amendment) Act 2024, effective 1 June 2025) requires a
   * published data protection contact / Data Protection Officer channel where
   * thresholds apply. Inquiries, access/correction/portability requests, and
   * breach/privacy notices route to this contact point until a dedicated DPO
   * mailbox is provisioned.
   */
  dataProtectionContactEmail: 'saliminnsibu@gmail.com',
  receptionHours: '24 hours',
} as const;

export const HOTEL_ADDRESS_ONE_LINE = HOTEL_LEGAL_IDENTITY.addressLines.join(', ');

/**
 * The legal identity with the configured business registration number applied.
 *
 * `getHotelSettings()` is a synchronous localStorage read, refreshed at boot by
 * `applyPublicHotelSettings()` from the unauthenticated `settings/public`
 * endpoint — which is why `hotel_business_number` is seeded `is_public`. A
 * browser that has never reached the backend, or a setting saved blank, falls
 * back to the constant above.
 */
export function getHotelLegalIdentity(): HotelLegalIdentity {
  const configured = getHotelSettings().hotel_business_number?.trim();
  if (!configured) return HOTEL_LEGAL_IDENTITY;
  return { ...HOTEL_LEGAL_IDENTITY, companyRegistrationNumber: configured };
}
