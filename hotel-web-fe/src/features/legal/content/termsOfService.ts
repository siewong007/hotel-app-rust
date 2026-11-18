import { HOTEL_LEGAL_IDENTITY, type HotelLegalIdentity } from './hotelIdentity';
import { getHotelSettings } from '../../../utils/hotelSettings';
import type { LegalDocument, LegalLocale } from './types';

/**
 * Booking terms and conditions.
 *
 * The commercial stay rules here (full-day rate after 3:00 pm, free
 * cancellation at least three days before arrival, first night charged on late
 * cancellation or no-show, confirmation on full payment) match the published
 * Salim Inn policy and FAQ so a guest is never shown two different contracts
 * for the same stay. Statutory disclosures cover:
 *  - Electronic Commerce Act 2006 (Act 658) & Consumer Protection (Electronic
 *    Trade Transactions) Regulations 2024 [P.U. (A) 170/2024]
 *  - Consumer Protection Act 1999 (Act 599) (including Parts IIIA, IX and XII)
 *  - Tourism Tax Act 2017 (Act 791), Service Tax Act 2018 (Act 807) & Income
 *    Tax Act 1967 (Act 53) (LHDN e-Invoicing)
 *  - Registration of Guests Act 1965 (Act 381) & Tourism Industry Act 1992
 *    (Act 482)
 *  - Control of Smoking Products for Public Health Act 2024 (Act 852)
 *  - Applicable laws of Malaysia and Sarawak (note: the Innkeepers Act 1952
 *    [Act 248] applies to Peninsular Malaysia only and is intentionally not
 *    cited for a Sarawak property)
 *
 * Check-in and check-out times resolve dynamically from the public
 * `check_in_time` / `check_out_time` settings so the contract tracks the times
 * the hotel actually operates.
 */
export const TERMS_OF_SERVICE_VERSION = '2026-11-18';

/**
 * "15:00" → "3:00 pm" / "3:00 petang". A malformed setting is shown verbatim
 * (the operator's typo is still the true value); an absent one falls back to
 * the seeded defaults so the page never renders a blank contract clause.
 */
const formatStayTime = (
  raw: string | undefined,
  fallback: '15:00' | '11:00',
  locale: LegalLocale
): string => {
  const value = raw?.trim() || fallback;
  const match = /^(\d{1,2}):(\d{2})/.exec(value);
  const h = match ? parseInt(match[1], 10) : NaN;
  if (!match || h > 23) return value;
  const h12 = h % 12 === 0 ? 12 : h % 12;
  if (locale === 'ms') {
    const period =
      h === 0 ? 'tengah malam' : h < 12 ? 'pagi' : h === 12 ? 'tengah hari' : h < 19 ? 'petang' : 'malam';
    return `${h12}:${match[2]} ${period}`;
  }
  if (locale === 'zh' || locale === 'zh-TW') {
    return `${h < 12 ? '上午' : '下午'} ${h12}:${match[2]}`;
  }
  return `${h12}:${match[2]} ${h < 12 ? 'am' : 'pm'}`;
};

/**
 * Builds the terms against a resolved identity.
 *
 * The business registration number comes from the `hotel_business_number`
 * setting so an operator can correct it without a release, and check-in/out
 * times come from the public operational settings for the reason given above.
 * `version` is not a function of either — a consent record pins the wording
 * generation, not the values that generation happened to disclose.
 */
