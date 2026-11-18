import { HOTEL_LEGAL_IDENTITY, type HotelLegalIdentity } from './hotelIdentity';
import type { LegalDocument } from './types';

/**
 * PDPA s.7 "Notice and Choice" notice, updated for the Personal Data Protection
 * (Amendment) Act 2024 (Act A1727 — fully in force across Phases 1–3 by 1 June
 * 2025) and the 2025 Personal Data Protection Commissioner (JPDP) guidelines.
 *
 * Section 7(1) of the Personal Data Protection Act 2010 (Act 709) lists what a
 * written notice must tell a data subject, and every item has a dedicated
 * section below:
 *   (a) that data is being processed + description      -> `what-we-collect`
 *   (b) the purposes of collection                      -> `why-we-use-it`
 *   (c) any information available as to the source      -> `where-it-comes-from`
 *   (d) right of access/correction + DPO/contact (s.12A)-> `controller`, `your-rights`
 *   (e) class of third parties + processor duty (s.9)   -> `who-we-share-with`
 *   (f) choices to limit processing (ss. 42, 43)        -> `your-choices`
 *   (g) obligatory vs voluntary supply & consequences   -> `is-it-obligatory`
 *
 * Additional statutory disclosures cover biometric sensitive data (ss. 4 & 40),
 * common-area 24-hour CCTV surveillance (JPDP CCTV Guidelines), minors under 18
 * (Personal Data Protection Regulations 2013 r.4), amended cross-border
 * transfers (s.129), data portability (s.43A), retention (s.10), and mandatory
 * 72-hour data breach notification (s.12B). Section 7(2) requires the notice in
 * both Bahasa Malaysia and English, enforced at compile time by `LocalizedText`.
 */
export const PRIVACY_NOTICE_VERSION = '2026-11-18';

