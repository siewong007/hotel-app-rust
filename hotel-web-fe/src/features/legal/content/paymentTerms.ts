import { HOTEL_LEGAL_IDENTITY, type HotelLegalIdentity } from './hotelIdentity';
import type { LegalDocument, LocalizedText } from './types';

/**
 * Payment terms shown at the point of payment.
 *
 * Every statement here describes what the payment flow actually does:
 *  - the charged amount is derived server-side from the booking and never
 *    accepted from the browser (clause 1);
 *  - online payments use PayPal (for PayPal and debit/credit cards) or bank
 *    transfer, while walk-in / front-desk settlement additionally accepts
 *    cash, Sarawak Pay (S Pay Global), QRPay, and physical cards (clauses 4–5);
 *  - a bank transfer is a claim verified by staff via the payment-approvals
 *    queue before confirmation (clause 5);
 *  - tax itemisation (Service Tax Act 2018 [Act 807], Tourism Tax Act 2017
 *    [Act 791]), LHDN e-Invoicing (Income Tax Act 1967 [Act 53]), Consumer
 *    Protection Act 1999 (Act 599) rights, and anti-scam advisories (including
 *    Malaysia's National Scam Response Centre — 997) are clearly disclosed.
 */
export const PAYMENT_TERMS_VERSION = '2026-11-18';

/**
 * A short, scannable summary rendered inline beside the payment control. The
 * full document remains one click away; this is what most guests actually read.
 */
export const PAYMENT_KEY_POINTS: LocalizedText[] = [
  {
    en: 'The amount shown in Malaysian Ringgit (MYR) is calculated on our server from your booking, including any itemised Service Tax (SST) and Tourism Tax. Browser tampering cannot alter the charge.',
    ms: 'Jumlah yang dipaparkan dalam Ringgit Malaysia (MYR) dikira pada pelayan kami daripada tempahan anda, termasuk sebarang Cukai Perkhidmatan (SST) dan Cukai Pelancongan yang diperincikan. Amaun tidak sekali-kali diambil daripada pelayar anda.',
    zh: '所示马来西亚林吉特（MYR）金额由我们的服务器根据您的预订直接计算，已包含报价单列明的服务税（SST）及旅游税，无法在浏览器端被篡改。',
    'zh-TW': '所示馬來西亞令吉（MYR）金額由我們的伺服器根據您的預訂直接計算，已包含報價單列明的服務稅（SST）及旅遊稅，無法在瀏覽器端被篡改。',
  },
  {
    en: 'Online card and PayPal payments are processed directly by PayPal — your card number and security code are never seen or stored by the hotel.',
    ms: 'Pembayaran kad dan PayPal dalam talian diproses terus oleh PayPal — nombor kad dan kod keselamatan anda tidak pernah dilihat atau disimpan oleh hotel.',
    zh: '在线银行卡及PayPal付款均由PayPal直接处理——酒店不会看到也不会存储您的卡号与安全码。',
    'zh-TW': '線上銀行卡及PayPal付款均由PayPal直接處理——酒店不會看到也不會儲存您的卡號與安全碼。',
  },
  {
    en: 'A bank transfer is confirmed only after our staff verify receipt of funds in our bank account. Transfer early using the exact reference and upload your receipt to avoid hold expiry.',
    ms: 'Pindahan bank hanya disahkan selepas kakitangan kami menyemak penerimaan dana dalam akaun bank kami. Buat pindahan awal menggunakan rujukan tepat dan muat naik resit untuk mengelakkan tempoh tahanan tamat.',
    zh: '银行转账需在工作人员核实资金到账后方予确认。请使用准确的参考编号尽早转账并上传凭证，以免保留期届满失效。',
    'zh-TW': '銀行轉帳需在工作人員核實資金到帳後方予確認。請使用準確的參考編號盡早轉帳並上傳憑證，以免保留期屆滿失效。',
  },
  {
    en: 'Cancel at least three (3) calendar days before arrival for a full room refund; later cancellation or no-show may incur the first night’s charge.',
    ms: 'Batalkan sekurang-kurangnya tiga (3) hari kalendar sebelum ketibaan untuk bayaran balik penuh bilik; pembatalan lewat atau ketidakhadiran boleh dikenakan caj malam pertama.',
    zh: '于抵达前至少三（3）个日历日取消可获全额房费退款；逾期取消或未入住可能收取首晚房费。',
    'zh-TW': '於抵達前至少三（3）個日曆日取消可獲全額房費退款；逾期取消或未入住可能收取首晚房費。',
  },
];

