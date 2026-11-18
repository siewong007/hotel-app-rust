import { HOTEL_LEGAL_IDENTITY, type HotelLegalIdentity } from './hotelIdentity';
import type { LegalDocument, LocalizedText } from './types';

/**
 * Explicit consent for identity verification (eKYC).
 *
 * The eKYC flow captures an identity document AND a live selfie. Under sections
 * 4 and 40 of the Personal Data Protection Act 2010 (Act 709), as amended by
 * the Personal Data Protection (Amendment) Act 2024 (Act A1727, effective 1
 * April 2025), biometric data is classified as SENSITIVE personal data and
 * requires the data subject's EXPLICIT consent. That is why this is a separate
 * document with its own `consent_records` entry, never pre-ticked and never
 * bundled with Booking Terms.
 */
export const EKYC_CONSENT_VERSION = '2026-11-18';

/** Points shown inline above the eKYC consent checkbox. */
export const EKYC_KEY_POINTS: LocalizedText[] = [
  {
    en: 'You are about to upload an identity document and a facial photograph (selfie). Under sections 4 and 40 of the Personal Data Protection Act 2010 (as amended in 2024), facial biometric data is sensitive personal data.',
    ms: 'Anda akan memuat naik dokumen pengenalan dan gambar wajah (swafoto). Di bawah seksyen 4 dan 40 Akta Perlindungan Data Peribadi 2010 (sebagaimana dipinda pada 2024), data biometrik wajah adalah data peribadi sensitif.',
    zh: '您即将上传身份证件及面部照片（自拍）。依据经2024年修订的《2010年个人资料保护法令》第4条及第40条，面部生物识别资料属敏感个人资料。',
    'zh-TW': '您即將上傳身分證件及面部照片（自拍）。依據經2024年修訂的《2010年個人資料保護法令》第4條及第40條，面部生物識別資料屬敏感個人資料。',
  },
  {
    en: 'Authorised reviewers use it solely to confirm that you match the person on the document. We do not use automated facial-recognition profiling, advertising, or AI model training.',
    ms: 'Penyemak yang diberi kuasa menggunakannya semata-mata untuk mengesahkan bahawa anda adalah individu pada dokumen tersebut. Kami tidak menggunakan pemprofilan pengecaman wajah automatik, pengiklanan, atau latihan model AI.',
    zh: '授权审核人员仅将其用于核实您与证件所示持有人一致，绝不用于自动化人脸识别画像、广告营销或训练任何AI模型。',
    'zh-TW': '授權審核人員僅將其用於核實您與證件所示持有人一致，絕不用於自動化人臉識別畫像、廣告行銷或訓練任何AI模型。',
  },
  {
    en: 'Online verification is completely optional. You may decline and present your NRIC or passport in person at reception instead, and you may withdraw consent in writing at any time.',
    ms: 'Pengesahan dalam talian adalah pilihan sepenuhnya. Anda boleh menolak dan mengemukakan NRIC atau pasport secara bersemuka di kaunter penyambut tetamu, serta menarik balik persetujuan secara bertulis pada bila-bila masa.',
    zh: '在线身份验证完全自愿。您可拒绝并改于到店时在前台出示身份证或护照原件，亦可随时书面撤回本同意。',
    'zh-TW': '線上身分驗證完全自願。您可拒絕並改於到店時在前台出示身分證或護照原件，亦可隨時書面撤回本同意。',
  },
];