export const buildTermsOfService = (
  identity: HotelLegalIdentity = HOTEL_LEGAL_IDENTITY
): LegalDocument => {
  const addressOneLine = identity.addressLines.join(', ');
  const settings = getHotelSettings();
  const checkIn = (locale: LegalLocale) => formatStayTime(settings.check_in_time, '15:00', locale);
  const checkOut = (locale: LegalLocale) => formatStayTime(settings.check_out_time, '11:00', locale);
  return {
    id: 'terms_of_service',
    version: TERMS_OF_SERVICE_VERSION,
    effectiveDate: '2026-11-18',
    title: {
      en: 'Booking Terms and Conditions',
      ms: 'Terma dan Syarat Tempahan',
      zh: '预订条款与条件',
      'zh-TW': '預訂條款與條件',
    },
    summary: {
      en: `These terms govern a reservation you make with ${identity.tradingName}. They set out our statutory supplier disclosures, how a booking becomes confirmed, applicable rates and Malaysian taxes, cancellation and refund rules, statutory guest-registration and smoke-free house rules, and your consumer rights. Please read them carefully before confirming a booking.`,
      ms: `Terma ini mentadbir tempahan yang anda buat dengan ${identity.tradingName}. Ia menetapkan pendedahan pembekal berkanun kami, cara tempahan disahkan, kadar dan cukai Malaysia yang terpakai, peraturan pembatalan dan bayaran balik, peraturan pendaftaran tetamu berkanun serta penginapan bebas asap rokok, dan hak pengguna anda. Sila baca dengan teliti sebelum mengesahkan tempahan.`,
      zh: `本条款约束您向${identity.tradingName}作出的预订，载明我们的法定经营者披露事项、预订确认方式、适用房价与马来西亚税费、取消与退款规则、法定住客登记与无烟住宿规定，以及您的消费者权利。请在确认预订前仔细阅读。`,
      'zh-TW': `本條款約束您向${identity.tradingName}作出的預訂，載明我們的法定經營者披露事項、預訂確認方式、適用房價與馬來西亞稅費、取消與退款規則、法定住客登記與無煙住宿規定，以及您的消費者權利。請在確認預訂前仔細閱讀。`,
    },
    sections: [
      {
        id: 'parties',
        heading: {
          en: '1. Supplier disclosure and contracting parties',
          ms: '1. Pendedahan pembekal dan pihak berkontrak',
          zh: '1. 经营者披露与缔约方',
          'zh-TW': '1. 經營者披露與締約方',
        },
        body: [
          {
            en: `These terms form a legally binding agreement under the Contracts Act 1950 (Act 136) and the Electronic Commerce Act 2006 (Act 658) between you (the guest making the booking) and ${identity.registeredName} (business registration number ${identity.companyRegistrationNumber}), operating at ${addressOneLine}, email ${identity.email}, telephone ${identity.phone} ("the Hotel", "we", "us"). These details are provided in accordance with the Consumer Protection (Electronic Trade Transactions) Regulations 2024 [P.U. (A) 170/2024].`,
            ms: `Terma ini membentuk perjanjian yang mengikat di sisi undang-undang di bawah Akta Kontrak 1950 (Akta 136) dan Akta Perdagangan Elektronik 2006 (Akta 658) antara anda (tetamu yang membuat tempahan) dengan ${identity.registeredName} (nombor pendaftaran perniagaan ${identity.companyRegistrationNumber}), beroperasi di ${addressOneLine}, e-mel ${identity.email}, telefon ${identity.phone} ("Hotel", "kami"). Butiran ini diberikan selaras dengan Peraturan-Peraturan Perlindungan Pengguna (Urus Niaga Perdagangan Elektronik) 2024 [P.U. (A) 170/2024].`,
            zh: `本条款依据《1950年合同法令》（第136号法令）及《2006年电子商务法令》（第658号法令），构成您（作出预订的宾客）与${identity.registeredName}（商业登记号码：${identity.companyRegistrationNumber}，营业地址：${addressOneLine}，电子邮箱：${identity.email}，电话：${identity.phone}，下称“酒店”或“我们”）之间具法律约束力的协议。上述资料系依据《2024年消费者保护（电子贸易交易）条例》［P.U. (A) 170/2024］作出披露。`,
            'zh-TW': `本條款依據《1950年合同法令》（第136號法令）及《2006年電子商務法令》（第658號法令），構成您（作出預訂的賓客）與${identity.registeredName}（商業登記號碼：${identity.companyRegistrationNumber}，營業地址：${addressOneLine}，電子郵箱：${identity.email}，電話：${identity.phone}，下稱「酒店」或「我們」）之間具法律約束力的協議。上述資料係依據《2024年消費者保護（電子貿易交易）條例》［P.U. (A) 170/2024］作出披露。`,
          },
          {
            en: 'They apply to bookings you make online through our website, the guest portal, or a booking link we send you, whether or not you hold a guest account. Before submitting an online reservation, our review step lets you check and correct any input errors (such as dates, room type, occupancy, or contact details). Bookings made at reception follow the same stay rules.',
            ms: 'Terma ini terpakai kepada tempahan yang anda buat dalam talian melalui laman web kami, portal tetamu, atau pautan tempahan yang kami hantar, sama ada anda mempunyai akaun tetamu atau tidak. Sebelum menghantar tempahan dalam talian, langkah semakan kami membolehkan anda menyemak dan membetulkan sebarang kesilapan input (seperti tarikh, jenis bilik, bilangan tetamu, atau butiran hubungan). Tempahan di kaunter penyambut tetamu mengikut peraturan penginapan yang sama.',
            zh: '本条款适用于您通过我们的网站、宾客门户或我们发送的预订链接在线作出的预订，无论您是否持有宾客账户。在提交在线预订前，系统核对步骤可供您检查并更正任何输入错误（如日期、房型、入住人数或联络资料）。在前台办理的预订适用相同的住宿规定。',
            'zh-TW': '本條款適用於您透過我們的網站、賓客門戶或我們發送的預訂連結線上作出的預訂，無論您是否持有賓客帳戶。在提交線上預訂前，系統核對步驟可供您檢查並更正任何輸入錯誤（如日期、房型、入住人數或聯絡資料）。在前台辦理的預訂適用相同的住宿規定。',
          },
          {
            en: 'By submitting a booking you confirm that you are at least 18 years old and legally able to enter into this agreement, that any accompanying minor under 18 is under your parental or guardian responsibility, and that the information you have provided is true, accurate, and complete.',
            ms: 'Dengan menghantar tempahan, anda mengesahkan bahawa anda berumur sekurang-kurangnya 18 tahun dan berkelayakan di sisi undang-undang untuk memasuki perjanjian ini, bahawa mana-mana kanak-kanak bawah umur 18 tahun yang mengiringi berada di bawah tanggungjawab ibu bapa atau penjaga anda, dan bahawa maklumat yang anda berikan adalah benar, tepat dan lengkap.',
            zh: '提交预订即表示您确认自己年满18周岁且具有订立本协议的法律行为能力，随行的任何未满18周岁未成年人由您承担父母或监护人责任，且您提供的资料真实、准确、完整。',
            'zh-TW': '提交預訂即表示您確認自己年滿18週歲且具有訂立本協議的法律行為能力，隨行的任何未滿18週歲未成年人由您承擔父母或監護人責任，且您提供的資料真實、準確、完整。',
          },
        ],
      },
      {
        id: 'booking-confirmation',
        heading: {
          en: '2. How a booking is acknowledged and confirmed',
          ms: '2. Cara tempahan diakui terima dan disahkan',
          zh: '2. 预订的受理回执与确认方式',
          'zh-TW': '2. 預訂的受理回執與確認方式',
        },
        emphasis: 'requirement',
        body: [
          {
            en: 'When you submit an online reservation, we acknowledge receipt electronically right away on screen and by email with your booking reference. However, a reservation remains a provisional request until we confirm it: a booking is confirmed only after full payment has been received and verified against your reservation. Until then, the room is held provisionally and is not guaranteed.',
            ms: 'Apabila anda menghantar tempahan dalam talian, kami mengakui penerimaan secara elektronik dengan serta-merta pada skrin dan melalui e-mel berserta nombor rujukan tempahan anda. Walau bagaimanapun, tempahan kekal sebagai permohonan sementara sehingga kami mengesahkannya: tempahan hanya disahkan selepas bayaran penuh diterima dan disahkan terhadap tempahan anda. Sehingga itu, bilik hanya ditahan sementara dan tidak dijamin.',
            zh: '当您提交在线预订后，系统会立即在页面及通过电子邮件向您发出载有预订编号的电子受理回执。但在我们正式确认前，预订仍属临时申请：只有在收到全额款项并与您的预订核对无误后，预订方获确认。在此之前，客房仅作临时保留而非保证。',
            'zh-TW': '當您提交線上預訂後，系統會立即在頁面及透過電子郵件向您發出載有預訂編號的電子受理回執。但在我們正式確認前，預訂仍屬臨時申請：只有在收到全額款項並與您的預訂核對無誤後，預訂方獲確認。在此之前，客房僅作臨時保留而非保證。',
          },
          {
            en: 'An unpaid online booking may be released automatically once its holding period expires, returning the room to public sale, and prevailing rates may change if you rebook later. Please ensure your email address is accurate so our electronic acknowledgements, receipts, and confirmation reach you.',
            ms: 'Tempahan dalam talian yang belum dibayar boleh dilepaskan secara automatik sebaik sahaja tempoh tahanannya tamat, mengembalikan bilik untuk jualan umum, dan kadar semasa boleh berubah jika anda menempah semula kemudian. Sila pastikan alamat e-mel anda tepat supaya pengakuan elektronik, resit dan pengesahan kami sampai kepada anda.',
            zh: '未付款的在线预订在保留期限届满后可能自动释放，客房将恢复公开发售；若您日后重新预订，适用房价可能发生变动。请确保您填写的电子邮箱准确无误，以便接收我们的电子回执、收据及确认函。',
            'zh-TW': '未付款的線上預訂在保留期限屆滿後可能自動釋出，客房將恢復公開發售；若您日後重新預訂，適用房價可能發生變動。請確保您填寫的電子郵箱準確無誤，以便接收我們的電子回執、收據及確認函。',
          },
        ],
      },
      {
        id: 'rates-and-taxes',
        heading: {
          en: '3. Rates, taxes, e-Invoicing and charges',
          ms: '3. Kadar, cukai, e-Invois dan caj',
          zh: '3. 房价、税费、电子发票与杂费',
          'zh-TW': '3. 房價、稅費、電子發票與雜費',
        },
        body: [
          {
            en: 'All rates are quoted in Malaysian Ringgit (MYR / RM) unless stated otherwise. In accordance with Malaysian price-marking and e-commerce regulations, the total presented before checkout itemises the room rate for each night alongside any applicable taxes, statutory levies, and optional extras (such as an extra bed) before you commit to pay.',
            ms: 'Semua kadar dinyatakan dalam Ringgit Malaysia (MYR / RM) melainkan dinyatakan sebaliknya. Selaras dengan peraturan penandaan harga dan e-dagang Malaysia, jumlah yang dipaparkan sebelum pembayaran memperincikan kadar bilik bagi setiap malam berserta sebarang cukai, levi berkanun dan tambahan pilihan (seperti katil tambahan) sebelum anda membuat komitmen bayaran.',
            zh: '除另有注明外，所有房价均以马来西亚林吉特（MYR / RM）计价。依据马来西亚价格标示及电子商务法规，结账前显示的总额会在您确认付款前逐项列明每晚房费、适用税费、法定征费及可选附加项目（如加床费）。',
            'zh-TW': '除另有註明外，所有房價均以馬來西亞令吉（MYR / RM）計價。依據馬來西亞價格標示及電子商務法規，結帳前顯示的總額會在您確認付款前逐項列明每晚房費、適用稅費、法定徵費及可選附加項目（如加床費）。',
          },
        ],
        bullets: [
          {
            en: 'Service Tax (SST) — tax-inclusive pricing: In accordance with the Price Control and Anti-Profiteering (Price Marking for Goods and Charges for Services) Order 2020 and the Service Tax Act 2018 (Act 807) (First Schedule, Group A — Accommodation), published room and extra-bed rates already include applicable Service Tax, and the included Service Tax component is itemised separately on your checkout invoice.',
            ms: 'Cukai Perkhidmatan (SST) — harga termasuk cukai: Selaras dengan Perintah Kawalan Harga dan Antipencatutan (Penandaan Harga bagi Barangan dan Caj bagi Perkhidmatan) 2020 serta Akta Cukai Perkhidmatan 2018 (Akta 807) (Jadual Pertama, Kumpulan A — Penginapan), kadar bilik dan katil tambahan yang diterbitkan telah pun merangkumi Cukai Perkhidmatan yang terpakai, dan komponen Cukai Perkhidmatan tersebut diperincikan secara berasingan pada invois daftar keluar anda.',
            zh: '服务税（SST）——含税标价：依据《2020年价格管制及反暴利（商品标价与服务收费标示）令》及《2018年服务税法令》（第807号法令，第一附表A组——住宿服务），公布的房价及加床费均已包含适用的服务税，所含服务税税额将在退房发票上单独列明。',
            'zh-TW': '服務稅（SST）——含稅標價：依據《2020年價格管制及反暴利（商品標價與服務收費標示）令》及《2018年服務稅法令》（第807號法令，第一附表A組——住宿服務），公佈的房價及加床費均已包含適用的服務稅，所含服務稅稅額將在退房發票上單獨列明。',
          },
          {
            en: 'Tourism Tax (TTx): Under the Tourism Tax Act 2017 (Act 791), a flat Tourism Tax of RM10.00 per room per night (minimum one night, including same-day stays) is added for foreign tourists (guests who are neither Malaysian citizens holding a MyKad nor Malaysia Permanent Residents holding a MyPR). You declare your guest type when booking, and we verify nationality and residency against physical identification at check-in; any difference is collected or refunded on arrival. Where a booking was made through a registered Digital Platform Service Provider (OTA) that has already collected Tourism Tax under the Act, please present proof of collection so it is not charged twice, while any unpaid desk charges (such as Tourism Tax, extra beds, or room upgrades) remain payable at reception.',
            ms: 'Cukai Pelancongan (TTx): Di bawah Akta Cukai Pelancongan 2017 (Akta 791), Cukai Pelancongan kadar rata RM10.00 bagi setiap bilik setiap malam (minimum satu malam, termasuk penginapan hari sama) ditambah bagi pelancong asing (tetamu yang bukan warganegara Malaysia pemegang MyKad dan bukan Pemastautin Tetap Malaysia pemegang MyPR). Anda mengisytiharkan jenis tetamu semasa menempah, dan kami mengesahkan kewarganegaraan serta taraf pemastautin berdasarkan dokumen pengenalan fizikal semasa daftar masuk; sebarang perbezaan akan dikutip atau dipulangkan semasa ketibaan. Jika tempahan dibuat melalui Penyedia Perkhidmatan Platform Digital (OTA) berdaftar yang telah mengutip Cukai Pelancongan di bawah Akta tersebut, sila kemukakan bukti kutipan supaya ia tidak dicaj dua kali, manakala sebarang caj kaunter yang belum dijelaskan (seperti Cukai Pelancongan, katil tambahan atau naik taraf bilik) perlu dibayar di kaunter penyambut tetamu.',
            zh: '旅游税（TTx）：依《2017年旅游税法令》（第791号法令），非马来西亚公民（非MyKad持有人）且非马来西亚永久居民（非MyPR持有人）的外国旅客须另行按每房每晚RM10.00的固定标准缴纳旅游税（最少按一晚计征，含当日入住退房）。预订时请如实申报旅客类型，我们将在入住时核对身份证件原件；如有差额将于到店时补收或退还。若您通过已依法代收旅游税的注册数字平台服务商（OTA）预订，请出示代收凭证以免重复征收；任何尚未结清的前台费用（如旅游税、加床费或房型升级差价）仍须在前台缴付。',
            'zh-TW': '旅遊稅（TTx）：依《2017年旅遊稅法令》（第791號法令），非馬來西亞公民（非MyKad持有人）且非馬來西亞永久居民（非MyPR持有人）的外國旅客須另行按每房每晚RM10.00的固定標準繳納旅遊稅（最少按一晚計徵，含當日入住退房）。預訂時請如實申報旅客類型，我們將在入住時核對身分證件原件；如有差額將於到店時補收或退還。若您透過已依法代收旅遊稅的註冊數位平台服務商（OTA）預訂，請出示代收憑證以免重複徵收；任何尚未結清的前台費用（如旅遊稅、加床費或房型升級差價）仍須在前台繳付。',
          },
          {
            en: 'Receipts and LHDN e-Invoicing: Official receipts and invoices are issued for every settled stay. Where you or your company require a validated e-Invoice under the Income Tax Act 1967 (Act 53) and Inland Revenue Board of Malaysia (LHDN) guidelines (subject to applicable statutory mandates and exemptions), you must provide accurate taxpayer particulars (Tax Identification Number, NRIC or business registration number, and billing address) at payment or check-out within the applicable billing period.',
            ms: 'Resit dan e-Invois LHDN: Resit dan invois rasmi dikeluarkan bagi setiap penginapan yang dijelaskan. Sekiranya anda atau syarikat anda memerlukan e-Invois yang disahkan di bawah Akta Cukai Pendapatan 1967 (Akta 53) dan garis panduan Lembaga Hasil Dalam Negeri Malaysia (LHDN) (tertakluk kepada mandat dan pengecualian berkanun yang terpakai), anda hendaklah memberikan butiran pembayar cukai yang tepat (Nombor Pengenalan Cukai/TIN, NRIC atau nombor pendaftaran perniagaan, serta alamat pengebilan) semasa pembayaran atau daftar keluar dalam tempoh pengebilan yang ditetapkan.',
            zh: '收据与内陆税收局（LHDN）电子发票：每次结清的住宿均会开具正式收据或发票。如您或贵公司需依据《1967年所得税法令》（第53号法令）及马来西亚内陆税收局（LHDN）指南开具经验证的电子发票（视现行法定适用范围及豁免规定而定），须于付款或退房时在规定开票期限内提供准确的纳税人资料（税务识别号TIN、身份证号或商业登记号及账单地址）。',
            'zh-TW': '收據與內陸稅收局（LHDN）電子發票：每次結清的住宿均會開具正式收據或發票。如您或貴公司需依據《1967年所得稅法令》（第53號法令）及馬來西亞內陸稅收局（LHDN）指南開具經驗證的電子發票（視現行法定適用範圍及豁免規定而定），須於付款或退房時在規定開票期限內提供準確的納稅人資料（稅務識別號TIN、身分證號或商業登記號及帳單地址）。',
          },
          {
            en: 'Keycard deposit and incidentals: A refundable keycard or security deposit may be collected at check-in and returned at check-out once the keycard is returned and the room is inspected. Incidental charges (such as extra beds, late check-out, lost keycards, deep cleaning, or damage beyond fair wear and tear) are payable on departure.',
            ms: 'Deposit kad kunci dan caj sampingan: Deposit kad kunci atau keselamatan yang boleh dikembalikan mungkin dikutip semasa daftar masuk dan dipulangkan semasa daftar keluar setelah kad kunci dipulangkan dan bilik diperiksa. Caj sampingan (seperti katil tambahan, daftar keluar lewat, kehilangan kad kunci, pembersihan rapi, atau kerosakan melebihi haus dan lusuh munasabah) perlu dibayar semasa daftar keluar.',
            zh: '房卡押金与杂项费用：办理入住时可能收取可退还的房卡或住宿押金，退房归还房卡且客房经查验无异常后予以退还。住宿期间产生的杂项费用（如加床、延迟退房、遗失房卡、深度清洁或超出正常损耗的损坏赔偿）须于离店时结清。',
            'zh-TW': '房卡押金與雜項費用：辦理入住時可能收取可退還的房卡或住宿押金，退房歸還房卡且客房經查驗無異常後予以退還。住宿期間產生的雜項費用（如加床、延遲退房、遺失房卡、深度清潔或超出正常損耗的損壞賠償）須於離店時結清。',
          },
        ],
      },
      {
        id: 'cancellation',
        heading: {
          en: '4. Cancellation, changes and no-shows',
          ms: '4. Pembatalan, perubahan dan ketidakhadiran',
          zh: '4. 取消、更改与未入住',
          'zh-TW': '4. 取消、更改與未入住',
        },
        emphasis: 'requirement',
        body: [
          {
            en: 'You can cancel an unpaid booking at any time through the guest portal, and the cancellation takes effect immediately. Online self-cancellation may be temporarily disabled for operational reasons; if so, the portal will let you know and you can contact reception at any hour.',
            ms: 'Anda boleh membatalkan tempahan yang belum dibayar pada bila-bila masa melalui portal tetamu, dan pembatalan berkuat kuasa serta-merta. Pembatalan kendiri dalam talian mungkin digantung buat sementara atas sebab operasi; jika demikian, portal akan memaklumkannya dan anda boleh menghubungi kaunter penyambut tetamu pada bila-bila masa.',
            zh: '您可随时通过宾客门户取消尚未付款的预订，取消即时生效。在线自助取消功能可能因运营原因暂时停用；届时门户会予以提示，您可随时联系24小时前台办理。',
            'zh-TW': '您可隨時透過賓客門戶取消尚未付款的預訂，取消即時生效。線上自助取消功能可能因營運原因暫時停用；屆時門戶會予以提示，您可隨時聯絡24小時前台辦理。',
          },
          {
            en: 'A booking that has already been paid for cannot be cancelled automatically online. You may submit a cancellation request through the guest portal or by contacting us directly — your booking remains active while our team reviews the request against these terms, and we will email you the outcome.',
            ms: 'Tempahan yang telah dibayar tidak boleh dibatalkan secara automatik dalam talian. Anda boleh menghantar permohonan pembatalan melalui portal tetamu atau menghubungi kami terus — tempahan anda kekal aktif semasa pasukan kami menyemak permohonan tersebut mengikut terma ini, dan kami akan memaklumkan keputusannya melalui e-mel.',
            zh: '已付款的预订无法在线自动取消。您可通过宾客门户或直接联系我们提交取消申请——在团队依据本条款审核期间，您的预订保持有效，审核结果将通过电子邮件通知您。',
            'zh-TW': '已付款的預訂無法線上自動取消。您可透過賓客門戶或直接聯絡我們提交取消申請——在團隊依據本條款審核期間，您的預訂保持有效，審核結果將透過電子郵件通知您。',
          },
          {
            en: 'Where we receive your cancellation notice at least three (3) calendar days before your scheduled arrival date, we refund the room payment in full. Where less than three (3) days’ notice is given, or where you do not arrive on your check-in date without notice (a no-show), we may retain or charge the first night’s room rate (plus applicable tax for that night) and refund any remaining nights paid.',
            ms: 'Sekiranya kami menerima notis pembatalan anda sekurang-kurangnya tiga (3) hari kalendar sebelum tarikh ketibaan yang dijadualkan, kami memulangkan bayaran bilik sepenuhnya. Sekiranya notis kurang daripada tiga (3) hari diberikan, atau anda tidak hadir pada tarikh daftar masuk tanpa makluman (no-show), kami boleh mengekalkan atau mengenakan caj kadar bilik malam pertama (berserta cukai berkaitan bagi malam tersebut) dan memulangkan baki malam selebihnya yang telah dibayar.',
            zh: '如我们在您预定抵达日期前至少三（3）个日历日收到取消通知，我们将全额退还已付房费。如通知不足三（3）日，或您于入住当日未到店且未提前通知（未入住 / no-show），我们可扣除或收取首晚房费（含该晚适用税费），并退还其余已付房晚的款项。',
            'zh-TW': '如我們在您預定抵達日期前至少三（3）個日曆日收到取消通知，我們將全額退還已付房費。如通知不足三（3）日，或您於入住當日未到店且未提前通知（未入住 / no-show），我們可扣除或收取首晚房費（含該晚適用稅費），並退還其餘已付房晚的款項。',
          },
          {
            en: 'Promotional rates or vouchers explicitly marked as non-cancellable or non-refundable at the time of booking cannot be cancelled for a cash refund. Date-change requests are subject to room availability and to the rate applicable on the new dates.',
            ms: 'Kadar promosi atau baucar yang dinyatakan secara jelas sebagai tidak boleh dibatalkan atau tidak boleh dikembalikan bayaran semasa tempahan tidak layak mendapat bayaran balik tunai. Permohonan menukar tarikh tertakluk kepada kekosongan bilik dan kadar yang terpakai pada tarikh baharu.',
            zh: '预订时明确标明不可取消或不可退款的促销房价或礼券，取消后不予现金退款。更改入住日期的申请须视客房供应情况而定，并按新日期适用的房价执行。',
            'zh-TW': '預訂時明確標明不可取消或不可退款的促銷房價或禮券，取消後不予現金退款。更改入住日期的申請須視客房供應情況而定，並按新日期適用的房價執行。',
          },
          {
            en: 'Nothing in this clause limits your statutory rights under the Consumer Protection Act 1999 (Act 599), including Part IIIA (unfair contract terms) and Part IX (guarantees in respect of the supply of services), where accommodation is not provided in accordance with the contract.',
            ms: 'Tiada apa-apa dalam fasal ini menghadkan hak berkanun anda di bawah Akta Perlindungan Pengguna 1999 (Akta 599), termasuk Bahagian IIIA (terma kontrak tidak adil) dan Bahagian IX (gerenti berkenaan pembekalan perkhidmatan), sekiranya penginapan tidak disediakan mengikut kontrak.',
            zh: '若住宿服务未按约定提供，本条任何内容均不限制您依《1999年消费者保护法令》（第599号法令，包括第IIIA部不公平合同条款及第IX部服务供应保证）享有的法定权利。',
            'zh-TW': '若住宿服務未按約定提供，本條任何內容均不限制您依《1999年消費者保護法令》（第599號法令，包括第IIIA部不公平合同條款及第IX部服務供應保證）享有的法定權利。',
          },
        ],
      },
      {
        id: 'stay-rules',
        heading: {
          en: '5. Check-in, statutory guest registration, smoke-free policy and house rules',
          ms: '5. Daftar masuk, pendaftaran tetamu berkanun, dasar bebas asap rokok dan peraturan penginapan',
          zh: '5. 入住与退房、法定住客登记、禁烟规定及住宿守则',
          'zh-TW': '5. 入住與退房、法定住客登記、禁煙規定及住宿守則',
        },
        bullets: [
          {
            en: `Check-in begins at ${checkIn('en')}, and check-out is by ${checkOut('en')}, as displayed on your booking confirmation. Late check-out is subject to availability and extra charges; a full-day room rate may apply after 3:00 pm.`,
            ms: `Daftar masuk bermula pada ${checkIn('ms')}, dan daftar keluar sebelum ${checkOut('ms')}, seperti yang dinyatakan pada pengesahan tempahan anda. Daftar keluar lewat tertakluk kepada kekosongan dan caj tambahan; kadar bilik sehari penuh boleh dikenakan selepas 3:00 petang.`,
            zh: `入住自${checkIn('zh')}开始，退房截止时间为${checkOut('zh')}，并以您的预订确认函所载为准。延迟退房须视房态而定并可能加收费用；下午3:00后退房可能按全日房价计收。`,
            'zh-TW': `入住自${checkIn('zh-TW')}開始，退房截止時間為${checkOut('zh-TW')}，並以您的預訂確認函所載為準。延遲退房須視房態而定並可能加收費用；下午3:00後退房可能按全日房價計收。`,
          },
          {
            en: 'Statutory guest register: Under the Registration of Guests Act 1965 (Act 381) (applicable throughout Malaysia, including Sarawak) and the Tourism Industry Act 1992 (Act 482), every person staying overnight must be registered at reception and present a valid government-issued identity card (MyKad/MyPR) or passport upon arrival. Guests under 18 years of age may only check in when accompanied by a registered parent or adult legal guardian.',
            ms: 'Daftar tetamu berkanun: Di bawah Akta Pendaftaran Tetamu 1965 (Akta 381) (terpakai di seluruh Malaysia termasuk Sarawak) dan Akta Industri Pelancongan 1992 (Akta 482), setiap individu yang bermalam wajib didaftarkan di kaunter penyambut tetamu dan mengemukakan kad pengenalan rasmi kerajaan yang sah (MyKad/MyPR) atau pasport semasa ketibaan. Tetamu berumur di bawah 18 tahun hanya dibenarkan mendaftar masuk apabila ditemani oleh ibu bapa atau penjaga sah dewasa yang berdaftar.',
            zh: '法定住客登记：依据适用于全马来西亚（含砂拉越）的《1965年住客登记法令》（第381号法令）及《1992年旅游业法令》（第482号法令），所有留宿人员均须在前台登记，并在抵达时出示有效的政府签发身份证件（MyKad/MyPR）或护照原件。未满18周岁的宾客须由已登记的父母或成年法定监护人陪同方可办理入住。',
            'zh-TW': '法定住客登記：依據適用於全馬來西亞（含砂拉越）的《1965年住客登記法令》（第381號法令）及《1992年旅遊業法令》（第482號法令），所有留宿人員均須在前台登記，並在抵達時出示有效的政府簽發身分證件（MyKad/MyPR）或護照原件。未滿18週歲的賓客須由已登記的父母或成年法定監護人陪同方可辦理入住。',
          },
          {
            en: 'Smoke-free premises (Act 852): In compliance with the Control of Smoking Products for Public Health Act 2024 (Act 852) and hotel policy, smoking and vaping (including cigarettes, cigars, e-cigarettes, and heated tobacco products) are strictly prohibited in all indoor public areas (lobby, reception, corridors, stairways) and in non-smoking guest rooms. Breaching this rule exposes offenders to statutory fines and a room deep-cleaning and deodorising recovery charge.',
            ms: 'Premis bebas asap rokok (Akta 852): Mematuhi Akta Kawalan Produk Merokok Demi Kesihatan Awam 2024 (Akta 852) dan dasar hotel, perbuatan merokok dan menghisap vape (termasuk rokok, cerut, rokok elektronik dan produk tembakau dipanaskan) adalah dilarang sama sekali di semua kawasan awam tertutup (lobi, kaunter penyambut tetamu, koridor, tangga) serta di dalam bilik tetamu bebas rokok. Pelanggaran peraturan ini boleh mendedahkan pesalah kepada denda berkanun serta caj pemulihan pembersihan rapi dan penyahbauan bilik.',
            zh: '禁烟规定（第852号法令）：依据《2024年公共卫生烟草与吸烟产品管制法令》（第852号法令）及本酒店规定，所有室内公共区域（大堂、接待处、走廊、楼梯间）及无烟客房内严禁吸烟及使用电子烟（含传统卷烟、雪茄、电子雾化器及加热烟草制品）。违反者除可能面临法定罚款外，还须承担客房深度清洁与除味恢复费用。',
            'zh-TW': '禁煙規定（第852號法令）：依據《2024年公共衛生煙草與吸煙產品管制法令》（第852號法令）及本酒店規定，所有室內公共區域（大堂、接待處、走廊、樓梯間）及無煙客房內嚴禁吸煙及使用電子煙（含傳統捲煙、雪茄、電子霧化器及加熱煙草製品）。違反者除可能面臨法定罰款外，還須承擔客房深度清潔與除味恢復費用。',
          },
          {
            en: 'Occupancy, safety and lawful conduct: Room occupancy must not exceed the maximum stated for the booked room type (including any approved extra bed) in compliance with local building and fire-safety regulations enforced in Sibu, Sarawak. Hazardous materials, controlled drugs (Dangerous Drugs Act 1952), unlawful gambling, or any unlawful activity are strictly forbidden. We may refuse check-in or terminate a stay without refund where a guest endangers safety, causes serious nuisance, or materially breaches these rules.',
            ms: 'Muatan bilik, keselamatan dan kelakuan sah: Bilangan penghuni bilik tidak boleh melebihi had maksimum yang dinyatakan bagi jenis bilik yang ditempah (termasuk sebarang katil tambahan yang diluluskan) selaras dengan peraturan bangunan dan keselamatan kebakaran tempatan yang dikuatkuasakan di Sibu, Sarawak. Bahan berbahaya, dadah terkawal (Akta Dadah Berbahaya 1952), perjudian haram, atau sebarang aktiviti menyalahi undang-undang adalah dilarang keras. Kami berhak menolak daftar masuk atau menamatkan penginapan tanpa bayaran balik sekiranya tetamu membahayakan keselamatan, menimbulkan gangguan serius, atau melanggar peraturan ini secara material.',
            zh: '入住人数、安全与守法行为：为遵守砂拉越诗巫当地建筑及消防安全法规，每间客房入住人数不得超过所订房型规定上限（含经批准的加床）。严禁携带危险物品、违禁毒品（《1952年危险毒品法令》）或从事非法赌博及任何违法活动。如宾客危及安全、造成严重滋扰或重大违反本守则，我们有权拒绝办理入住或终止住宿且不予退款。',
            'zh-TW': '入住人數、安全與守法行為：為遵守砂拉越詩巫當地建築及消防安全法規，每間客房入住人數不得超過所訂房型規定上限（含經批准的加床）。嚴禁攜帶危險物品、違禁毒品（《1952年危險毒品法令》）或從事非法賭博及任何違法活動。如賓客危及安全、造成嚴重滋擾或重大違反本守則，我們有權拒絕辦理入住或終止住宿且不予退款。',
          },
        ],
      },
      {
        id: 'liability',
        heading: {
          en: '6. Our responsibility and guest property',
          ms: '6. Tanggungjawab kami dan harta benda tetamu',
          zh: '6. 我们的责任与宾客财物',
          'zh-TW': '6. 我們的責任與賓客財物',
        },
        body: [
          {
            en: 'We will provide the accommodation you booked with reasonable care and skill in accordance with Part IX of the Consumer Protection Act 1999. We are not liable for unforeseeable losses or for failure or delay caused by events outside our reasonable control (such as utility outages, severe weather, public-health orders, or actions of civil authorities), though where an event prevents us from providing a paid room at all, we will offer rebooking or a refund of unused nights.',
            ms: 'Kami akan menyediakan penginapan yang anda tempah dengan penjagaan dan kemahiran yang munasabah selaras dengan Bahagian IX Akta Perlindungan Pengguna 1999. Kami tidak bertanggungjawab atas kerugian yang tidak dapat dijangka secara munasabah atau atas kegagalan mahupun kelewatan akibat kejadian di luar kawalan munasabah kami (seperti gangguan bekalan utiliti, cuaca buruk melampau, perintah kesihatan awam, atau tindakan pihak berkuasa awam); namun jika kejadian sedemikian menghalang kami daripada menyediakan bilik yang telah dibayar sama sekali, kami akan menawarkan penjadualan semula atau bayaran balik bagi malam yang belum digunakan.',
            zh: '我们将依《1999年消费者保护法令》第IX部，以合理的谨慎与技能提供您所预订的住宿。对于无法合理预见的损失，或因超出我们合理控制范围的事件（如市政水电中断、极端天气、公共卫生指令或政府机关行为）导致的无法履约或延误，我们不承担赔偿责任；但若该类事件导致我们完全无法提供已付款的客房，我们将协助改期或退还未入住晚数的房费。',
            'zh-TW': '我們將依《1999年消費者保護法令》第IX部，以合理的謹慎與技能提供您所預訂的住宿。對於無法合理預見的損失，或因超出我們合理控制範圍的事件（如市政水電中斷、極端天氣、公共衛生指令或政府機關行為）導致的無法履約或延誤，我們不承擔賠償責任；但若該類事件導致我們完全無法提供已付款的客房，我們將協助改期或退還未入住晚數的房費。',
          },
          {
            en: 'Nothing in these terms excludes or limits our liability for death or personal injury caused by our negligence, for fraud or fraudulent misrepresentation, or for any statutory right or liability that cannot lawfully be excluded under the laws of Malaysia applicable in Sarawak, including the Consumer Protection Act 1999 and the Contracts Act 1950.',
            ms: 'Tiada apa-apa dalam terma ini mengecualikan atau menghadkan liabiliti kami bagi kematian atau kecederaan diri akibat kecuaian kami, bagi penipuan atau salah nyata tipuan, atau bagi mana-mana hak atau liabiliti berkanun yang tidak boleh dikecualikan di sisi undang-undang Malaysia yang terpakai di Sarawak, termasuk Akta Perlindungan Pengguna 1999 dan Akta Kontrak 1950.',
            zh: '本条款任何内容均不排除或限制我们因自身疏忽造成的死亡或人身伤害责任、欺诈或欺诈性失实陈述责任，以及依适用于砂拉越的马来西亚法律（包括《1999年消费者保护法令》及《1950年合同法令》）不得合法排除的任何法定权利或责任。',
            'zh-TW': '本條款任何內容均不排除或限制我們因自身疏忽造成的死亡或人身傷害責任、欺詐或欺詐性失實陳述責任，以及依適用於砂拉越的馬來西亞法律（包括《1999年消費者保護法令》及《1950年合同法令》）不得合法排除的任何法定權利或責任。',
          },
          {
            en: 'Please lock your room door and keep cash, travel documents, electronics, and valuables secure at all times. Ground-level parking bays outside the hotel are provided for convenience and are used at the vehicle owner’s risk except to the extent loss or damage is caused by our negligence. Please report any loss or incident to our 24-hour reception immediately so we can assist.',
            ms: 'Sila kunci pintu bilik anda dan simpan wang tunai, dokumen perjalanan, peranti elektronik serta barangan berharga dengan selamat pada setiap masa. Petak letak kenderaan aras tanah di hadapan hotel disediakan untuk kemudahan dan digunakan atas risiko pemilik kenderaan sendiri kecuali setakat kehilangan atau kerosakan disebabkan oleh kecuaian kami. Sila laporkan sebarang kehilangan atau insiden kepada kaunter penyambut tetamu 24 jam kami dengan segera supaya kami dapat membantu.',
            zh: '请随手锁好房门，并妥善保管现金、旅行证件、电子设备及贵重物品。酒店门前的地面停车位仅供便利使用，除因我们疏忽造成的损失或损坏外，车辆停放风险由车主自行承担。如发生任何遗失或突发事件，请立即通知24小时前台以便协助。',
            'zh-TW': '請隨手鎖好房門，並妥善保管現金、旅行證件、電子設備及貴重物品。酒店門前的地面停車位僅供便利使用，除因我們疏忽造成的損失或損壞外，車輛停放風險由車主自行承擔。如發生任何遺失或突發事件，請立即通知24小時前台以便協助。',
          },
        ],
      },
      {
        id: 'personal-data',
        heading: {
          en: '7. Personal data and CCTV',
          ms: '7. Data peribadi dan CCTV',
          zh: '7. 个人资料保护与公共区域监控（CCTV）',
          'zh-TW': '7. 個人資料保護與公共區域監控（CCTV）',
        },
        body: [
          {
            en: 'We process your personal data in accordance with the Personal Data Protection Act 2010 (Act 709, as amended by the Personal Data Protection (Amendment) Act 2024) and our Privacy Notice, which explains what we collect (including 24-hour CCTV in public/common areas for safety and security), why we process it, who we share it with, how long we retain it, and how you may exercise your rights of access, correction, withdrawal of consent, and data portability.',
            ms: 'Kami memproses data peribadi anda selaras dengan Akta Perlindungan Data Peribadi 2010 (Akta 709, sebagaimana dipinda oleh Akta Perlindungan Data Peribadi (Pindaan) 2024) dan Notis Privasi kami, yang menerangkan data yang kami kumpul (termasuk rakaman CCTV 24 jam di kawasan awam/bersama bagi tujuan keselamatan), tujuan pemprosesan, pihak yang dikongsikan, tempoh penyimpanan, serta cara anda melaksanakan hak akses, pembetulan, penarikan balik persetujuan dan kemudahalihan data.',
            zh: '我们依照《2010年个人资料保护法令》（第709号法令，经《2024年个人资料保护（修正）法令》修订）及《隐私通知》处理您的个人资料，其中说明我们收集的内容（含为保障安全在公共区域运行的24小时闭路电视监控CCTV）、处理目的、共享对象、保留期限，以及您如何行使查阅、更正、撤回同意及资料可携权。',
            'zh-TW': '我們依照《2010年個人資料保護法令》（第709號法令，經《2024年個人資料保護（修正）法令》修訂）及《隱私通知》處理您的個人資料，其中說明我們收集的內容（含為保障安全在公共區域運行的24小時閉路電視監控CCTV）、處理目的、共享對象、保留期限，以及您如何行使查閱、更正、撤回同意及資料可攜權。',
          },
        ],
      },
      {
        id: 'law',
        heading: {
          en: '8. Governing law, consumer redress and contact',
          ms: '8. Undang-undang yang mentadbir, remedi pengguna dan hubungan',
          zh: '8. 管辖法律、消费者救济途径与联络方式',
          'zh-TW': '8. 管轄法律、消費者救濟途徑與聯絡方式',
        },
        emphasis: 'info',
        body: [
          {
            en: 'These terms are governed by the laws of Malaysia as applicable in Sarawak, and any dispute is subject to the jurisdiction of the courts of Malaysia (including the courts in Sarawak). If a consumer dispute cannot be resolved directly with us, you may also refer an eligible claim to the Tribunal for Consumer Claims Malaysia (Tribunal Tuntutan Pengguna Malaysia — TTPM) under Part XII of the Consumer Protection Act 1999.',
            ms: 'Terma ini ditadbir oleh undang-undang Malaysia sebagaimana terpakai di Sarawak, dan sebarang pertikaian tertakluk kepada bidang kuasa mahkamah Malaysia (termasuk mahkamah di Sarawak). Sekiranya pertikaian pengguna tidak dapat diselesaikan secara terus dengan kami, anda juga boleh merujuk tuntutan yang layak kepada Tribunal Tuntutan Pengguna Malaysia (TTPM) di bawah Bahagian XII Akta Perlindungan Pengguna 1999.',
            zh: '本条款受适用于砂拉越的马来西亚法律管辖，任何争议受马来西亚法院（包括砂拉越法院）管辖。若消费者争议无法与我们直接协商解决，您亦可依《1999年消费者保护法令》第XII部向马来西亚消费者索赔仲裁庭（TTPM）提交符合条件的申诉。',
            'zh-TW': '本條款受適用於砂拉越的馬來西亞法律管轄，任何爭議受馬來西亞法院（包括砂拉越法院）管轄。若消費者爭議無法與我們直接協商解決，您亦可依《1999年消費者保護法令》第XII部向馬來西亞消費者索賠仲裁庭（TTPM）提交符合條件的申訴。',
          },
          {
            en: `Reception operates ${identity.receptionHours}. For any enquiry, correction, complaint, or cancellation request, contact us at ${identity.email} or ${identity.phone}. If any provision of these terms is held invalid or unenforceable under Malaysian law, the remaining provisions continue in full force.`,
            ms: `Kaunter penyambut tetamu beroperasi ${identity.receptionHours}. Untuk sebarang pertanyaan, pembetulan, aduan atau permohonan pembatalan, hubungi kami di ${identity.email} atau ${identity.phone}. Sekiranya mana-mana peruntukan terma ini didapati tidak sah atau tidak boleh dikuatkuasakan di bawah undang-undang Malaysia, peruntukan selebihnya terus berkuat kuasa sepenuhnya.`,
            zh: `前台服务时间：${identity.receptionHours}。如有任何查询、更正、投诉或取消申请，请通过${identity.email}或${identity.phone}联系我们。若本条款任何规定依马来西亚法律被认定无效或不可执行，其余规定仍继续完全有效。`,
            'zh-TW': `前台服務時間：${identity.receptionHours}。如有任何查詢、更正、投訴或取消申請，請透過${identity.email}或${identity.phone}聯絡我們。若本條款任何規定依馬來西亞法律被認定無效或不可執行，其餘規定仍繼續完全有效。`,
          },
        ],
      },
    ],
  };
};