export const buildPaymentTerms = (
  identity: HotelLegalIdentity = HOTEL_LEGAL_IDENTITY
): LegalDocument => ({
  id: 'payment_terms',
  version: PAYMENT_TERMS_VERSION,
  effectiveDate: '2026-11-18',
  title: {
    en: 'Payment Terms',
    ms: 'Terma Pembayaran',
    zh: '付款条款',
    'zh-TW': '付款條款',
  },
  summary: {
    en: `These terms govern payments made to ${identity.tradingName}, explaining how your total and Malaysian taxes are calculated, how online and front-desk payment methods operate, when a reservation becomes confirmed, how receipts, e-Invoices, and refunds are handled, and how to keep your payment safe. They apply alongside our Booking Terms and Conditions.`,
    ms: `Terma ini mentadbir pembayaran kepada ${identity.tradingName}, menerangkan cara jumlah bayaran dan cukai Malaysia dikira, cara kaedah pembayaran dalam talian dan di kaunter berfungsi, bila tempahan disahkan, cara resit, e-Invois dan bayaran balik dikendalikan, serta panduan keselamatan pembayaran. Ia terpakai bersama Terma dan Syarat Tempahan kami.`,
    zh: `本条款约束您向${identity.tradingName}支付款项的事宜，说明应付总额与马来西亚税费的计算方式、线上及前台各付款渠道的运作规则、预订何时获得确认、收据与电子发票及退款的处理方式，以及付款防诈安全须知。本条款与《预订条款与条件》一并适用。`,
    'zh-TW': `本條款約束您向${identity.tradingName}支付款項的事宜，說明應付總額與馬來西亞稅費的計算方式、線上及前台各付款渠道的運作規則、預訂何時獲得確認、收據與電子發票及退款的處理方式，以及付款防詐安全須知。本條款與《預訂條款與條件》一併適用。`,
  },
  sections: [
    {
      id: 'what-you-pay',
      heading: {
        en: '1. What you are paying and tax itemisation',
        ms: '1. Amaun yang dibayar dan perincian cukai',
        zh: '1. 应付金额与税费明细',
        'zh-TW': '1. 應付金額與稅費明細',
      },
      emphasis: 'requirement',
      body: [
        {
          en: 'The amount presented is the total payable for the reservation shown: published room and extra-bed rates are inclusive of applicable Service Tax under the Service Tax Act 2018 (Act 807) (itemised separately on your checkout invoice), plus additive Tourism Tax of RM10.00 per room per night where applicable to foreign tourists under the Tourism Tax Act 2017 (Act 791).',
          ms: 'Amaun yang dipaparkan adalah jumlah keseluruhan yang perlu dibayar bagi tempahan yang ditunjukkan: kadar bilik dan katil tambahan yang diterbitkan telah merangkumi Cukai Perkhidmatan yang terpakai di bawah Akta Cukai Perkhidmatan 2018 (Akta 807) (diperincikan secara berasingan pada invois daftar keluar anda), ditambah Cukai Pelancongan RM10.00 setiap bilik setiap malam sekiranya terpakai kepada pelancong asing di bawah Akta Cukai Pelancongan 2017 (Akta 791).',
          zh: '向您展示的金额即为所示预订的应付总额：公布的房价与加床费已包含依《2018年服务税法令》（第807号法令）适用的服务税（将在退房发票上单独列示税额），并另行加上依《2017年旅游税法令》（第791号法令）适用于外国旅客的旅游税（每房每晚RM10.00）。',
          'zh-TW': '向您展示的金額即為所示預訂的應付總額：公佈的房價與加床費已包含依《2018年服務稅法令》（第807號法令）適用的服務稅（將在退房發票上單獨列示稅額），並另行加上依《2017年旅遊稅法令》（第791號法令）適用於外國旅客的旅遊稅（每房每晚RM10.00）。',
        },
        {
          en: 'Our server computes the payable total directly from your stored booking record at the moment of payment; client-side values are never trusted. If the displayed figure ever differs from what you expect, stop and contact reception before authorising payment.',
          ms: 'Pelayan kami mengira jumlah perlu dibayar terus daripada rekod tempahan anda yang tersimpan pada masa pembayaran dibuat; nilai daripada pelayar tidak pernah dipercayai. Sekiranya angka yang dipaparkan berbeza daripada jangkaan anda, hentikan transaksi dan hubungi kaunter penyambut tetamu sebelum membenarkan bayaran.',
          zh: '应付总额由我们的服务器在付款当下直接根据系统保存的预订记录计算，绝不采信浏览器端提交的金额。如显示金额与您的预期不符，请立即停止操作并在付款前联系前台核实。',
          'zh-TW': '應付總額由我們的伺服器在付款當下直接根據系統保存的預訂記錄計算，絕不採信瀏覽器端提交的金額。如顯示金額與您的預期不符，請立即停止操作並在付款前聯絡前台核實。',
        },
        {
          en: 'On-site deposits (such as a refundable keycard deposit) and incidental stay charges (such as extra beds added after arrival, late check-out, or damage recovery) are billed separately and settled at reception.',
          ms: 'Deposit di premis (seperti deposit kad kunci boleh dikembalikan) dan caj sampingan penginapan (seperti katil tambahan selepas ketibaan, daftar keluar lewat, atau ganti rugi kerosakan) dibilkan secara berasingan dan dijelaskan di kaunter penyambut tetamu.',
          zh: '到店押金（如可退还的房卡押金）及入住期间产生的杂项费用（如到店后加床、延迟退房或损坏赔偿）将另行计费并于前台结清。',
          'zh-TW': '到店押金（如可退還的房卡押金）及入住期間產生的雜項費用（如到店後加床、延遲退房或損壞賠償）將另行計費並於前台結清。',
        },
      ],
    },
    {
      id: 'currency',
      heading: {
        en: '2. Settlement currency, exchange rates and receipts',
        ms: '2. Mata wang penyelesaian, kadar pertukaran dan resit',
        zh: '2. 结算币种、汇率转换与收据发票',
        'zh-TW': '2. 結算幣種、匯率轉換與收據發票',
      },
      body: [
        {
          en: 'Our base pricing and accounting currency in Malaysia is Malaysian Ringgit (MYR / RM). Where your payment card, PayPal wallet, or bank account is denominated in another currency, your card issuer, bank, or PayPal determines the foreign-exchange rate and any cross-border or conversion fee; those third-party charges are outside our control and are not part of the sum we receive.',
          ms: 'Mata wang asas harga dan perakaunan kami di Malaysia ialah Ringgit Malaysia (MYR / RM). Sekiranya kad pembayaran, dompet PayPal atau akaun bank anda dalam mata wang asing, pengeluar kad, bank atau PayPal anda menentukan kadar pertukaran asing serta sebarang fi rentas sempadan atau penukaran; caj pihak ketiga tersebut berada di luar kawalan kami dan bukan sebahagian daripada amaun yang kami terima.',
          zh: '我们在马来西亚的基准计价与记账币种为马来西亚林吉特（MYR / RM）。若您的银行卡、PayPal账户或银行账户使用其他币种，汇率及任何跨境或货币转换手续费均由发卡机构、银行或PayPal决定；该等第三方费用非我们所能控制，亦不属于我们实收款项的一部分。',
          'zh-TW': '我們在馬來西亞的基準計價與記帳幣種為馬來西亞令吉（MYR / RM）。若您的銀行卡、PayPal帳戶或銀行帳戶使用其他幣種，匯率及任何跨境或貨幣轉換手續費均由發卡機構、銀行或PayPal決定；該等第三方費用非我們所能控制，亦不屬於我們實收款項的一部分。',
        },
        {
          en: 'An electronic payment receipt is issued once your payment is verified. If you or your organisation require an LHDN-validated e-Invoice under Malaysia’s Income Tax Act 1967 (Act 53) (subject to applicable statutory thresholds), please provide your Tax Identification Number (TIN), NRIC or business registration number, and billing address promptly upon payment or before check-out.',
          ms: 'Resit pembayaran elektronik dikeluarkan sebaik sahaja bayaran anda disahkan. Sekiranya anda atau organisasi anda memerlukan e-Invois yang disahkan LHDN di bawah Akta Cukai Pendapatan 1967 (Akta 53) Malaysia (tertakluk kepada ambang berkanun yang terpakai), sila berikan Nombor Pengenalan Cukai (TIN), NRIC atau nombor pendaftaran perniagaan serta alamat pengebilan anda dengan segera semasa pembayaran atau sebelum daftar keluar.',
          zh: '付款核实后系统将开具电子付款收据。如您或贵单位需依马来西亚《1967年所得税法令》（第53号法令）开具经LHDN验证的电子发票（视现行法定适用门槛而定），请在付款时或退房前及时提供税务识别号（TIN）、身份证号或商业登记号及账单地址。',
          'zh-TW': '付款核實後系統將開具電子付款收據。如您或貴單位需依馬來西亞《1967年所得稅法令》（第53號法令）開具經LHDN驗證的電子發票（視現行法定適用門檻而定），請在付款時或退房前及時提供稅務識別號（TIN）、身分證號或商業登記號及帳單地址。',
        },
      ],
    },
    {
      id: 'when-confirmed',
      heading: {
        en: '3. When your booking becomes confirmed',
        ms: '3. Bila tempahan anda disahkan',
        zh: '3. 预订何时获得确认',
        'zh-TW': '3. 預訂何時獲得確認',
      },
      emphasis: 'requirement',
      body: [
        {
          en: 'Your booking becomes confirmed only when full payment has been received and matched to your reservation (immediately upon PayPal capture, or upon staff verification for a bank transfer). Until confirmation, the room is provisionally held rather than guaranteed, and an unpaid online hold may be released automatically once its holding window expires.',
          ms: 'Tempahan anda hanya disahkan apabila bayaran penuh telah diterima dan dipadankan dengan tempahan anda (serta-merta selepas tangkapan bayaran PayPal, atau selepas pengesahan kakitangan bagi pindahan bank). Sehingga pengesahan dibuat, bilik hanya ditahan sementara dan bukan dijamin, dan tahanan dalam talian tanpa bayaran boleh dilepaskan secara automatik apabila tempoh tahanannya tamat.',
          zh: '只有在全额款项到账并与您的预订核对无误后（PayPal扣款成功后即时确认，银行转账则经员工核实后确认），您的预订方告确认。确认前客房仅作临时保留而非保证；未完成付款的在线保留预订在保留窗口届满后可能被自动释放。',
          'zh-TW': '只有在全額款項到帳並與您的預訂核對無誤後（PayPal扣款成功後即時確認，銀行轉帳則經員工核實後確認），您的預訂方告確認。確認前客房僅作臨時保留而非保證；未完成付款的線上保留預訂在保留窗口屆滿後可能被自動釋出。',
        },
      ],
    },
    {
      id: 'card-and-paypal',
      heading: {
        en: '4. Online card, PayPal and front-desk payment channels',
        ms: '4. Saluran pembayaran kad dalam talian, PayPal dan kaunter hadapan',
        zh: '4. 线上银行卡、PayPal及前台付款渠道',
        'zh-TW': '4. 線上銀行卡、PayPal及前台付款渠道',
      },
      body: [
        {
          en: 'Online portal payments can be made via PayPal (supporting PayPal balances and major debit/credit cards) or via bank transfer. Additional walk-in and front-desk settlement methods — including Cash, Visa, Mastercard, Debit Card, Sarawak Pay (S Pay Global), Boost, MAE, and QRPay / DuitNow QR — are accepted in person at reception.',
          ms: 'Pembayaran dalam talian melalui portal boleh dibuat melalui PayPal (menyokong baki PayPal serta kad debit/kredit utama) atau pindahan bank. Kaedah penyelesaian tambahan secara bersemuka — termasuk Tunai, Visa, Mastercard, Kad Debit, Sarawak Pay (S Pay Global), Boost, MAE dan QRPay / DuitNow QR — diterima di kaunter penyambut tetamu.',
          zh: '线上门户支持通过PayPal（可使用PayPal余额及主流借记卡/信用卡）或银行转账付款。亲临前台办理结算时，亦接受现金、Visa、Mastercard、借记卡、Sarawak Pay（S Pay Global）、Boost、MAE及QRPay / DuitNow QR等支付方式。',
          'zh-TW': '線上門戶支援透過PayPal（可使用PayPal餘額及主流金融卡/信用卡）或銀行轉帳付款。親臨前台辦理結算時，亦接受現金、Visa、Mastercard、金融卡、Sarawak Pay（S Pay Global）、Boost、MAE及QRPay / DuitNow QR等支付方式。',
        },
        {
          en: 'When paying online by card or PayPal, you enter your payment credentials directly into PayPal’s encrypted checkout interface; the hotel never sees, receives, or stores your full card number, expiry date, or CVV security code. We record only the payment status, amount, method, and PayPal transaction reference for reconciliation and audit.',
          ms: 'Apabila membayar dalam talian menggunakan kad atau PayPal, anda memasukkan butiran pembayaran terus ke dalam antara muka tersulit PayPal; hotel tidak pernah melihat, menerima atau menyimpan nombor kad penuh, tarikh luput atau kod keselamatan CVV anda. Kami hanya merekodkan status bayaran, amaun, kaedah dan nombor rujukan transaksi PayPal bagi tujuan penyelarasan dan audit.',
          zh: '使用线上银行卡或PayPal付款时，您的支付凭据直接输入PayPal加密结账界面；本酒店绝不会看到、接收或存储您的完整卡号、有效期或CVV安全码。我们仅记录付款状态、金额、方式及PayPal交易参考号，以供对账与审计。',
          'zh-TW': '使用線上銀行卡或PayPal付款時，您的支付憑據直接輸入PayPal加密結帳介面；本酒店絕不會看到、接收或儲存您的完整卡號、有效期或CVV安全碼。我們僅記錄付款狀態、金額、方式及PayPal交易參考號，以供對帳與審計。',
        },
        {
          en: 'Do not close or reload your browser while payment authorisation is in progress. If a transaction appears interrupted, check your email and booking status before retrying so you do not submit duplicate charges.',
          ms: 'Jangan tutup atau muat semula pelayar anda semasa pengesahan pembayaran sedang berjalan. Sekiranya transaksi kelihatan tergendala, semak e-mel dan status tempahan anda sebelum mencuba semula bagi mengelakkan caj berganda.',
          zh: '付款授权处理期间请勿关闭或刷新浏览器页面。若交易看似中断，请先查看电子邮件及预订状态后再决定是否重试，以免造成重复扣款。',
          'zh-TW': '付款授權處理期間請勿關閉或重新整理瀏覽器頁面。若交易看似中斷，請先查看電子郵件及預訂狀態後再決定是否重試，以免造成重複扣款。',
        },
      ],
    },
    {
      id: 'bank-transfer',
      heading: {
        en: '5. Paying by bank transfer',
        ms: '5. Pembayaran melalui pindahan bank',
        zh: '5. 以银行转账付款',
        'zh-TW': '5. 以銀行轉帳付款',
      },
      emphasis: 'requirement',
      body: [
        {
          en: 'A bank transfer is not confirmed instantaneously. Submitting a bank-transfer notice records your claim that you have transferred (or are transferring) funds to our designated hotel bank account. Authorised staff reconcile the transfer against our bank statement and approve it; your reservation remains unconfirmed until that verification is completed.',
          ms: 'Pindahan bank tidak disahkan secara serta-merta. Menghantar makluman pindahan bank merekodkan makluman anda bahawa anda telah (atau sedang) memindahkan dana ke akaun bank rasmi hotel yang dipaparkan. Kakitangan yang diberi kuasa akan menyemaknya dengan penyata bank kami dan meluluskannya; tempahan anda kekal belum disahkan sehingga semakan tersebut selesai.',
          zh: '银行转账并非即时自动确认。提交银行转账申报即登记您已向（或正向）所示酒店指定银行账户汇款。授权工作人员核对银行账单无误后予以批准；在核实完成前，您的预订仍处于未确认状态。',
          'zh-TW': '銀行轉帳並非即時自動確認。提交銀行轉帳申報即登記您已向（或正向）所示酒店指定銀行帳戶匯款。授權工作人員核對銀行帳單無誤後予以批准；在核實完成前，您的預訂仍處於未確認狀態。',
        },
        {
          en: 'Please transfer the exact MYR amount quoted, enter the booking reference provided, and upload a clear transfer slip or receipt. Mismatched amounts or missing references are the primary cause of delayed verification or lapsed holds.',
          ms: 'Sila pindahkan amaun MYR yang tepat seperti dalam sebut harga, masukkan rujukan tempahan yang diberikan, dan muat naik slip atau resit pindahan yang jelas. Amaun tidak sepadan atau ketiadaan rujukan adalah punca utama kelewatan pengesahan atau tamat tempoh tahanan.',
          zh: '请按报价单所示准确林吉特（MYR）金额转账，备注我们提供的预订参考号，并上传清晰的转账回单或收据。金额不符或漏填参考号是导致核验延误或保留失效的最常见原因。',
          'zh-TW': '請按報價單所示準確令吉（MYR）金額轉帳，備註我們提供的預訂參考號，並上傳清晰的轉帳回單或收據。金額不符或漏填參考號是導致核驗延誤或保留失效的最常見原因。',
        },
        {
          en: 'Uploaded receipts are stored securely alongside your payment record solely for verification and statutory accounting audit. Please mask or omit unrelated personal banking information before uploading.',
          ms: 'Resit yang dimuat naik disimpan dengan selamat bersama rekod pembayaran anda semata-mata untuk pengesahan dan audit perakaunan berkanun. Sila kaburkan atau elakkan memuat naik maklumat perbankan peribadi lain yang tidak berkaitan.',
          zh: '您上传的转账凭证仅用于核实收款及法定会计审计，并与付款记录安全保存。上传前请遮蔽或避免包含与本次转账无关的其他个人银行隐私信息。',
          'zh-TW': '您上傳的轉帳憑證僅用於核實收款及法定會計審計，並與付款記錄安全保存。上傳前請遮蔽或避免包含與本次轉帳無關的其他個人銀行隱私資訊。',
        },
      ],
    },
    {
      id: 'refunds',
      heading: {
        en: '6. Refunds and reversals',
        ms: '6. Bayaran balik dan pembalikan transaksi',
        zh: '6. 退款与冲正处理',
        'zh-TW': '6. 退款與沖正處理',
      },
      body: [
        {
          en: 'Approved refunds under our cancellation policy are returned to the original payment source: PayPal and card payments are refunded to the originating PayPal account or card, and bank transfers are refunded to the originating bank account (for which we may verify your account details). This anti-fraud rule also complies with Malaysian anti-money-laundering controls.',
          ms: 'Bayaran balik yang diluluskan di bawah dasar pembatalan kami dikembalikan ke sumber pembayaran asal: pembayaran PayPal dan kad dipulangkan ke akaun PayPal atau kad asal, manakala pindahan bank dipulangkan ke akaun bank pengirim asal (yang mana kami mungkin menyemak butiran akaun anda). Peraturan pencegahan penipuan ini turut mematuhi kawalan pencegahan pengubahan wang haram Malaysia.',
          zh: '凡符合取消政策的获批退款，均原路退回至原付款渠道：PayPal及银行卡付款退回原PayPal账户或原卡；银行转账退回原汇出账户（为此我们可能需核实您的收款账户资料）。此项反欺诈规则亦符合马来西亚反洗钱合规要求。',
          'zh-TW': '凡符合取消政策的獲批退款，均原路退回至原付款渠道：PayPal及銀行卡付款退回原PayPal帳戶或原卡；銀行轉帳退回原匯出帳戶（為此我們可能需核實您的收款帳戶資料）。此項反欺詐規則亦符合馬來西亞反洗錢合規要求。',
        },
        {
          en: 'Once initiated by us, bank and card-scheme settlement times are governed by your bank or card issuer (typically several business days) and are outside our control. Foreign-currency conversion differences applied by your bank or PayPal are not reimbursable by the hotel.',
          ms: 'Sebaik sahaja dimulakan oleh kami, tempoh kemasukan dana oleh bank dan rangkaian kad ditentukan oleh bank atau pengeluar kad anda (lazimnya beberapa hari bekerja) dan berada di luar kawalan kami. Perbezaan pertukaran mata wang asing yang dikenakan oleh bank atau PayPal anda tidak dapat diganti oleh pihak hotel.',
          zh: '我们发起退款后，具体入账时间取决于您的发卡银行或清算机构（通常需数个工作日），非本酒店所能控制。由您的银行或PayPal产生的汇率折算差额，本酒店不予补偿。',
          'zh-TW': '我們發起退款後，具體入帳時間取決於您的發卡銀行或清算機構（通常需數個工作日），非本酒店所能控制。由您的銀行或PayPal產生的匯率折算差額，本酒店不予補償。',
        },
      ],
    },
    {
      id: 'problems',
      heading: {
        en: '7. Discrepancies, disputes and consumer rights',
        ms: '7. Percanggahan, pertikaian dan hak pengguna',
        zh: '7. 账务异议、争议解决与消费者权利',
        'zh-TW': '7. 帳務異議、爭議解決與消費者權利',
      },
      emphasis: 'info',
      body: [
        {
          en: `If you believe you were charged twice, billed an incorrect amount, or if a completed payment does not appear on your booking, contact us promptly at ${identity.email} or ${identity.phone} (reception operates ${identity.receptionHours}) with your booking number and transaction reference.`,
          ms: `Sekiranya anda percaya anda telah dicaj dua kali, dibilkan amaun yang salah, atau bayaran yang selesai tidak tertera pada tempahan anda, hubungi kami segera di ${identity.email} atau ${identity.phone} (kaunter penyambut tetamu beroperasi ${identity.receptionHours}) berserta nombor tempahan dan rujukan transaksi anda.`,
          zh: `如您发现重复扣款、金额有误，或已完成付款但未显示在预订记录中，请尽快凭预订编号及交易参考号通过 ${identity.email} 或 ${identity.phone}（前台${identity.receptionHours}服务）联系我们。`,
          'zh-TW': `如您發現重複扣款、金額有誤，或已完成付款但未顯示在預訂記錄中，請盡快憑預訂編號及交易參考號透過 ${identity.email} 或 ${identity.phone}（前台${identity.receptionHours}服務）聯絡我們。`,
        },
        {
          en: 'Every payment action is recorded in an append-only audit trail so our team can investigate promptly. Nothing in these Payment Terms limits your statutory rights under the Consumer Protection Act 1999 (Act 599), including access to the Tribunal for Consumer Claims Malaysia (TTPM).',
          ms: 'Setiap tindakan pembayaran direkodkan dalam jejak audit tambah-sahaja supaya pasukan kami dapat menyiasat dengan segera. Tiada apa-apa dalam Terma Pembayaran ini menghadkan hak berkanun anda di bawah Akta Perlindungan Pengguna 1999 (Akta 599), termasuk akses kepada Tribunal Tuntutan Pengguna Malaysia (TTPM).',
          zh: '每项收付款操作均保存在防篡改追加式审计日志中，便于我们迅速核查追溯。本《付款条款》任何内容均不限制您在《1999年消费者保护法令》（第599号法令）下的法定权利，包括向马来西亚消费者索赔仲裁庭（TTPM）申诉的权利。',
          'zh-TW': '每項收付款操作均保存在防篡改追加式審計日誌中，便於我們迅速核查追溯。本《付款條款》任何內容均不限制您在《1999年消費者保護法令》（第599號法令）下的法定權利，包括向馬來西亞消費者索賠仲裁庭（TTPM）申訴的權利。',
        },
      ],
    },
    {
      id: 'security-advice',
      heading: {
        en: '8. Anti-scam and payment security notice',
        ms: '8. Notis keselamatan pembayaran dan pencegahan penipuan',
        zh: '8. 防诈骗与付款安全提示',
        'zh-TW': '8. 防詐騙與付款安全提示',
      },
      body: [
        {
          en: 'We will never phone, email, or message you to ask for your full card number, CVV code, online banking password, DuitNow/TAC one-time passcode (OTP), or to ask you to transfer money to a personal bank account not shown in our official booking portal. Any such message claiming to come from us is fraudulent — do not act on it, and notify us immediately.',
          ms: 'Kami tidak sekali-kali akan menelefon, menghantar e-mel atau mesej untuk meminta nombor kad penuh, kod CVV, kata laluan perbankan dalam talian, kod pengesahan sekali guna (OTP/TAC), atau meminta pindahan wang ke akaun bank peribadi yang tidak tertera dalam portal tempahan rasmi kami. Sebarang mesej sedemikian yang mendakwa daripada kami adalah penipuan — jangan bertindak ke atasnya dan maklumkan kami segera.',
          zh: '我们绝不会通过电话、电子邮件或即时通讯向您索取完整卡号、CVV安全码、网银登录密码、一次性验证码（OTP/TAC），亦绝不会要求您向官方预订门户未列示的个人银行账户汇款。凡自称本酒店提出上述要求者均属诈骗——请勿理会并立即通知我们。',
          'zh-TW': '我們絕不會透過電話、電子郵件或即時通訊向您索取完整卡號、CVV安全碼、網銀登入密碼、一次性驗證碼（OTP/TAC），亦絕不會要求您向官方預訂門戶未列示的個人銀行帳戶匯款。凡自稱本酒店提出上述要求者均屬詐騙——請勿理會並立即通知我們。',
        },
        {
          en: 'Always verify the website address before paying. If you suspect an authorised-push or banking scam in Malaysia, contact your bank’s 24-hour fraud hotline immediately and call Malaysia’s National Scam Response Centre (NSRC) at 997.',
          ms: 'Sentiasa semak alamat laman web sebelum membuat bayaran. Sekiranya anda mengesyaki penipuan perbankan dalam talian di Malaysia, hubungi talian kecemasan penipuan 24 jam bank anda dengan segera dan hubungi Pusat Respons Scam Kebangsaan (NSRC) Malaysia di talian 997.',
          zh: '付款前请务必核对浏览器网址。如您在马来西亚怀疑遭遇电信或银行转账诈骗，请立即致电您开户银行的24小时反诈热线，并拨打马来西亚国家诈骗应对中心（NSRC）专线 997。',
          'zh-TW': '付款前請務必核對瀏覽器網址。如您在馬來西亞懷疑遭遇電信或銀行轉帳詐騙，請立即致電您開戶銀行的24小時反詐熱線，並撥打馬來西亞國家詐騙應對中心（NSRC）專線 997。',
        },
      ],
    },
  ],
});

export const paymentTerms: LegalDocument = buildPaymentTerms();