export const buildPrivacyNotice = (
  identity: HotelLegalIdentity = HOTEL_LEGAL_IDENTITY
): LegalDocument => {
  const addressOneLine = identity.addressLines.join(', ');
  return {
    id: 'privacy_notice',
    version: PRIVACY_NOTICE_VERSION,
    effectiveDate: '2026-11-18',
    title: {
      en: 'Privacy Notice (Personal Data Protection Act 2010)',
      ms: 'Notis Privasi (Akta Perlindungan Data Peribadi 2010)',
      zh: '隐私通知（2010年个人资料保护法令）',
      'zh-TW': '隱私通知（2010年個人資料保護法令）',
    },
    summary: {
      en: `${identity.tradingName} processes personal data so we can take your booking, host your stay, process payments, maintain guest and premises safety, and comply with Malaysian law. This statutory notice is issued under section 7 of the Personal Data Protection Act 2010 (Act 709), as amended by the Personal Data Protection (Amendment) Act 2024 (Act A1727), and explains what we collect, why we process it, and the rights you hold over it.`,
      ms: `${identity.tradingName} memproses data peribadi supaya kami boleh menerima tempahan anda, menguruskan penginapan anda, memproses pembayaran, menjaga keselamatan tetamu serta premis, dan mematuhi undang-undang Malaysia. Notis berkanun ini dikeluarkan di bawah seksyen 7 Akta Perlindungan Data Peribadi 2010 (Akta 709), sebagaimana dipinda oleh Akta Perlindungan Data Peribadi (Pindaan) 2024 (Akta A1727), dan menerangkan apa yang kami kumpul, tujuan pemprosesannya, serta hak yang anda miliki ke atasnya.`,
      zh: `${identity.tradingName}处理个人资料，以便承接您的预订、安排您的住宿、处理款项、保障宾客与场所安全并遵守马来西亚法律。本法定通知依据经《2024年个人资料保护（修正）法令》（第A1727号法令）修订的《2010年个人资料保护法令》（第709号法令）第7条发出，说明我们收集的内容、处理目的以及您享有的法定权利。`,
      'zh-TW': `${identity.tradingName}處理個人資料，以便承接您的預訂、安排您的住宿、處理款項、保障賓客與場所安全並遵守馬來西亞法律。本法定通知依據經《2024年個人資料保護（修正）法令》（第A1727號法令）修訂的《2010年個人資料保護法令》（第709號法令）第7條發出，說明我們收集的內容、處理目的以及您享有的法定權利。`,
    },
    sections: [
      {
        id: 'controller',
        heading: {
          en: '1. Data controller and Data Protection Contact',
          ms: '1. Pengawal data dan Pegawai / Hubungan Perlindungan Data',
          zh: '1. 资料控制者与个人资料保护联络人',
          'zh-TW': '1. 資料控制者與個人資料保護聯絡人',
        },
        emphasis: 'info',
        body: [
          {
            en: `The data controller (pengawal data) is ${identity.registeredName} (business registration number ${identity.companyRegistrationNumber}), of ${addressOneLine}. In accordance with sections 7(1)(d) and 12A of the Personal Data Protection Act 2010, our Data Protection Contact for privacy enquiries, access, correction, portability or withdrawal requests, and complaints can be reached at ${identity.dataProtectionContactEmail} or by telephone at ${identity.phone} (${identity.receptionHours}).`,
            ms: `Pengawal data ialah ${identity.registeredName} (nombor pendaftaran perniagaan ${identity.companyRegistrationNumber}), beralamat di ${addressOneLine}. Selaras dengan seksyen 7(1)(d) dan 12A Akta Perlindungan Data Peribadi 2010, Hubungan Perlindungan Data kami bagi pertanyaan privasi, permohonan akses, pembetulan, kemudahalihan atau penarikan balik persetujuan, serta aduan boleh dihubungi melalui ${identity.dataProtectionContactEmail} atau telefon di ${identity.phone} (${identity.receptionHours}).`,
            zh: `资料控制者（pengawal data）为${identity.registeredName}（商业登记号码：${identity.companyRegistrationNumber}），地址：${addressOneLine}。依据《2010年个人资料保护法令》第7(1)(d)条及第12A条，如您有任何隐私查询、查阅、更正、可携传输、撤回同意请求或投诉，请联系我们的资料保护联络渠道：电子邮箱 ${identity.dataProtectionContactEmail} 或电话 ${identity.phone}（${identity.receptionHours}）。`,
            'zh-TW': `資料控制者（pengawal data）為${identity.registeredName}（商業登記號碼：${identity.companyRegistrationNumber}），地址：${addressOneLine}。依據《2010年個人資料保護法令》第7(1)(d)條及第12A條，如您有任何隱私查詢、查閱、更正、可攜傳輸、撤回同意請求或投訴，請聯絡我們的資料保護聯絡渠道：電子郵箱 ${identity.dataProtectionContactEmail} 或電話 ${identity.phone}（${identity.receptionHours}）。`,
          },
        ],
      },
      {
        id: 'what-we-collect',
        heading: {
          en: '2. What personal data we process',
          ms: '2. Data peribadi yang kami proses',
          zh: '2. 我们处理的个人资料类别',
          'zh-TW': '2. 我們處理的個人資料類別',
        },
        body: [
          {
            en: 'What we hold depends on how you book, pay, and stay with us. Across our services we process the following categories of personal data:',
            ms: 'Data yang kami simpan bergantung pada cara anda menempah, membayar dan menginap bersama kami. Merentasi perkhidmatan kami, kami memproses kategori data peribadi berikut:',
            zh: '我们所持有的资料取决于您的预订、付款及入住方式。在各项服务中，我们可能处理下列类别的个人资料：',
            'zh-TW': '我們所持有的資料取決於您的預訂、付款及入住方式。在各項服務中，我們可能處理下列類別的個人資料：',
          },
        ],
        bullets: [
          {
            en: 'Identity, contact and tax particulars: full name, preferred name, email address, telephone number, residential or billing address, nationality, gender, date of birth, NRIC or passport details recorded at check-in, declared guest type (Malaysian citizen/PR or foreign tourist for Tourism Tax), and — where you request a validated LHDN e-Invoice — your Tax Identification Number (TIN) or business registration details.',
            ms: 'Butiran identiti, hubungan dan cukai: nama penuh, nama pilihan, alamat e-mel, nombor telefon, alamat kediaman atau pengebilan, kewarganegaraan, jantina, tarikh lahir, butiran NRIC atau pasport yang direkodkan semasa daftar masuk, jenis tetamu yang diisytiharkan (warganegara/PR Malaysia atau pelancong asing bagi Cukai Pelancongan), dan — sekiranya anda memohon e-Invois LHDN yang disahkan — Nombor Pengenalan Cukai (TIN) atau butiran pendaftaran perniagaan anda.',
            zh: '身份、联络及税务资料：姓名、称呼、电子邮箱、电话号码、居住或账单地址、国籍、性别、出生日期、入住登记时记录的身份证（NRIC）或护照信息、申报的旅客类型（用于核算旅游税的马来西亚公民/永久居民或外国旅客），以及在您要求开具LHDN验证电子发票时提供的税务识别号（TIN）或商业登记资料。',
            'zh-TW': '身分、聯絡及稅務資料：姓名、稱呼、電子郵箱、電話號碼、居住或帳單地址、國籍、性別、出生日期、入住登記時記錄的身分證（NRIC）或護照資訊、申報的旅客類型（用於核算旅遊稅的馬來西亞公民/永久居民或外國旅客），以及在您要求開具LHDN驗證電子發票時提供的稅務識別號（TIN）或商業登記資料。',
          },
          {
            en: 'Booking and stay records: reservation numbers, dates, room allocation and rate plan, occupancy, special requests, housekeeping preferences, check-in/check-out timestamps, corporate ledger association (where your stay is company-billed), and booking change history.',
            ms: 'Rekod tempahan dan penginapan: nombor tempahan, tarikh, peruntukan bilik dan pelan kadar, bilangan penghuni, permintaan khas, pilihan pengemasan bilik, cap masa daftar masuk/keluar, kaitan lejar korporat (jika penginapan dibilkan kepada syarikat), serta sejarah perubahan tempahan.',
            zh: '预订及住宿记录：预订编号、入住与退房日期、分配客房与房价方案、入住人数、特殊要求、客房清洁偏好、入住/退房时间戳、公司挂账关联信息（如由公司结账）以及预订变更记录。',
            'zh-TW': '預訂及住宿記錄：預訂編號、入住與退房日期、分配客房與房價方案、入住人數、特殊要求、客房清潔偏好、入住/退房時間戳、公司掛帳關聯資訊（如由公司結帳）以及預訂變更記錄。',
          },
          {
            en: 'Payment and billing records: amount, currency, payment method and status, PayPal or bank-transfer transaction references, uploaded transfer receipts, and issued invoices or credit notes. We never receive or store your full payment card number or CVV — card and PayPal payments are captured directly by PayPal.',
            ms: 'Rekod pembayaran dan pengebilan: amaun, mata wang, kaedah dan status pembayaran, rujukan transaksi PayPal atau pindahan bank, resit pindahan yang dimuat naik, serta invois atau nota kredit yang dikeluarkan. Kami tidak pernah menerima atau menyimpan nombor penuh kad pembayaran atau kod CVV anda — pembayaran kad dan PayPal diproses terus oleh PayPal.',
            zh: '付款及账单记录：金额、币种、付款方式与状态、PayPal或银行转账参考编号、上传的转账凭证，以及开具的发票或贷项通知单。我们从不接收或存储您的完整银行卡号或安全码（CVV）——银行卡及PayPal付款均由PayPal直接采集处理。',
            'zh-TW': '付款及帳單記錄：金額、幣種、付款方式與狀態、PayPal或銀行轉帳參考編號、上傳的轉帳憑證，以及開具的發票或貸項通知單。我們從不接收或儲存您的完整銀行卡號或安全碼（CVV）——銀行卡及PayPal付款均由PayPal直接採集處理。',
          },
          {
            en: 'Online identity verification (eKYC) data, if you choose to use it: identity document metadata and images, optional proof of address, and a live selfie photograph for visual identity matching.',
            ms: 'Data pengesahan identiti dalam talian (eKYC), jika anda memilih untuk menggunakannya: metadata dan imej dokumen pengenalan, bukti alamat pilihan, serta gambar swafoto wajah bagi tujuan padanan identiti secara visual.',
            zh: '在线身份验证（eKYC）资料（如您选择使用）：身份证件元数据及影像、可选的住址证明，以及用于人工视觉核对身份的自拍面部照片。',
            'zh-TW': '線上身分驗證（eKYC）資料（如您選擇使用）：身分證件元數據及影像、可選的住址證明，以及用於人工視覺核對身分的自拍面部照片。',
          },
          {
            en: 'Account, authentication and consent records: username, cryptographic password hash (never plaintext), optional TOTP two-factor or WebAuthn passkey public-key credentials, sign-in timestamps, and the date, version, locale, IP address, and browser user-agent recorded when you grant or withdraw consent.',
            ms: 'Rekod akaun, pengesahan dan persetujuan: nama pengguna, cincangan kriptografi kata laluan (bukan teks biasa), kelayakan kunci awam pengesahan dua faktor TOTP atau kunci laluan WebAuthn pilihan, masa log masuk, serta tarikh, versi, bahasa, alamat IP dan ejen pengguna pelayar yang direkodkan apabila anda memberi atau menarik balik persetujuan.',
            zh: '账户、认证及同意记录：用户名、密码加密散列值（绝不保存明文）、可选的TOTP双重验证或WebAuthn通行密钥公钥凭证、登录时间，以及您授予或撤回同意时记录的日期、版本、语言、IP地址与浏览器标识。',
            'zh-TW': '帳戶、認證及同意記錄：用戶名、密碼加密雜湊值（絕不保存明文）、可選的TOTP雙重驗證或WebAuthn通行金鑰公鑰憑證、登入時間，以及您授予或撤回同意時記錄的日期、版本、語言、IP位址與瀏覽器標識。',
          },
          {
            en: 'On-premises CCTV footage and correspondence: 24-hour closed-circuit television (CCTV) video images recorded without audio in public and common areas of the hotel (entrance canopy, parking bays, lobby, reception, stairways, and corridors — never inside guest rooms or bathrooms) for safety and crime prevention, together with messages sent via support chat, email, or telephone.',
            ms: 'Rakaman CCTV di premis dan surat-menyurat: imej video televisyen litar tertutup (CCTV) 24 jam tanpa audio yang dirakam di kawasan awam dan guna sama hotel (kanopi pintu masuk, petak parkir, lobi, kaunter penyambut tetamu, tangga dan koridor — tidak sekali-kali di dalam bilik tetamu atau bilik mandi) bagi tujuan keselamatan dan pencegahan jenayah, berserta mesej melalui sembang sokongan, e-mel atau telefon.',
            zh: '场所闭路电视监控（CCTV）及往来通信：为保障安全与防范犯罪，在酒店公共及共用区域（门前雨棚、停车位、大堂、接待台、楼梯间及走廊——绝不在客房或卫浴内）运行的24小时无录音闭路电视监控画面，以及您通过在线客服、电子邮件或电话与我们的往来沟通记录。',
            'zh-TW': '場所閉路電視監控（CCTV）及往來通信：為保障安全與防範犯罪，在酒店公共及共用區域（門前雨棚、停車位、大堂、接待台、樓梯間及走廊——絕不在客房或衛浴內）運行的24小時無錄音閉路電視監控畫面，以及您透過線上客服、電子郵件或電話與我們的往來溝通記錄。',
          },
          {
            en: 'Accompanying minors (under 18): We do not knowingly accept online bookings directly from persons under 18. Where personal data of an accompanying minor under 18 is provided for guest registration, you confirm as their parent or legal guardian that you consent to its processing in accordance with Regulation 4 of the Personal Data Protection Regulations 2013.',
            ms: 'Kanak-kanak bawah umur yang mengiringi (bawah 18 tahun): Kami tidak menerima tempahan dalam talian secara langsung daripada individu berumur di bawah 18 tahun. Sekiranya data peribadi kanak-kanak bawah 18 tahun diberikan bagi tujuan pendaftaran tetamu, anda mengesahkan sebagai ibu bapa atau penjaga sah bahawa anda bersetuju terhadap pemprosesannya selaras dengan Peraturan 4 Peraturan-Peraturan Perlindungan Data Peribadi 2013.',
            zh: '随行未成年人（未满18周岁）：我们不接受未满18周岁人士直接在线预订。如需为入住登记提供未满18周岁随行未成年人的个人资料，即表示您作为其父母或法定监护人，依《2013年个人资料保护条例》第4条同意我们处理该资料。',
            'zh-TW': '隨行未成年人（未滿18週歲）：我們不接受未滿18週歲人士直接線上預訂。如需為入住登記提供未滿18週歲隨行未成年人的個人資料，即表示您作為其父母或法定監護人，依《2013年個人資料保護條例》第4條同意我們處理該資料。',
          },
        ],
      },
      {
        id: 'sensitive-data',
        heading: {
          en: '3. Sensitive personal data (biometric data)',
          ms: '3. Data peribadi sensitif (data biometrik)',
          zh: '3. 敏感个人资料（生物识别资料）',
          'zh-TW': '3. 敏感個人資料（生物識別資料）',
        },
        body: [
          {
            en: 'Under sections 4 and 40 of the Personal Data Protection Act 2010 (as amended by the Personal Data Protection (Amendment) Act 2024), biometric data — including a facial photograph captured for technical or identity verification — is classified as sensitive personal data. We process online eKYC facial images only when you give separate, explicit consent via our Identity Verification Consent Notice, which you are free to decline. Online identity verification is optional and is never required to book or stay with us.',
            ms: 'Di bawah seksyen 4 dan 40 Akta Perlindungan Data Peribadi 2010 (sebagaimana dipinda oleh Akta Perlindungan Data Peribadi (Pindaan) 2024), data biometrik — termasuk gambar wajah yang diambil bagi pengesahan teknikal atau identiti — diklasifikasikan sebagai data peribadi sensitif. Kami hanya memproses imej wajah eKYC dalam talian apabila anda memberikan persetujuan nyata yang berasingan melalui Notis Persetujuan Pengesahan Identiti kami, yang bebas untuk anda tolak. Pengesahan identiti dalam talian adalah pilihan dan bukan syarat untuk menempah atau menginap.',
            zh: '依经《2024年个人资料保护（修正）法令》修订的《2010年个人资料保护法令》第4条及第40条，生物识别资料（包括用于技术或身份核验而采集的面部照片）属敏感个人资料。仅在您通过《身份验证同意通知》单独作出明确同意时，我们才会处理在线eKYC面部影像，您有权拒绝。在线身份验证纯属自愿，绝非预订或入住的前提条件。',
            'zh-TW': '依經《2024年個人資料保護（修正）法令》修訂的《2010年個人資料保護法令》第4條及第40條，生物識別資料（包括用於技術或身分核驗而採集的面部照片）屬敏感個人資料。僅在您透過《身分驗證同意通知》單獨作出明確同意時，我們才會處理線上eKYC面部影像，您有權拒絕。線上身分驗證純屬自願，絕非預訂或入住的前提條件。',
          },
          {
            en: 'Authorised staff inspect submitted images solely to confirm that the person presenting the identity document matches the document holder. We do not run automated biometric profiling, do not use facial images to train artificial-intelligence models, and never share them for marketing.',
            ms: 'Kakitangan yang diberi kuasa menyemak imej yang dihantar semata-mata untuk mengesahkan bahawa individu yang mengemukakan dokumen pengenalan sepadan dengan pemegang dokumen tersebut. Kami tidak menjalankan pemprofilan biometrik automatik, tidak menggunakan imej wajah untuk melatih model kecerdasan buatan, dan tidak pernah berkongsinya untuk tujuan pemasaran.',
            zh: '授权员工仅通过人工查验确认出示证件者与证件持有人一致。我们不进行自动化生物特征画像分析，不使用面部影像训练任何人工智能模型，也绝不将其用于营销共享。',
            'zh-TW': '授權員工僅透過人工查驗確認出示證件者與證件持有人一致。我們不進行自動化生物特徵畫像分析，不使用面部影像訓練任何人工智慧模型，也絕不將其用於行銷共享。',
          },
        ],
      },
      {
        id: 'where-it-comes-from',
        heading: {
          en: '4. Sources of personal data',
          ms: '4. Sumber data peribadi',
          zh: '4. 个人资料的来源',
          'zh-TW': '4. 個人資料的來源',
        },
        body: [
          {
            en: 'Most personal data comes directly from you when you search, book, pay, verify your identity, check in at reception, or contact us. Where a reservation is placed through an online travel agency (OTA), travel agent, or your employer/corporate account, we receive your booking particulars from them. Payment statuses and references come from PayPal or banking channels, and common-area CCTV footage is captured by clearly signed cameras on hotel premises.',
            ms: 'Kebanyakan data peribadi datang terus daripada anda semasa anda membuat carian, menempah, membayar, mengesahkan identiti, mendaftar masuk di kaunter, atau menghubungi kami. Jika tempahan dibuat melalui agensi pelancongan dalam talian (OTA), ejen pelancongan, atau majikan/akaun korporat anda, kami menerima butiran tempahan daripada pihak tersebut. Status dan rujukan pembayaran diterima daripada PayPal atau saluran perbankan, manakala rakaman CCTV kawasan awam dirakam oleh kamera bertanda jelas di premis hotel.',
            zh: '大部分个人资料由您在查询、预订、付款、验证身份、前台登记入住或联系我们时直接提供。若您的预订经由在线旅游平台（OTA）、旅行社或您的雇主/公司账户作出，我们自该方接收相关预订信息。付款状态与交易参考号来自PayPal或银行渠道，公共区域监控画面则由场所内设有明显标识的摄像设备采集。',
            'zh-TW': '大部分個人資料由您在查詢、預訂、付款、驗證身分、前台登記入住或聯絡我們時直接提供。若您的預訂經由線上旅遊平台（OTA）、旅行社或您的僱主/公司帳戶作出，我們自該方接收相關預訂資訊。付款狀態與交易參考號來自PayPal或銀行渠道，公共區域監控畫面則由場所內設有明顯標識的攝像設備採集。',
          },
        ],
      },
      {
        id: 'why-we-use-it',
        heading: {
          en: '5. Purposes of processing',
          ms: '5. Tujuan pemprosesan',
          zh: '5. 处理个人资料的目的',
          'zh-TW': '5. 處理個人資料的目的',
        },
        bullets: [
          {
            en: 'To create, confirm, modify, or cancel reservations and communicate transactional updates about your stay.',
            ms: 'Untuk membuat, mengesahkan, mengubah atau membatalkan tempahan serta menghantar makluman transaksi berkaitan penginapan anda.',
            zh: '创建、确认、更改或取消预订，并就您的住宿发送交易性通知。',
            'zh-TW': '建立、確認、更改或取消預訂，並就您的住宿發送交易性通知。',
          },
          {
            en: 'To process payments and refunds, manage corporate city-ledger accounts, issue receipts and LHDN e-Invoices where applicable, and resolve billing or payment disputes.',
            ms: 'Untuk memproses bayaran dan bayaran balik, mengurus akaun lejar bandar korporat, mengeluarkan resit dan e-Invois LHDN jika berkaitan, serta menyelesaikan pertikaian pengebilan atau pembayaran.',
            zh: '处理收款与退款、管理企业挂账账户、按规定开具收据及LHDN电子发票，并处理账单或付款争议。',
            'zh-TW': '處理收款與退款、管理企業掛帳帳戶、按規定開具收據及LHDN電子發票，並處理帳單或付款爭議。',
          },
          {
            en: 'To host your stay: allocate rooms, coordinate housekeeping and maintenance, and fulfil guest requests.',
            ms: 'Untuk menguruskan penginapan anda: memperuntukkan bilik, menyelaras pengemasan dan penyelenggaraan, serta memenuhi permintaan tetamu.',
            zh: '安排住宿接待：分配客房、协调客房清洁与维修，并响应宾客需求。',
            'zh-TW': '安排住宿接待：分配客房、協調客房清潔與維修，並響應賓客需求。',
          },
          {
            en: 'To comply with statutory duties in Malaysia and Sarawak: maintaining the mandatory guest register under the Registration of Guests Act 1965 (Act 381) and Tourism Industry Act 1992 (Act 482), assessing and remitting Tourism Tax (Act 791) and Service Tax (Act 807), retaining tax and accounting records under the Income Tax Act 1967 (Act 53), and responding to lawful requests from enforcement authorities.',
            ms: 'Untuk mematuhi kewajipan berkanun di Malaysia dan Sarawak: menyelenggara daftar tetamu wajib di bawah Akta Pendaftaran Tetamu 1965 (Akta 381) dan Akta Industri Pelancongan 1992 (Akta 482), menaksir dan meremitkan Cukai Pelancongan (Akta 791) serta Cukai Perkhidmatan (Akta 807), menyimpan rekod cukai dan perakaunan di bawah Akta Cukai Pendapatan 1967 (Akta 53), serta memenuhi permintaan sah pihak berkuasa penguatkuasaan.',
            zh: '履行马来西亚及砂拉越法定义务：依《1965年住客登记法令》（第381号法令）及《1992年旅游业法令》（第482号法令）备置住客登记册，依《2017年旅游税法令》（第791号法令）及《2018年服务税法令》（第807号法令）核算申报税款，依《1967年所得税法令》（第53号法令）保存财税记录，并配合执法机关的合法调取要求。',
            'zh-TW': '履行馬來西亞及砂拉越法定義務：依《1965年住客登記法令》（第381號法令）及《1992年旅遊業法令》（第482號法令）備置住客登記冊，依《2017年旅遊稅法令》（第791號法令）及《2018年服務稅法令》（第807號法令）核算申報稅款，依《1967年所得稅法令》（第53號法令）保存財稅記錄，並配合執法機關的合法調取要求。',
          },
          {
            en: 'To safeguard guests, staff, property, and IT systems (including common-area CCTV monitoring, access logging, fraud prevention, and exercising or defending legal claims).',
            ms: 'Untuk melindungi keselamatan tetamu, kakitangan, harta benda dan sistem IT (termasuk pemantauan CCTV kawasan awam, pengelogan capaian, pencegahan penipuan, serta melaksanakan atau membela tuntutan undang-undang).',
            zh: '保障宾客、员工、财物及信息系统安全（包括公共区域CCTV监控、访问审计日志、反欺诈，以及主张或抗辩法律权利）。',
            'zh-TW': '保障賓客、員工、財物及資訊系統安全（包括公共區域CCTV監控、存取審計日誌、反欺詐，以及主張或抗辯法律權利）。',
          },
          {
            en: 'To administer our guest loyalty programme where you hold an account, and — strictly where you have separately opted in — to send promotional offers, announcements, or birthday vouchers until you unsubscribe.',
            ms: 'Untuk mentadbir program kesetiaan tetamu kami sekiranya anda mempunyai akaun, dan — hanya apabila anda telah memilih masuk secara berasingan — menghantar tawaran promosi, pengumuman atau baucar hari lahir sehingga anda berhenti melanggan.',
            zh: '在您持有账户时管理宾客忠诚度计划；且仅在您单独选择订阅的前提下，向您发送促销优惠、酒店公告或生日礼券，直至您退订为止。',
            'zh-TW': '在您持有帳戶時管理賓客忠誠度計劃；且僅在您單獨選擇訂閱的前提下，向您發送促銷優惠、酒店公告或生日禮券，直至您退訂為止。',
          },
        ],
      },
      {
        id: 'who-we-share-with',
        heading: {
          en: '6. Classes of third parties and data processors',
          ms: '6. Kelas pihak ketiga dan pemproses data',
          zh: '6. 披露对象类别与资料处理者',
          'zh-TW': '6. 披露對象類別與資料處理者',
        },
        body: [
          {
            en: 'We never sell your personal data. We disclose relevant personal data strictly on a need-to-know basis to the following classes of third parties:',
            ms: 'Kami tidak sekali-kali menjual data peribadi anda. Kami hanya mendedahkan data peribadi yang berkaitan mengikut keperluan kepada kelas pihak ketiga berikut:',
            zh: '我们绝不出售您的个人资料。我们仅在必要范围内向下列类别的第三方披露相关个人资料：',
            'zh-TW': '我們絕不出售您的個人資料。我們僅在必要範圍內向下列類別的第三方披露相關個人資料：',
          },
        ],
        bullets: [
          {
            en: 'Payment and banking institutions: PayPal (acting as an independent controller/processor for card and PayPal transactions under its own privacy statement) and licensed banks handling transfers and refunds.',
            ms: 'Institusi pembayaran dan perbankan: PayPal (bertindak sebagai pengawal/pemproses bebas bagi transaksi kad dan PayPal di bawah pernyataan privasinya sendiri) dan bank berlesen yang mengendalikan pindahan serta bayaran balik.',
            zh: '支付与银行机构：PayPal（依其自身隐私声明处理银行卡及PayPal交易）以及办理转账和退款的持牌银行。',
            'zh-TW': '支付與銀行機構：PayPal（依其自身隱私聲明處理銀行卡及PayPal交易）以及辦理轉帳和退款的持牌銀行。',
          },
          {
            en: 'Data processors (pemproses data): cloud infrastructure, backup, and transactional email delivery providers acting strictly on our instructions. Under section 9 of the amended Personal Data Protection Act 2010, our data processors are also directly bound by statutory security obligations in addition to our contractual safeguards.',
            ms: 'Pemproses data: penyedia infrastruktur awan, sandaran dan penghantaran e-mel transaksi yang bertindak mengikut arahan kami semata-mata. Di bawah seksyen 9 Akta Perlindungan Data Peribadi 2010 yang dipinda, pemproses data kami turut terikat secara langsung dengan kewajipan keselamatan berkanun selain perlindungan kontrak kami.',
            zh: '资料处理者（pemproses data）：严格按我们指示提供云托管、备份及交易邮件投递服务的技术供应商。依据修订后的《2010年个人资料保护法令》第9条，该等资料处理者除受我们的合同约束外，亦直接承担法定安全保护义务。',
            'zh-TW': '資料處理者（pemproses data）：嚴格按我們指示提供雲託管、備份及交易郵件投遞服務的技術供應商。依據修訂後的《2010年個人資料保護法令》第9條，該等資料處理者除受我們的合同約束外，亦直接承擔法定安全保護義務。',
          },
          {
            en: 'Booking channels and corporate accounts: online travel platforms or travel agents through whom you booked, or your employer/company where your stay is charged to a corporate account.',
            ms: 'Saluran tempahan dan akaun korporat: platform pelancongan dalam talian atau ejen pelancongan yang melaluinya anda menempah, atau majikan/syarikat anda sekiranya penginapan dicaj kepada akaun korporat.',
            zh: '预订渠道及企业账户：您预订所使用的在线旅游平台或旅行社，或为您承担挂账住宿费用的雇主/公司。',
            'zh-TW': '預訂渠道及企業帳戶：您預訂所使用的線上旅遊平台或旅行社，或為您承擔掛帳住宿費用的僱主/公司。',
          },
          {
            en: 'Government, tax and enforcement authorities: Royal Malaysia Police (PDRM), Royal Malaysian Customs Department (RMCD), Inland Revenue Board of Malaysia (LHDN), Ministry of Tourism, Arts and Culture (MOTAC), Sibu local authorities, and the Personal Data Protection Commissioner (JPDP), where required or authorised by law.',
            ms: 'Pihak berkuasa kerajaan, cukai dan penguatkuasaan: Polis Diraja Malaysia (PDRM), Jabatan Kastam Diraja Malaysia (JKDM), Lembaga Hasil Dalam Negeri Malaysia (LHDN), Kementerian Pelancongan, Seni dan Budaya (MOTAC), pihak berkuasa tempatan Sibu, dan Pesuruhjaya Perlindungan Data Peribadi (JPDP), apabila dikehendaki atau dibenarkan oleh undang-undang.',
            zh: '政府、税务及执法机关：在法律要求或授权时，包括马来西亚皇家警察（PDRM）、马来西亚皇家关税局（RMCD）、马来西亚内陆税收局（LHDN）、旅游、艺术及文化部（MOTAC）、诗巫地方当局及个人资料保护专员（JPDP）。',
            'zh-TW': '政府、稅務及執法機關：在法律要求或授權時，包括馬來西亞皇家警察（PDRM）、馬來西亞皇家關稅局（RMCD）、馬來西亞內陸稅收局（LHDN）、旅遊、藝術及文化部（MOTAC）、詩巫地方當局及個人資料保護專員（JPDP）。',
          },
          {
            en: 'Professional advisers: external accountants, auditors, insurers, and legal counsel bound by professional confidentiality.',
            ms: 'Penasihat profesional: akauntan luar, juruaudit, penanggung insurans dan peguam yang terikat dengan kerahsiaan profesional.',
            zh: '专业顾问：承担职业保密义务的外部会计师、审计师、保险机构及法律顾问。',
            'zh-TW': '專業顧問：承擔職業保密義務的外部會計師、審計師、保險機構及法律顧問。',
          },
        ],
      },
      {
        id: 'transfer-abroad',
        heading: {
          en: '7. Cross-border transfers outside Malaysia',
          ms: '7. Pemindahan rentas sempadan ke luar Malaysia',
          zh: '7. 向马来西亚境外的跨境传输',
          'zh-TW': '7. 向馬來西亞境外的跨境傳輸',
        },
        body: [
          {
            en: 'Certain service providers — notably PayPal and our email or cloud infrastructure providers — may process data in jurisdictions outside Malaysia. Whenever personal data is transferred outside Malaysia, we comply with section 129 of the Personal Data Protection Act 2010 (as amended in 2024) and JPDP Cross-Border Personal Data Transfer Guidelines: transfers take place only where the receiving jurisdiction has data protection laws substantially similar to Act 709 or ensures an adequate level of protection, where the transfer is necessary to perform your booking or payment transaction, where you have consented, or where we have exercised due diligence and put enforceable contractual and technical safeguards in place.',
            ms: 'Sesetengah penyedia perkhidmatan — khususnya PayPal serta penyedia infrastruktur e-mel atau awan kami — mungkin memproses data di bidang kuasa di luar Malaysia. Setiap kali data peribadi dipindahkan ke luar Malaysia, kami mematuhi seksyen 129 Akta Perlindungan Data Peribadi 2010 (sebagaimana dipinda pada 2024) dan Garis Panduan Pemindahan Data Peribadi Rentas Sempadan JPDP: pemindahan hanya dibuat apabila bidang kuasa penerima mempunyai undang-undang perlindungan data yang secara substansialnya setara dengan Akta 709 atau menjamin tahap perlindungan yang mencukupi, apabila pemindahan perlu bagi melaksanakan kontrak tempahan atau pembayaran anda, apabila anda telah memberi persetujuan, atau apabila kami telah melaksanakan usaha wajar serta perlindungan kontrak dan teknikal yang boleh dikuatkuasakan.',
            zh: '部分服务提供商（尤其是PayPal以及电子邮件或云基础设施服务商）可能在马来西亚境外司法管辖区处理资料。凡个人资料传出马来西亚境外的，我们均遵守经2024年修订的《2010年个人资料保护法令》第129条及个人资料保护局（JPDP）跨境传输指南：仅在接收地具备与第709号法令实质相当的法律或提供充分保护水平、为履行您的预订或付款交易合同所必需、经您同意，或我们已履行尽职调查并落实可执行合同与技术保障措施的情况下进行。',
            'zh-TW': '部分服務提供商（尤其是PayPal以及電子郵件或雲基礎設施服務商）可能在馬來西亞境外司法管轄區處理資料。凡個人資料傳出馬來西亞境外的，我們均遵守經2024年修訂的《2010年個人資料保護法令》第129條及個人資料保護局（JPDP）跨境傳輸指南：僅在接收地具備與第709號法令實質相當的法律或提供充分保護水平、為履行您的預訂或付款交易合同所必需、經您同意，或我們已履行盡職調查並落實可執行合同與技術保障措施的情況下進行。',
          },
        ],
      },
      {
        id: 'is-it-obligatory',
        heading: {
          en: '8. Obligatory versus voluntary data',
          ms: '8. Data wajib berbanding sukarela',
          zh: '8. 必须提供与自愿提供的资料',
          'zh-TW': '8. 必須提供與自願提供的資料',
        },
        emphasis: 'requirement',
        body: [
          {
            en: 'Providing your name, contact email, stay dates, and declared guest type is obligatory to make an online booking; on arrival, providing full name, nationality, address, and NRIC/passport identification is obligatory under the Registration of Guests Act 1965 and the Tourism Tax Act 2017 (and taxpayer particulars are obligatory if you request a validated LHDN e-Invoice). If you do not supply obligatory data, we cannot accept your reservation, complete check-in, or issue a validated tax e-Invoice.',
            ms: 'Pemberian nama, e-mel hubungan, tarikh penginapan dan jenis tetamu yang diisytiharkan adalah wajib untuk membuat tempahan dalam talian; semasa ketibaan, pemberian nama penuh, kewarganegaraan, alamat dan pengenalan NRIC/pasport adalah wajib di bawah Akta Pendaftaran Tetamu 1965 dan Akta Cukai Pelancongan 2017 (dan butiran pembayar cukai adalah wajib jika anda memohon e-Invois LHDN yang disahkan). Sekiranya anda tidak memberikan data wajib tersebut, kami tidak dapat menerima tempahan anda, melengkapkan daftar masuk, atau mengeluarkan e-Invois cukai yang disahkan.',
            zh: '在线预订必须提供姓名、联络邮箱、入住退房日期及旅客类型申报；到店入住时，依《1965年住客登记法令》及《2017年旅游税法令》必须提供全名、国籍、住址及身份证/护照信息（如您要求开具经验证的LHDN电子发票，亦须提供相应纳税人资料）。若您未能提供上述必须资料，我们将无法受理预订、办理入住或开具验证电子发票。',
            'zh-TW': '線上預訂必須提供姓名、聯絡郵箱、入住退房日期及旅客類型申報；到店入住時，依《1965年住客登記法令》及《2017年旅遊稅法令》必須提供全名、國籍、住址及身分證/護照資訊（如您要求開具經驗證的LHDN電子發票，亦須提供相應納稅人資料）。若您未能提供上述必須資料，我們將無法受理預訂、辦理入住或開具驗證電子發票。',
          },
          {
            en: 'All other data — including special requests, loyalty participation, promotional email subscriptions, and online eKYC identity verification — is voluntary. Declining optional items never affects your right to book, check in at reception, or pay our published rates.',
            ms: 'Semua data lain — termasuk permintaan khas, penyertaan kesetiaan, langganan e-mel promosi dan pengesahan identiti eKYC dalam talian — adalah sukarela. Menolak perkara pilihan tidak sekali-kali menjejaskan hak anda untuk menempah, mendaftar masuk di kaunter, atau menikmati kadar harga yang diterbitkan.',
            zh: '其余所有资料（包括特殊要求、忠诚计划参与、促销邮件订阅及在线eKYC身份验证）均属自愿提供。拒绝提供可选项目绝不会影响您预订、在前台办理入住或享受公布房价的权利。',
            'zh-TW': '其餘所有資料（包括特殊要求、忠誠計劃參與、促銷郵件訂閱及線上eKYC身分驗證）均屬自願提供。拒絕提供可選項目絕不會影響您預訂、在前台辦理入住或享受公佈房價的權利。',
          },
        ],
      },
      {
        id: 'your-choices',
        heading: {
          en: '9. Choices to limit processing and withdraw consent',
          ms: '9. Pilihan untuk menghadkan pemprosesan dan menarik balik persetujuan',
          zh: '9. 限制处理与撤回同意的选择',
          'zh-TW': '9. 限制處理與撤回同意的選擇',
        },
        bullets: [
          {
            en: 'Direct marketing (section 43): You may opt out of promotional emails, announcements, or birthday vouchers at any time via your guest portal preferences, the one-click unsubscribe link in every marketing email, or by writing to us.',
            ms: 'Pemasaran langsung (seksyen 43): Anda boleh memilih keluar daripada e-mel promosi, pengumuman atau baucar hari lahir pada bila-bila masa melalui tetapan portal tetamu anda, pautan berhenti melanggan satu klik dalam setiap e-mel pemasaran, atau dengan menulis kepada kami.',
            zh: '直接营销（第43条）：您可随时通过宾客门户偏好设置、每封营销邮件中的一键退订链接或联系我们，停止接收促销优惠、公告或生日礼券邮件。',
            'zh-TW': '直接行銷（第43條）：您可隨時透過賓客門戶偏好設置、每封行銷郵件中的一鍵退訂連結或聯絡我們，停止接收促銷優惠、公告或生日禮券郵件。',
          },
          {
            en: 'Withdrawal of consent (section 42): You may by written notice withdraw your consent to optional processing — including online eKYC biometric verification — and ask us to delete uploaded eKYC images unless statutory retention applies.',
            ms: 'Penarikan balik persetujuan (seksyen 42): Anda boleh melalui notis bertulis menarik balik persetujuan anda terhadap pemprosesan pilihan — termasuk pengesahan biometrik eKYC dalam talian — dan meminta kami memadamkan imej eKYC yang dimuat naik melainkan penyimpanan berkanun terpakai.',
            zh: '撤回同意（第42条）：您可通过书面通知撤回对可选处理项目（包括在线eKYC生物识别核验）的同意，并要求删除已上传的eKYC影像（法律规定必须保留的情形除外）。',
            'zh-TW': '撤回同意（第42條）：您可透過書面通知撤回對可選處理項目（包括線上eKYC生物識別核驗）的同意，並要求刪除已上傳的eKYC影像（法律規定必須保留的情形除外）。',
          },
          {
            en: 'Withdrawal does not affect the lawfulness of processing carried out before withdrawal, nor processing required to perform an existing booking contract or comply with a statutory obligation.',
            ms: 'Penarikan balik tidak menjejaskan kesahan pemprosesan yang telah dijalankan sebelum penarikan balik, mahupun pemprosesan yang diperlukan untuk melaksanakan kontrak tempahan sedia ada atau mematuhi kewajipan berkanun.',
            zh: '撤回同意不影响撤回前已依法进行的处理，亦不影响为履行既有预订合同或遵守法定义务所必需的处理。',
            'zh-TW': '撤回同意不影響撤回前已依法進行的處理，亦不影響為履行既有預訂合同或遵守法定義務所必需的處理。',
          },
        ],
      },
      {
        id: 'your-rights',
        heading: {
          en: '10. Your statutory rights (access, correction, portability) and complaints',
          ms: '10. Hak berkanun anda (akses, pembetulan, kemudahalihan) dan aduan',
          zh: '10. 您的法定权利（查阅、更正、可携权）与投诉途径',
          'zh-TW': '10. 您的法定權利（查閱、更正、可攜權）與投訴途徑',
        },
        emphasis: 'info',
        body: [
          {
            en: 'Under the Personal Data Protection Act 2010 (as amended by Act A1727), you have the right to: (a) request access to and a copy of your personal data (section 30); (b) request correction of inaccurate, incomplete, misleading, or outdated personal data (section 34); (c) withdraw consent (section 42) or prevent processing likely to cause unwarranted substantial damage or distress (section 38); and (d) request data portability — transmitting personal data you provided to another data controller in a structured, commonly used electronic format where technically feasible and compatible (section 43A).',
            ms: 'Di bawah Akta Perlindungan Data Peribadi 2010 (sebagaimana dipinda oleh Akta A1727), anda berhak untuk: (a) memohon akses dan salinan data peribadi anda (seksyen 30); (b) memohon pembetulan data peribadi yang tidak tepat, tidak lengkap, mengelirukan atau tidak terkini (seksyen 34); (c) menarik balik persetujuan (seksyen 42) atau menghalang pemprosesan yang berkemungkinan menyebabkan kerosakan atau tekanan ketara yang tidak wajar (seksyen 38); dan (d) memohon kemudahalihan data — menghantar data peribadi yang anda berikan kepada pengawal data lain dalam format elektronik berstruktur yang lazim digunakan sekiranya boleh dilaksanakan dari segi teknikal dan keserasian (seksyen 43A).',
            zh: '依据经第A1727号法令修订的《2010年个人资料保护法令》，您享有以下法定权利：（a）要求查阅并获取您的个人资料副本（第30条）；（b）要求更正不准确、不完整、具误导性或过时的个人资料（第34条）；（c）撤回同意（第42条）或要求停止可能造成不当重大损害或困扰的处理（第38条）；以及（d）行使资料可携权——在技术可行且格式兼容的前提下，要求将您提供的个人资料以结构化、常用的电子格式传输给另一资料控制者（第43A条）。',
            'zh-TW': '依據經第A1727號法令修訂的《2010年個人資料保護法令》，您享有以下法定權利：（a）要求查閱並獲取您的個人資料副本（第30條）；（b）要求更正不準確、不完整、具誤導性或過時的個人資料（第34條）；（c）撤回同意（第42條）或要求停止可能造成不當重大損害或困擾的處理（第38條）；以及（d）行使資料可攜權——在技術可行且格式相容的前提下，要求將您提供的個人資料以結構化、常用的電子格式傳輸給另一資料控制者（第43A條）。',
          },
          {
            en: `Submit requests to our Data Protection Contact at ${identity.dataProtectionContactEmail}. We verify requester identity before disclosing data and respond within twenty-one (21) days of receiving a complete request (or notify you in writing if up to fourteen (14) additional days are required, as permitted by sections 31 and 35 of the Act). Any fee for a data access request will not exceed the maximum prescribed under the Personal Data Protection (Fees) Regulations.`,
            ms: `Hantar permohonan kepada Hubungan Perlindungan Data kami di ${identity.dataProtectionContactEmail}. Kami mengesahkan identiti pemohon sebelum mendedahkan data dan memberi maklum balas dalam tempoh dua puluh satu (21) hari dari tarikh penerimaan permohonan lengkap (atau memaklumkan anda secara bertulis sekiranya sehingga empat belas (14) hari tambahan diperlukan, sebagaimana dibenarkan di bawah seksyen 31 dan 35 Akta). Sebarang fi bagi permohonan akses data tidak akan melebihi had maksimum yang ditetapkan di bawah Peraturan-Peraturan Perlindungan Data Peribadi (Fi).`,
            zh: `请将相关请求发送至我们的资料保护联络邮箱 ${identity.dataProtectionContactEmail}。我们将在核实申请人身份后，自收到完整请求之日起二十一（21）日内予以答复（如依该法令第31条及第35条需延长最多十四（14）日的，我们将书面通知您）。查阅资料请求如需收费，绝不超过《个人资料保护（费用）条例》规定的最高限额。`,
            'zh-TW': `請將相關請求發送至我們的資料保護聯絡郵箱 ${identity.dataProtectionContactEmail}。我們將在核實申請人身分後，自收到完整請求之日起二十一（21）日內予以答覆（如依該法令第31條及第35條需延長最多十四（14）日的，我們將書面通知您）。查閱資料請求如需收費，絕不超過《個人資料保護（費用）條例》規定的最高限額。`,
          },
          {
            en: 'If you remain dissatisfied with how we handle your personal data, you may lodge a complaint with the Personal Data Protection Commissioner (Pesuruhjaya Perlindungan Data Peribadi / Jabatan Perlindungan Data Peribadi Malaysia — JPDP, www.pdp.gov.my).',
            ms: 'Sekiranya anda masih tidak berpuas hati dengan cara kami mengendalikan data peribadi anda, anda boleh mengemukakan aduan kepada Pesuruhjaya Perlindungan Data Peribadi (Jabatan Perlindungan Data Peribadi Malaysia — JPDP, www.pdp.gov.my).',
            zh: '如您对我们处理个人资料的方式仍不满意，可向马来西亚个人资料保护专员（Jabatan Perlindungan Data Peribadi Malaysia — JPDP，网址：www.pdp.gov.my）提出投诉。',
            'zh-TW': '如您對我們處理個人資料的方式仍不滿意，可向馬來西亞個人資料保護專員（Jabatan Perlindungan Data Peribadi Malaysia — JPDP，網址：www.pdp.gov.my）提出投訴。',
          },
        ],
      },
      {
        id: 'retention',
        heading: {
          en: '11. Data retention (section 10)',
          ms: '11. Penyimpanan data (seksyen 10)',
          zh: '11. 资料保留期限（第10条）',
          'zh-TW': '11. 資料保留期限（第10條）',
        },
        body: [
          {
            en: 'In accordance with the Retention Principle under section 10 of the Act, personal data is kept no longer than necessary for its purpose and applicable statutory duties: financial, tax, invoice, and booking ledger records are retained for seven (7) years under section 82 of the Income Tax Act 1967 and Malaysian business-record laws; statutory guest-register entries are kept for the period required under the Registration of Guests Act 1965; eKYC identity and selfie images are held in restricted storage for verification and stay-related legal compliance and are purged upon valid post-stay withdrawal/erasure request or retention review while keeping only the pass/fail verification outcome; common-area CCTV recordings overwrite automatically on a short rolling cycle unless preserved for an active security or police investigation; and expired or revoked sign-in sessions are purged automatically.',
            ms: 'Selaras dengan Prinsip Penyimpanan di bawah seksyen 10 Akta, data peribadi tidak disimpan lebih lama daripada yang diperlukan bagi tujuannya dan kewajipan berkanun yang berkaitan: rekod kewangan, cukai, invois dan lejar tempahan disimpan selama tujuh (7) tahun di bawah seksyen 82 Akta Cukai Pendapatan 1967 dan undang-undang rekod perniagaan Malaysia; catatan daftar tetamu berkanun disimpan bagi tempoh yang dikehendaki di bawah Akta Pendaftaran Tetamu 1965; imej dokumen dan swafoto eKYC disimpan dalam storan terhad bagi pengesahan serta pematuhan undang-undang penginapan dan dipadamkan atas permohonan penarikan balik/pemadaman selepas penginapan yang sah atau semakan penyimpanan dengan hanya mengekalkan keputusan lulus/gagal; rakaman CCTV kawasan awam ditulis ganti secara automatik mengikut kitaran pendek melainkan disimpan bagi siasatan keselamatan atau polis yang aktif; dan sesi log masuk yang tamat tempoh atau dibatalkan dipadamkan secara automatik.',
            zh: '依据该法令第10条保留原则，个人资料的保存时间不超过实现其收集目的及履行法定义务所需的期限：财务、税务、发票及预订账册记录依《1967年所得税法令》第82条及马来西亚商业档案法规保存七（7）年；法定住客登记记录按《1965年住客登记法令》要求期限保存；eKYC证件及自拍影像保存在受限存储区以供核验及住宿相关法定合规之用，并在收到有效的离店后撤回/删除申请或经保留期审查后清除影像文件（仅保留通过/未通过的审核结论）；公共区域CCTV监控录像按短期循环自动覆盖（因正在进行的安全事件或警方调查而依法保全者除外）；过期或已注销的登录会话则自动清理。',
            'zh-TW': '依據該法令第10條保留原則，個人資料的保存時間不超過實現其收集目的及履行法定義務所需的期限：財務、稅務、發票及預訂帳冊記錄依《1967年所得稅法令》第82條及馬來西亞商業檔案法規保存七（7）年；法定住客登記記錄按《1965年住客登記法令》要求期限保存；eKYC證件與自拍影像保存在受限儲存區以供核驗及住宿相關法定合規之用，並在收到有效的離店後撤回/刪除申請或經保留期審查後清除影像文件（僅保留通過/未通過的審核結論）；公共區域CCTV監控錄像按短期循環自動覆蓋（因正在進行的安全事件或警方調查而依法保全者除外）；過期或已註銷的登入工作階段則自動清理。',
          },
        ],
      },
      {
        id: 'security',
        heading: {
          en: '12. Security safeguards and data breach notification (sections 9 and 12B)',
          ms: '12. Perlindungan keselamatan dan pemberitahuan pelanggaran data (seksyen 9 dan 12B)',
          zh: '12. 安全保障措施与资料泄露通知（第9条及第12B条）',
          'zh-TW': '12. 安全保障措施與資料洩露通知（第9條及第12B條）',
        },
        body: [
          {
            en: 'We protect personal data through technical and organisational controls under section 9 of the Act: TLS-encrypted transport, strict role-based access control (RBAC) for staff, append-only audit logs recording record access and changes, cryptographic password hashing, HttpOnly session tokens, and optional TOTP two-factor authentication and WebAuthn passkeys.',
            ms: 'Kami melindungi data peribadi melalui kawalan teknikal dan organisasi di bawah seksyen 9 Akta: penghantaran tersulit TLS, kawalan capaian berasaskan peranan (RBAC) yang ketat untuk kakitangan, log audit tambah-sahaja yang merekodkan capaian dan perubahan rekod, cincangan kata laluan kriptografi, token sesi HttpOnly, serta pengesahan dua faktor TOTP dan kunci laluan WebAuthn pilihan.',
            zh: '我们依该法令第9条采取技术与组织安全控制保护个人资料：TLS传输加密、严格的员工基于角色访问控制（RBAC）、防篡改追加式访问与变更审计日志、密码加密散列存储、HttpOnly会话令牌，以及可选的TOTP双重验证与WebAuthn通行密钥。',
            'zh-TW': '我們依該法令第9條採取技術與組織安全控制保護個人資料：TLS傳輸加密、嚴格的員工基於角色存取控制（RBAC）、防篡改追加式存取與變更審計日誌、密碼加密雜湊儲存、HttpOnly工作階段權杖，以及可選的TOTP雙重驗證與WebAuthn通行金鑰。',
          },
          {
            en: 'In compliance with section 12B of the Personal Data Protection Act 2010 and JPDP Data Breach Notification Guidelines, if we have reason to believe a personal data breach has occurred that causes or is likely to cause significant harm, we will notify the Personal Data Protection Commissioner as soon as practicable and within seventy-two (72) hours, and will notify affected individuals without unnecessary delay (within seven (7) days of notifying the Commissioner) with guidance on protective steps.',
            ms: 'Mematuhi seksyen 12B Akta Perlindungan Data Peribadi 2010 dan Garis Panduan Pemberitahuan Pelanggaran Data JPDP, sekiranya kami mempunyai alasan untuk mempercayai telah berlaku pelanggaran data peribadi yang menyebabkan atau berkemungkinan menyebabkan kemudaratan ketara, kami akan memaklumkan Pesuruhjaya Perlindungan Data Peribadi secepat yang dapat dilaksanakan dan dalam tempoh tujuh puluh dua (72) jam, serta memaklumkan individu yang terjejas tanpa kelengahan yang tidak perlu (dalam tempoh tujuh (7) hari selepas memaklumkan Pesuruhjaya) berserta panduan langkah perlindungan.',
            zh: '依据《2010年个人资料保护法令》第12B条及JPDP个人资料泄露通知指南，若我们有理由相信发生了造成或可能造成重大损害的个人资料泄露事件，我们将尽快且在七十二（72）小时内向个人资料保护专员报告，并在无不当延误的情况下（向专员报告后七（7）日内）通知受影响的个人并提供防护建议。',
            'zh-TW': '依據《2010年個人資料保護法令》第12B條及JPDP個人資料洩露通知指南，若我們有理由相信發生了造成或可能造成重大損害的個人資料洩露事件，我們將盡快且在七十二（72）小時內向個人資料保護專員報告，並在無不當延誤的情況下（向專員報告後七（7）日內）通知受影響的個人並提供防護建議。',
          },
        ],
      },
      {
        id: 'cookies',
        heading: {
          en: '13. Cookies and browser storage',
          ms: '13. Kuki dan storan pelayar',
          zh: '13. Cookies与浏览器存储',
          'zh-TW': '13. Cookies與瀏覽器儲存',
        },
        body: [
          {
            en: 'We use strictly necessary cookies and local browser storage to maintain your sign-in session, secure your active booking flow, and remember interface preferences such as language and display theme. We do not use third-party advertising trackers or cross-site behavioural profiling cookies. See our Cookie Usage page for full details.',
            ms: 'Kami menggunakan kuki yang amat diperlukan serta storan pelayar setempat untuk mengekalkan sesi log masuk anda, melindungi aliran tempahan aktif anda, dan mengingati keutamaan antara muka seperti bahasa serta tema paparan. Kami tidak menggunakan penjejak pengiklanan pihak ketiga atau kuki pemprofilan tingkah laku merentas laman. Lihat halaman Penggunaan Kuki kami untuk butiran penuh.',
            zh: '我们使用严格必要的Cookie和浏览器本地存储以维持您的登录会话、保障预订流程安全并记住语言及显示主题等界面偏好。我们不使用第三方广告追踪器或跨站行为画像Cookie。详情请参阅《Cookie 使用说明》页面。',
            'zh-TW': '我們使用嚴格必要的Cookie和瀏覽器本地儲存以維持您的登入工作階段、保障預訂流程安全並記住語言及顯示主題等介面偏好。我們不使用第三方廣告追蹤器或跨站行為畫像Cookie。詳情請參閱《Cookie 使用說明》頁面。',
          },
        ],
      },
      {
        id: 'changes',
        heading: {
          en: '14. Updates to this notice',
          ms: '14. Kemas kini pada notis ini',
          zh: '14. 本通知的更新',
          'zh-TW': '14. 本通知的更新',
        },
        body: [
          {
            en: 'Whenever we update this Privacy Notice we publish the revised text here with a new version date. Where an update materially expands how we process personal data you have already supplied, we will notify you and obtain fresh consent where required by the Act.',
            ms: 'Setiap kali kami mengemas kini Notis Privasi ini, kami menerbitkan teks semakan di sini dengan tarikh versi baharu. Sekiranya sesuatu kemas kini memperluas secara material cara kami memproses data peribadi yang telah anda berikan, kami akan memaklumkan anda dan mendapatkan persetujuan baharu apabila dikehendaki oleh Akta.',
            zh: '每当我们更新本《隐私通知》时，均会在此发布修订文本并标明新的版本日期。若更新内容实质性扩大了对您已提供个人资料的处理范围，我们将通知您并在法令要求时重新征得您的同意。',
            'zh-TW': '每當我們更新本《隱私通知》時，均會在此發布修訂文本並標明新的版本日期。若更新內容實質性擴大了對您已提供個人資料的處理範圍，我們將通知您並在法令要求時重新徵得您的同意。',
          },
        ],
      },
    ],
  };
};

export const privacyNotice: LegalDocument = buildPrivacyNotice();
