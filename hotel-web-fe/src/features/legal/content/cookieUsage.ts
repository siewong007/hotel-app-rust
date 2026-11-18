import type { LocalizedText } from './types';

/** Informational cookie and browser-storage copy; this is not a consentable legal document. */
export const cookieUsage = {
  languageLabel: {
    en: 'Language',
    ms: 'Bahasa',
    zh: '语言',
    'zh-TW': '語言',
  },
  title: {
    en: 'Cookie Usage',
    ms: 'Penggunaan Kuki',
    zh: 'Cookie 使用说明',
    'zh-TW': 'Cookie 使用說明',
  },
  privacyLink: {
    en: 'Privacy Policy',
    ms: 'Dasar Privasi',
    zh: '隐私政策',
    'zh-TW': '隱私權政策',
  },
  paragraphs: [
    {
      en: 'We save your language and display preferences in this browser. If you have signed in, saved account information helps us show your account link.',
      ms: 'Kami menyimpan pilihan bahasa dan paparan anda dalam pelayar ini. Jika anda telah log masuk, maklumat akaun tersimpan membantu kami memaparkan pautan akaun anda.',
      zh: '我们会在此浏览器中保存您的语言和显示偏好。如果您已登录，保存的账户信息会帮助我们显示您的账户链接。',
      'zh-TW': '我們會在此瀏覽器中保存您的語言和顯示偏好。如果您已登入，保存的帳戶資料會協助我們顯示您的帳戶連結。',
    },
    {
      en: 'Strictly necessary sign-in cookies (an HttpOnly session refresh cookie and a non-sensitive session indicator) and temporary browser storage keep your account and guest booking session working securely.',
      ms: 'Kuki log masuk yang amat diperlukan (kuki segar semula sesi HttpOnly dan penunjuk sesi bukan sensitif) serta storan pelayar sementara memastikan akaun dan sesi tempahan tetamu anda berfungsi dengan selamat.',
      zh: '严格必要的登录 Cookie（HttpOnly 会话刷新 Cookie 及非敏感登录状态标记）和临时浏览器存储可保障您的账户及宾客预订会话安全正常运行。',
      'zh-TW': '嚴格必要的登入 Cookie（HttpOnly 工作階段重新整理 Cookie 及非敏感登入狀態標記）和暫存瀏覽器儲存可保障您的帳戶及賓客預訂工作階段安全正常運作。',
    },
    {
      en: 'Neither the landing page nor the booking portal uses advertising trackers, cross-site behavioural profiling cookies, or third-party marketing pixels. Where you choose PayPal checkout or anti-abuse verification, those providers may set strictly functional security cookies during that step.',
      ms: 'Halaman utama mahupun portal tempahan tidak menggunakan penjejak pengiklanan, kuki pemprofilan tingkah laku merentas laman, atau piksel pemasaran pihak ketiga. Sekiranya anda memilih pembayaran PayPal atau pengesahan keselamatan anti-penyalahgunaan, penyedia tersebut mungkin menetapkan kuki keselamatan berfungsi semasa langkah tersebut.',
      zh: '本落地页及预订门户均不使用广告追踪器、跨站行为画像 Cookie 或第三方营销像素。若您选择使用 PayPal 结账或完成防滥用安全验证，相关服务商可能会在该步骤设置必要的功能安全 Cookie。',
      'zh-TW': '本落地頁及預訂門戶均不使用廣告追蹤器、跨站行為畫像 Cookie 或第三方行銷像素。若您選擇使用 PayPal 結帳或完成防濫用安全驗證，相關服務商可能會在該步驟設定必要的功能安全 Cookie。',
    },
    {
      en: 'You can block or clear cookies and site storage in your browser settings at any time. Doing so can reset your language or display preferences and sign you out of active sessions.',
      ms: 'Anda boleh menyekat atau memadam kuki dan storan laman melalui tetapan pelayar pada bila-bila masa. Tindakan itu boleh menetapkan semula bahasa atau pilihan paparan dan melog keluar anda daripada sesi aktif.',
      zh: '您可以随时在浏览器设置中阻止或清除 Cookie 和网站存储。这样做可能会重置语言或显示偏好，并使您退出当前登录会话。',
      'zh-TW': '您可以隨時在瀏覽器設定中封鎖或清除 Cookie 和網站儲存。這可能會重設語言或顯示偏好，並使您登出目前工作階段。',
    },
  ] satisfies LocalizedText[],
};