export const buildEkycConsent = (
  identity: HotelLegalIdentity = HOTEL_LEGAL_IDENTITY
): LegalDocument => ({
  id: 'ekyc_biometric',
  version: EKYC_CONSENT_VERSION,
  effectiveDate: '2026-11-18',
  title: {
    en: 'Identity Verification — Explicit Consent to Process Sensitive Personal Data',
    ms: 'Pengesahan Identiti — Persetujuan Nyata Memproses Data Peribadi Sensitif',
    zh: '身份验证——处理敏感个人资料的明确同意',
    'zh-TW': '身分驗證——處理敏感個人資料的明確同意',
  },
  summary: {
    en: `Online pre-arrival identity verification asks for an image of your identity document and a photograph of your face. Because facial biometric data is classified as sensitive personal data under sections 4 and 40 of the Personal Data Protection Act 2010 (Act 709, as amended by Act A1727), ${identity.tradingName} processes it only with your separate, explicit consent. This notice sets out exactly how it is handled.`,
    ms: `Pengesahan identiti dalam talian sebelum ketibaan meminta imej dokumen pengenalan dan gambar wajah anda. Oleh sebab data biometrik wajah diklasifikasikan sebagai data peribadi sensitif di bawah seksyen 4 dan 40 Akta Perlindungan Data Peribadi 2010 (Akta 709, sebagaimana dipinda oleh Akta A1727), ${identity.tradingName} hanya memprosesnya dengan persetujuan nyata anda yang berasingan. Notis ini menerangkan dengan tepat cara ia dikendalikan.`,
    zh: `在线入住前身份验证需您提供身份证件影像及面部自拍照片。由于面部生物识别资料依经第A1727号法令修订的《2010年个人资料保护法令》（第709号法令）第4条及第40条属敏感个人资料，${identity.tradingName}仅在取得您单独的明确同意后方予处理。本通知准确说明其处理规则。`,
    'zh-TW': `線上入住前身分驗證需您提供身分證件影像及面部自拍照片。由於面部生物識別資料依經第A1727號法令修訂的《2010年個人資料保護法令》（第709號法令）第4條及第40條屬敏感個人資料，${identity.tradingName}僅在取得您單獨的明確同意後方予處理。本通知準確說明其處理規則。`,
  },
  sections: [
    {
      id: 'what-we-take',
      heading: {
        en: '1. What data is collected',
        ms: '1. Data yang dikumpul',
        zh: '1. 收集的资料内容',
        'zh-TW': '1. 收集的資料內容',
      },
      emphasis: 'requirement',
      bullets: [
        {
          en: 'Identity document particulars: document type (MyKad, MyPR, or passport), document number, issuing country, and issue/expiry dates.',
          ms: 'Butiran dokumen pengenalan: jenis dokumen (MyKad, MyPR atau pasport), nombor dokumen, negara pengeluaran, serta tarikh dikeluarkan dan tarikh luput.',
          zh: '身份证件资料：证件类型（MyKad、MyPR或护照）、证件号码、签发国家，以及签发与到期日期。',
          'zh-TW': '身分證件資料：證件類型（MyKad、MyPR或護照）、證件號碼、簽發國家，以及簽發與到期日期。',
        },
        {
          en: 'Document images: a photograph or scan of the front of the document, and the reverse side where applicable.',
          ms: 'Imej dokumen: gambar atau imbasan bahagian hadapan dokumen, serta bahagian belakang jika berkaitan.',
          zh: '证件影像：证件正面的清晰照片或扫描件；证件有背面的，亦包括背面影像。',
          'zh-TW': '證件影像：證件正面的清晰照片或掃描件；證件有背面的，亦包括背面影像。',
        },
        {
          en: 'Facial image (selfie): a photograph of your face used by authorised reviewers to compare visually against the portrait on your identity document.',
          ms: 'Imej wajah (swafoto): gambar wajah anda yang digunakan oleh penyemak yang diberi kuasa untuk perbandingan visual dengan potret pada dokumen pengenalan anda.',
          zh: '面部影像（自拍）：您的面部照片，供授权审核人员与身份证件上的头像进行人工视觉比对。',
          'zh-TW': '面部影像（自拍）：您的面部照片，供授權審核人員與身分證件上的頭像進行人工視覺比對。',
        },
        {
          en: 'Optional proof of address, only if you choose to submit one.',
          ms: 'Bukti alamat pilihan, hanya jika anda memilih untuk mengemukakannya.',
          zh: '可选的住址证明文件（仅在您主动选择提交时收集）。',
          'zh-TW': '可選的住址證明文件（僅在您主動選擇提交時收集）。',
        },
      ],
    },
    {
      id: 'purpose',
      heading: {
        en: '2. Strictly limited purpose',
        ms: '2. Tujuan yang dihadkan secara ketat',
        zh: '2. 严格限定的处理目的',
        'zh-TW': '2. 嚴格限定的處理目的',
      },
      body: [
        {
          en: 'We process this data solely to verify your identity prior to check-in (including enabling scheduled express or automatic check-in where configured), to fulfil our statutory guest-registration duties under the Registration of Guests Act 1965 (Act 381) and Tourism Tax verification under the Tourism Tax Act 2017 (Act 791), and to prevent booking fraud or identity misuse.',
          ms: 'Kami memproses data ini semata-mata untuk mengesahkan identiti anda sebelum daftar masuk (termasuk membolehkan daftar masuk ekspres atau automatik jika ditetapkan), memenuhi kewajipan pendaftaran tetamu berkanun di bawah Akta Pendaftaran Tetamu 1965 (Akta 381) serta pengesahan Cukai Pelancongan di bawah Akta Cukai Pelancongan 2017 (Akta 791), dan mencegah penipuan tempahan atau penyalahgunaan identiti.',
          zh: '我们处理上述资料仅限于在入住前核实您的身份（包括在启用时支持预约快速或自动入住）、履行《1965年住客登记法令》（第381号法令）下的住客登记义务与《2017年旅游税法令》（第791号法令）下的旅客身份核验义务，并防范预订欺诈及身份冒用。',
          'zh-TW': '我們處理上述資料僅限於在入住前核實您的身分（包括在啟用時支援預約快速或自動入住）、履行《1965年住客登記法令》（第381號法令）下的住客登記義務與《2017年旅遊稅法令》（第791號法令）下的旅客身分核驗義務，並防範預訂欺詐及身分冒用。',
        },
        {
          en: 'We never use your facial image for automated profiling, cross-visit surveillance, marketing, or training any machine-learning system, and we never sell or license it to third parties.',
          ms: 'Kami tidak sekali-kali menggunakan imej wajah anda bagi pemprofilan automatik, pemantauan merentas kunjungan, pemasaran, atau melatih sebarang sistem pembelajaran mesin, dan kami tidak pernah menjual atau melesenkannya kepada pihak ketiga.',
          zh: '我们绝不会将您的面部影像用于自动化画像、跨次到店追踪、市场营销或训练任何机器学习系统，亦绝不会将其出售或授权给任何第三方。',
          'zh-TW': '我們絕不會將您的面部影像用於自動化畫像、跨次到店追蹤、市場行銷或訓練任何機器學習系統，亦絕不會將其出售或授權給任何第三方。',
        },
      ],
    },
    {
      id: 'who-sees-it',
      heading: {
        en: '3. Access controls and security',
        ms: '3. Kawalan capaian dan keselamatan',
        zh: '3. 访问权限控制与安全保障',
        'zh-TW': '3. 存取權限控制與安全保障',
      },
      body: [
        {
          en: 'Uploaded eKYC files are stored in isolated private storage outside the public web root. Only designated staff holding explicit eKYC review permissions can view them, and every view or review decision is recorded in an append-only audit trail. External disclosure occurs only where compelled by Malaysian law (such as a lawful request by the Royal Malaysia Police or a statutory regulator).',
          ms: 'Fail eKYC yang dimuat naik disimpan dalam storan peribadi terasing di luar akar web awam. Hanya kakitangan ditetapkan yang memegang kebenaran semakan eKYC khusus boleh melihatnya, dan setiap paparan atau keputusan semakan direkodkan dalam jejak audit tambah-sahaja. Pendedahan kepada pihak luar hanya berlaku apabila diwajibkan oleh undang-undang Malaysia (seperti permintaan sah Polis Diraja Malaysia atau pengawal selia berkanun).',
          zh: '上传的eKYC文件保存在独立于公开网站目录之外的受限私有存储区。仅持有专门eKYC审核权限的指定员工方可调阅，且每次查阅与审核决定均记录于防篡改追加式审计日志中。仅在马来西亚法律强制要求时（如马来西亚皇家警察或法定监管机关依法调取）方会对外披露。',
          'zh-TW': '上傳的eKYC文件保存在獨立於公開網站目錄之外的受限私有儲存區。僅持有專門eKYC審核權限的指定員工方可調閱，且每次查閱與審核決定均記錄於防篡改追加式審計日誌中。僅在馬來西亞法律強制要求時（如馬來西亞皇家警察或法定監管機關依法調取）方會對外披露。',
        },
      ],
    },
    {
      id: 'retention',
      heading: {
        en: '4. Retention and disposal',
        ms: '4. Penyimpanan dan pelupusan',
        zh: '4. 保留期限与销毁',
        'zh-TW': '4. 保留期限與銷毀',
      },
      body: [
        {
          en: 'In accordance with section 10 of the Personal Data Protection Act 2010, eKYC document and selfie images are retained in restricted storage only for as long as needed to complete verification and satisfy stay-related guest-register and audit obligations, or until you submit a valid erasure/withdrawal request after your stay, whereupon our compliance team purges the image files while retaining the non-biometric verification outcome (pass/fail and timestamp) on your guest record.',
          ms: 'Selaras dengan seksyen 10 Akta Perlindungan Data Peribadi 2010, imej dokumen dan swafoto eKYC disimpan dalam storan terhad hanya selama yang diperlukan untuk melengkapkan pengesahan serta memenuhi kewajipan daftar tetamu dan audit berkaitan penginapan, atau sehingga anda mengemukakan permohonan pemadaman/penarikan balik yang sah selepas penginapan anda, yang mana pasukan pematuhan kami akan memadamkan fail imej tersebut sambil mengekalkan keputusan pengesahan bukan biometrik (lulus/gagal dan cap masa) pada rekod tetamu anda.',
          zh: '依据《2010年个人资料保护法令》第10条，eKYC证件与自拍影像仅在完成身份核验及履行与住宿相关的住客登记与审计义务所需期间保存在受限存储中，或在您退房后提出有效删除/撤回请求时，由合规团队清除影像文件，仅在住客档案中保留非生物识别的核验结论（通过/未通过及时间戳）。',
          'zh-TW': '依據《2010年個人資料保護法令》第10條，eKYC證件與自拍影像僅在完成身分核驗及履行與住宿相關的住客登記與審計義務所需期間保存在受限儲存中，或在您退房後提出有效刪除/撤回請求時，由合規團隊清除影像文件，僅在住客檔案中保留非生物識別的核驗結論（通過/未通過及時間戳）。',
        },
      ],
    },
    {
      id: 'voluntary',
      heading: {
        en: '5. Voluntary choice and right to withdraw consent (section 42)',
        ms: '5. Pilihan sukarela dan hak menarik balik persetujuan (seksyen 42)',
        zh: '5. 自愿选择与撤回同意权（第42条）',
        'zh-TW': '5. 自願選擇與撤回同意權（第42條）',
      },
      emphasis: 'info',
      body: [
        {
          en: 'Online identity verification is completely voluntary. You can reserve, pay, and stay at the same rate without completing online eKYC; our reception team will simply sight and record your identification in person when you check in, as required for all guests.',
          ms: 'Pengesahan identiti dalam talian adalah sukarela sepenuhnya. Anda boleh menempah, membayar dan menginap pada kadar yang sama tanpa melengkapkan eKYC dalam talian; pasukan kaunter penyambut tetamu kami hanya akan menyemak dan merekodkan dokumen pengenalan anda secara bersemuka semasa daftar masuk, sebagaimana diwajibkan bagi semua tetamu.',
          zh: '在线身份验证完全出于自愿。即使不进行在线eKYC，您仍可按相同房价预订、付款并入住；届时只需在办理入住时按所有住客通用规定由前台人员现场查验并登记证件即可。',
          'zh-TW': '線上身分驗證完全出於自願。即使不進行線上eKYC，您仍可按相同房價預訂、付款並入住；屆時只需在辦理入住時按所有住客通用規定由前台人員現場查驗並登記證件即可。',
        },
        {
          en: `Under section 42 of the Personal Data Protection Act 2010, you may withdraw this consent at any time by written notice to ${identity.dataProtectionContactEmail}. Upon verifying your request, our compliance team will cease processing and delete your stored eKYC images unless Malaysian law requires retention. Withdrawal does not affect processing lawfully carried out before withdrawal.`,
          ms: `Di bawah seksyen 42 Akta Perlindungan Data Peribadi 2010, anda boleh menarik balik persetujuan ini pada bila-bila masa melalui notis bertulis kepada ${identity.dataProtectionContactEmail}. Selepas mengesahkan permohonan anda, pasukan pematuhan kami akan menghentikan pemprosesan dan memadamkan imej eKYC anda yang disimpan melainkan undang-undang Malaysia mewajibkan penyimpanannya. Penarikan balik tidak menjejaskan pemprosesan sah yang telah dilakukan sebelumnya.`,
          zh: `依据《2010年个人资料保护法令》第42条，您可随时发送书面通知至 ${identity.dataProtectionContactEmail} 撤回本同意。经核实请求后，除马来西亚法律要求保留外，我们的合规团队将停止处理并删除已保存的eKYC影像。撤回不影响此前已依法进行的处理。`,
          'zh-TW': `依據《2010年個人資料保護法令》第42條，您可隨時發送書面通知至 ${identity.dataProtectionContactEmail} 撤回本同意。經核實請求後，除馬來西亞法律要求保留外，我們的合規團隊將停止處理並刪除已保存的eKYC影像。撤回不影響此前已依法進行的處理。`,
        },
      ],
    },
  ],
});

export const ekycConsent: LegalDocument = buildEkycConsent();
