// Extracted verbatim from index.html's classic (non-module) inline
// <script> block, byte-for-byte except for the delegated listeners added
// at the end of this IIFE — moved to an external same-origin file so a
// strict CSP script-src ('self' + the exact external SDK origins) doesn't
// need 'unsafe-inline' or content hashes for it. This still runs as a
// blocking classic script at the exact same point in <head>/<body> as
// before (see index.html), and is genuinely outside the js/ ES module
// graph — see CLAUDE.md's "index.html script structure" section for why
// (window.tr/Icon/setIcon/translateStaticDOM/setLang/setTheme are real
// globals other js/*.js files call via window, not real imports).
(function(){
  // ── ICON LIBRARY (monochrome, line-style, SF-Symbols-like) ──
  var ICON_PATHS = {
    calendar:'<rect x="3.5" y="5" width="17" height="15" rx="3.2"/><path d="M3.5 9.6h17M8 3v3.4M16 3v3.4"/>',
    wallet:'<rect x="3" y="7" width="18" height="12" rx="3"/><path d="M3 10.6h18"/><circle cx="16.6" cy="14.4" r="1" fill="currentColor" stroke="none"/>',
    sun:'<circle cx="12" cy="12" r="4.2"/><path d="M12 2.6v2.3M12 19.1v2.3M4.3 4.3l1.6 1.6M18.1 18.1l1.6 1.6M2.6 12h2.3M19.1 12h2.3M4.3 19.7l1.6-1.6M18.1 5.9l1.6-1.6"/>',
    sunFill:'<circle cx="12" cy="12" r="4.6" fill="currentColor" stroke="none"/><path d="M12 2.6v2.1M12 19.3v2.1M4.3 4.3l1.5 1.5M18.2 18.2l1.5 1.5M2.6 12h2.1M19.3 12h2.1M4.3 19.7l1.5-1.5M18.2 5.8l1.5-1.5"/>',
    moon:'<path d="M20 14.6A8.6 8.6 0 1 1 9.4 4a7.1 7.1 0 0 0 10.6 10.6Z"/>',
    clock:'<circle cx="12" cy="12" r="8.4"/><path d="M12 7.5V12l3.1 1.9"/>',
    umbrella:'<path d="M3 13a9 9 0 0 1 18 0Z"/><path d="M12 4v1.3M12 13v6.4a1.8 1.8 0 0 1-3.5 0"/>',
    trendUp:'<path d="M3 17l6-6 4 4 8-9"/><path d="M15 6h6v6"/>',
    trendDown:'<path d="M3 7l6 6 4-4 8 9"/><path d="M15 18h6v-6"/>',
    barChart:'<path d="M4 20V10M10 20V4M16 20v-7"/><path d="M3 20h18"/>',
    bolt:'<path d="M13 2 4 14h6l-1 8 9-12h-6Z" fill="currentColor" stroke="none"/>',
    trash:'<path d="M5 6.5h14"/><path d="M9.5 6.5V4.3A1.3 1.3 0 0 1 10.8 3h2.4a1.3 1.3 0 0 1 1.3 1.3v2.2"/><path d="M6.7 6.5l1 13.2A1.8 1.8 0 0 0 9.5 21.5h5a1.8 1.8 0 0 0 1.8-1.8l1-13.2"/>',
    coin:'<circle cx="12" cy="12" r="8.4"/><path d="M12 7.6v8.8M9.4 9.4c0-1 1.1-1.7 2.6-1.7s2.6.7 2.6 1.6c0 2.3-5.2 1-5.2 3.3 0 1 1.1 1.7 2.6 1.7s2.6-.8 2.6-1.7"/>',
    card:'<rect x="2.5" y="5.5" width="19" height="13" rx="2.4"/><path d="M2.5 9.7h19"/>',
    banknote:'<rect x="2.5" y="6.5" width="19" height="11" rx="2"/><circle cx="12" cy="12" r="2.4"/>',
    plus:'<path d="M12 5v14M5 12h14"/>',
    warning:'<path d="M12 4 21.5 20H2.5Z"/><path d="M12 10v4.2"/><circle cx="12" cy="17.2" r=".15" fill="currentColor" stroke="none"/>',
    refresh:'<path d="M4 12a8 8 0 0 1 13.7-5.7L20 8"/><path d="M20 4v4h-4"/><path d="M20 12a8 8 0 0 1-13.7 5.7L4 16"/><path d="M4 20v-4h4"/>',
    check:'<path d="M4.5 12.5 9.5 17.5 19.5 6.5"/>',
    xmark:'<path d="M6 6l12 12M18 6 6 18"/>',
    offline:'<path d="M2 8.6a15 15 0 0 1 8.4-3.9M13.6 4.7A15 15 0 0 1 22 8.6"/><path d="M5 12.2a10 10 0 0 1 6-2.9M15.2 10.5a10 10 0 0 1 3.8 1.7"/><path d="M8.6 15.7a5 5 0 0 1 4.2-1"/><circle cx="12" cy="19" r="1" fill="currentColor" stroke="none"/><path d="M2.5 2.5l19 19"/>',
    inbox:'<path d="M3 12 6 5h12l3 7"/><path d="M3 12v6.3A1.7 1.7 0 0 0 4.7 20h14.6a1.7 1.7 0 0 0 1.7-1.7V12"/><path d="M3 12h5.2a.8.8 0 0 1 .7.4l.9 1.6a.8.8 0 0 0 .7.4h2.9a.8.8 0 0 0 .7-.4l.9-1.6a.8.8 0 0 1 .7-.4H21"/>',
    sparkle:'<path d="M12 3v4M12 17v4M4.2 12H8M16 12h3.8"/><path d="M6.3 6.3l2.1 2.1M15.6 15.6l2.1 2.1M17.7 6.3l-2.1 2.1M8.4 15.6l-2.1 2.1"/>',
    swap:'<path d="M4 8h13l-3-3"/><path d="M20 16H7l3 3"/>',
    bank:'<path d="M3 10 12 4l9 6"/><path d="M4 10h16v9H4Z"/><path d="M4 19h16M7 13v4M12 13v4M17 13v4"/>',
    cart:'<path d="M3 4h2l2.4 12.2A2 2 0 0 0 9.3 18H18a2 2 0 0 0 2-1.6L21.5 8H6"/><circle cx="10" cy="21" r="1.3"/><circle cx="18" cy="21" r="1.3"/>',
    car:'<path d="M5 16V11l2-4h10l2 4v5"/><path d="M3 16h18M7 16v2M17 16v2"/><circle cx="7.5" cy="16" r="1.2"/><circle cx="16.5" cy="16" r="1.2"/>',
    house:'<path d="M4 11 12 4l8 7"/><path d="M6 10v9.3a.7.7 0 0 0 .7.7h10.6a.7.7 0 0 0 .7-.7V10"/><path d="M10 20v-5h4v5"/>',
    bag:'<path d="M6 8h12l1 12.3a1.7 1.7 0 0 1-1.7 1.7H6.7A1.7 1.7 0 0 1 5 20.3Z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/>',
    coffee:'<path d="M4 9h13v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Z"/><path d="M17 10.5h1.4a2.4 2.4 0 0 1 0 4.8H17"/><path d="M7.3 6.2c0-1 .8-1 .8-2M11 6.2c0-1 .8-1 .8-2"/>',
    burger:'<path d="M4 10h16"/><path d="M3.5 14h17"/><path d="M5 10a7 7 0 0 1 14 0"/><path d="M5 14v1a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-1"/>',
    cigarette:'<path d="M2.5 14.5h13.5v3.2H2.5Z"/><path d="M12.7 14.5v3.2"/><path d="M16.5 11.8s-1-1-1-2 1-2 1-2M19.5 11.8s-1-1-1-2 1-2 1-2"/>',
    gift:'<path d="M3 9h18v4H3Z"/><path d="M5 13h14v6.3a.7.7 0 0 1-.7.7H5.7a.7.7 0 0 1-.7-.7Z"/><path d="M12 9v11"/><path d="M12 9C12 6.2 9.7 4.3 8.3 5.2S8.2 9 12 9M12 9c0-2.8 2.3-4.7 3.7-3.8S15.8 9 12 9"/>',
    briefcase:'<rect x="3" y="7.5" width="18" height="12" rx="2.2"/><path d="M8 7.5V6a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1.5"/><path d="M3 12.5h18"/>',
    person:'<circle cx="12" cy="8" r="3.3"/><path d="M5 20c0-3.6 3.1-6.3 7-6.3s7 2.7 7 6.3"/>',
    box:'<path d="M3 8 12 4l9 4-9 4-9-4Z"/><path d="M3 8v9l9 4 9-4V8"/><path d="M12 12v9"/>',
    logout:'<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>',
    tag:'<path d="M3 12V5.5A2.5 2.5 0 0 1 5.5 3H12l8.5 8.5a2 2 0 0 1 0 2.8l-6.2 6.2a2 2 0 0 1-2.8 0L3 12Z"/><circle cx="7.5" cy="7.5" r="1.3" fill="currentColor" stroke="none"/>',
    chevron:'<path d="M6 9l6 6 6-6"/>',
    caretDown:'<path d="M7 9.5 12 14.5 17 9.5Z" fill="currentColor" stroke="none"/>',
    gear:'<circle cx="12" cy="12" r="3.2"/><path d="M19.4 13.5a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H2.9a2 2 0 1 1 0-4H3a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V2.9a2 2 0 1 1 4 0V3a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"/>',
    sliders:'<path d="M4 7h9"/><path d="M17 7h3"/><circle cx="15" cy="7" r="2" fill="var(--bg2)" stroke="currentColor"/><path d="M4 12h3"/><path d="M11 12h9"/><circle cx="9" cy="12" r="2" fill="var(--bg2)" stroke="currentColor"/><path d="M4 17h10"/><path d="M18 17h2"/><circle cx="16" cy="17" r="2" fill="var(--bg2)" stroke="currentColor"/>',
    target:'<circle cx="12" cy="12" r="8.4"/><circle cx="12" cy="12" r="4.6"/><circle cx="12" cy="12" r="1.1" fill="currentColor" stroke="none"/>',
    repeat:'<path d="M17 2.5l4 4-4 4"/><path d="M3 11.5v-2a4 4 0 0 1 4-4h14"/><path d="M7 21.5l-4-4 4-4"/><path d="M21 12.5v2a4 4 0 0 1-4 4H3"/>',
    download:'<path d="M12 3v12.5"/><path d="M7 11.5 12 16.5 17 11.5"/><path d="M4 20h16"/>',
    upload:'<path d="M12 16.5v-11"/><path d="M7 9.5 12 4.5 17 9.5"/><path d="M4 20h16"/>',
    lock:'<rect x="4.5" y="10.5" width="15" height="10" rx="2.5"/><path d="M8 10.5V7a4 4 0 0 1 8 0v3.5"/>',
    more:'<circle cx="12" cy="5" r="1.3" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/><circle cx="12" cy="19" r="1.3" fill="currentColor" stroke="none"/>',
    search:'<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
    pencil:'<path d="M17.3 3.3a2.4 2.4 0 0 1 3.4 3.4L8.4 19 3.5 20.5 5 15.6Z"/><path d="M14.9 5.7l3.4 3.4"/>',
    info:'<circle cx="12" cy="12" r="8.4"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1" fill="currentColor" stroke="none"/>',
    grip:'<circle cx="9" cy="6" r="1.4" fill="currentColor" stroke="none"/><circle cx="9" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="9" cy="18" r="1.4" fill="currentColor" stroke="none"/><circle cx="15" cy="6" r="1.4" fill="currentColor" stroke="none"/><circle cx="15" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="15" cy="18" r="1.4" fill="currentColor" stroke="none"/>',
    star:'<path d="M12 3.4l2.7 5.7 6.2.6-4.6 4.2 1.3 6.1L12 16.9l-5.6 3.1 1.3-6.1-4.6-4.2 6.2-.6Z"/>',
    bell:'<path d="M6 10.2a6 6 0 0 1 12 0c0 4.1 1.5 5.6 2 6.1H4c.5-.5 2-2 2-6.1Z"/><path d="M9.3 19a2.7 2.7 0 0 0 5.4 0"/>',
    camera:'<path d="M4 8.3A1.7 1.7 0 0 1 5.7 6.6h1.8l1-1.9h6.9l1 1.9h1.8A1.7 1.7 0 0 1 20 8.3v9A1.7 1.7 0 0 1 18.3 19H5.7A1.7 1.7 0 0 1 4 17.3Z"/><circle cx="12" cy="13" r="3.4"/>',
    globe:'<circle cx="12" cy="12" r="8.4"/><path d="M3.6 12h16.8"/><path d="M12 3.6c2.7 2.2 4.2 5.2 4.2 8.4s-1.5 6.2-4.2 8.4c-2.7-2.2-4.2-5.2-4.2-8.4S9.3 5.8 12 3.6Z"/>',
    pie:'<path d="M12 3.5v8.5h8.5A8.5 8.5 0 1 1 12 3.5Z"/><path d="M14.2 3.8A8.5 8.5 0 0 1 20.2 9.8"/>',
    doc:'<path d="M6 3.5h8l4 4v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-16a1 1 0 0 1 1-1Z"/><path d="M14 3.5V8h4"/><path d="M8 12.5h8M8 16h8"/>',
    grid:'<rect x="3" y="3" width="8" height="8" rx="2"/><rect x="13" y="3" width="8" height="8" rx="2"/><rect x="3" y="13" width="8" height="8" rx="2"/><rect x="13" y="13" width="8" height="8" rx="2"/>',
    eye:'<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
    eyeOff:'<path d="M3 3l18 18"/><path d="M10.6 5.2A10.7 10.7 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.1M6.6 6.6C3.6 8.6 2 12 2 12s3.6 7 10 7a10 10 0 0 0 4-.8"/><path d="M9.9 10a3 3 0 0 0 4.2 4.2"/>',
    flag:'<path d="M5 3v18"/><path d="M5 4h13l-3 4 3 4H5"/>',
    phone:'<rect x="7" y="2.5" width="10" height="19" rx="2.2"/><path d="M11 18.5h2"/>',
    calculator:'<rect x="5" y="2.5" width="14" height="19" rx="2.5"/><rect x="7.5" y="5" width="9" height="4" rx="1"/><circle cx="8.3" cy="13" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="13" r="1" fill="currentColor" stroke="none"/><circle cx="15.7" cy="13" r="1" fill="currentColor" stroke="none"/><circle cx="8.3" cy="17" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="17" r="1" fill="currentColor" stroke="none"/><circle cx="15.7" cy="17" r="1" fill="currentColor" stroke="none"/>',
    idCard:'<rect x="2.5" y="5" width="19" height="14" rx="2.5"/><circle cx="8" cy="12" r="2.2"/><path d="M5 16.5c0-1.7 1.3-2.8 3-2.8s3 1.1 3 2.8"/><path d="M13.5 9.5h4.5M13.5 13h4.5"/>',
    people:'<circle cx="9" cy="8" r="3"/><path d="M3.5 19c0-3.2 2.5-5.5 5.5-5.5s5.5 2.3 5.5 5.5"/><circle cx="17" cy="9" r="2.4"/><path d="M15.2 13.8c2.5.3 4.3 2.3 4.3 5"/>'
  };
  window.Icon = function(name){
    var d = ICON_PATHS[name] || '';
    return '<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'+d+'</svg>';
  };
  // Curated subset of ICON_PATHS suitable for a user-facing "pick a category
  // icon" grid — deliberately excludes pure UI/action glyphs (trash, plus,
  // check, xmark, chevron, gear, search, pencil, grip, lock, etc.) that make
  // sense as button icons but not as a thing-you'd-pick-to-represent-a-
  // spending-category. Exposed on window (like Icon/setIcon above) since
  // this is a classic script outside the js/ ES module graph — see
  // CLAUDE.md's "index.html script structure" section. Add new entries here
  // when adding a new icon to ICON_PATHS that's thematically category-like.
  window.ICON_NAMES = ['calendar','wallet','clock','umbrella','trendUp','trendDown','barChart','bolt','coin','card','banknote','inbox','sparkle','swap','bank','cart','car','house','bag','coffee','burger','cigarette','gift','briefcase','person','box','tag','target','repeat','star','bell','camera','globe','pie','doc','flag','phone','calculator','idCard','people'];
  function setIcon(id, name){
    var el = document.getElementById(id);
    if(el) el.innerHTML = window.Icon(name);
  }
  var GOOGLE_G_SVG='<svg class="ico" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.4-6.4C35.6 3 30.1 1 24 1 14.6 1 6.5 6.4 2.6 14.2l7.5 5.8C12 13.6 17.5 9.5 24 9.5z"/><path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.6c-.5 3-2.2 5.5-4.7 7.2l7.2 5.6C43.5 37.6 46.5 31.6 46.5 24.5z"/><path fill="#FBBC05" d="M10.1 20c-.6 1.6-.9 3.3-.9 5s.3 3.4.9 5l-7.5 5.8C1 32.6 0 28.4 0 24s1-8.6 2.6-11.8z"/><path fill="#34A853" d="M24 47c6.1 0 11.3-2 15-5.5l-7.2-5.6c-2 1.4-4.6 2.2-7.8 2.2-6.5 0-12-4.1-13.9-9.9l-7.5 5.8C6.5 41.6 14.6 47 24 47z"/></svg>';
  var elGoogle=document.getElementById('ic-google'); if(elGoogle) elGoogle.innerHTML=GOOGLE_G_SVG;
  setIcon('ic-phone','phone');
  setIcon('ic-set-signout','logout');
  setIcon('ic-set-delete','trash');
  setIcon('ic-set-pin','lock');
  setIcon('ic-pin-logo','lock');
  setIcon('ic-bio-unlock','lock');
  setIcon('ic-bio-enable','lock');
  setIcon('ic-bio-disable','lock');
  // Populate every static icon slot
  setIcon('btn-refresh','refresh');
  setIcon('ptr-spinner','refresh');
  try{
    var hideAmt=localStorage.getItem('mxHideAmounts')==='1';
    document.body.classList.toggle('amounts-hidden', hideAmt);
    setIcon('btn-hide-amounts', hideAmt?'eyeOff':'eye');
    var hideBtn=document.getElementById('btn-hide-amounts');
    if(hideBtn){
      hideBtn.classList.toggle('is-active', hideAmt);
      hideBtn.setAttribute('aria-pressed', hideAmt?'true':'false');
      hideBtn.setAttribute('aria-label', hideAmt?'Показати суми':'Сховати суми');
    }
  }catch(e){}
  setIcon('ic-settings-search','search');
  setIcon('ic-settings-tip','sparkle');
  setIcon('ic-settings-tip-close','xmark');
  setIcon('ic-topbar-settings','sliders');
  setIcon('ic-settings-profile','person');
  setIcon('ic-nickname-edit','pencil');
  setIcon('ic-settings-profiles','people');
  setIcon('ic-settings-finance','wallet');
  setIcon('ic-settings-security','lock');
  setIcon('ic-settings-notif','bell');
  setIcon('ic-notif-push','bell');
  setIcon('ic-notif-toggle','bell');
  setIcon('ic-notif-budget','warning');
  setIcon('ic-notif-recurring','repeat');
  setIcon('ic-notif-debt','bell');
  setIcon('ic-settings-appearance','sun');
  setIcon('ic-settings-privacy-cache','lock');
  setIcon('ic-settings-account','idCard');
  setIcon('ic-settings-about','info');
  setIcon('ic-set-premium','star');
  setIcon('ic-premium-badge','star');
  setIcon('ic-premium-wallets','check');
  setIcon('ic-premium-bank','bank');
  setIcon('ic-set-wallets','wallet');
  setIcon('ic-set-rates','swap');
  setIcon('ic-rates-update','refresh');
  setIcon('ic-fx-widget','swap');
  setIcon('ic-fx-widget-refresh','refresh');
  setIcon('ic-fx-converter','calculator');
  setIcon('ic-fx-converter-swap','swap');
  setIcon('ic-set-widgets','grid');
  setIcon('ic-set-cats','box');
  setIcon('ic-set-tags','tag');
  setIcon('ic-set-budgets','target');
  setIcon('ic-set-recurring','repeat');
  setIcon('ic-set-rules','sparkle');
  setIcon('ic-add-rule','plus');
  setIcon('ic-set-export','download');
  setIcon('ic-set-import','upload');
  setIcon('ic-set-terms','doc');
  setIcon('ic-set-privacy','lock');
  setIcon('tab-icon-shifts','calendar');
  setIcon('tab-icon-finance','wallet');
  setIcon('tab-icon-debt','bank');
  setIcon('ic-debt-start','bank');
  setIcon('ic-debt-balance','coin');
  setIcon('ic-debt-paid','check');
  setIcon('ic-debt-count','calendar');
  setIcon('ic-debt-due','bell');
  setIcon('ic-debt-forecast','trendDown');
  setIcon('ic-debt-info','doc');
  setIcon('debt-info-chevron','caretDown');
  setIcon('debt-history-chevron','caretDown');
  setIcon('tab-icon-shopping','cart');
  setIcon('ic-shopping-remaining','cart');
  setIcon('ic-shopping-bought','check');
  setIcon('ic-shopping-add','plus');
  setIcon('ic-debt-plus','plus');
  setIcon('ic-debt-fab','plus');
  setIcon('ic-salary','banknote');
  setIcon('ic-hours','clock');
  setIcon('ic-day','sun');
  setIcon('ic-vacation','umbrella');
  setIcon('ic-trend','trendUp');
  setIcon('ic-bolt','bolt');
  setIcon('tools-toggle-chevron','caretDown');
  setIcon('ic-shift-types','gear');
  setIcon('ic-trash-month','trash');
  setIcon('ic-barchart','barChart');
  setIcon('ic-plus','plus');
  setIcon('ic-fin-fab','plus');
  setIcon('ic-tx-search','search');
  setIcon('ic-tx-search-clear','xmark');
  setIcon('ic-modal-cal','calendar');
  setIcon('ic-budget','target');
  setIcon('ic-goals','flag');
  setIcon('ic-set-goals','flag');
  setIcon('ic-add-goal','plus');
  setIcon('ic-qa-tx','plus');
  setIcon('ic-qa-tools','grid');
  setIcon('ic-qa-budgets','target');
  setIcon('ic-qa-goals','flag');
  setIcon('ic-analytics','pie');
  setIcon('ic-add-recur','plus');
  setIcon('ic-add-tag','plus');
  setIcon('ic-add-profile','plus');
  setIcon('ic-share-profile','people');
  setIcon('ic-join-profile','idCard');
  setIcon('ic-scan-receipt','camera');
  setIcon('ic-set-link-phone','phone');
  setIcon('ic-cat-act-edit','pencil');
  setIcon('ic-cat-act-show','search');
  setIcon('ic-cat-act-del','trash');
  setIcon('ic-add-st','plus');
  setIcon('ic-add-w','plus');
  setIcon('ic-add-cat','plus');

  function $(id){return document.getElementById(id);}

  // ── THEME ──
  var THEME_ICON={dark:'moon',light:'sun'};
  var THEME_COLOR={dark:'#1c1c1f',light:'#f2f2f7'};
  function applyTheme(theme){
    if(!THEME_ICON[theme]) theme='dark';
    document.documentElement.setAttribute('data-theme', theme);
    try{localStorage.setItem('mxTheme', theme);}catch(e){}
    var mc=document.querySelector('meta[name=theme-color]');
    if(mc) mc.setAttribute('content', THEME_COLOR[theme]);
    document.querySelectorAll('.theme-opt').forEach(function(b){ b.classList.toggle('active', b.dataset.theme===theme); });
  }
  window.setTheme=function(theme){ applyTheme(theme); };
  window.toggleTheme=function(){
    var cur=document.documentElement.getAttribute('data-theme')||'dark';
    var order=['dark','light'];
    applyTheme(order[(order.indexOf(cur)+1)%order.length]);
  };
  applyTheme(document.documentElement.getAttribute('data-theme')||'dark');

  // ── LANGUAGE (uk/en) ──
  // Named `tr` (not `t`) since the app uses `t` everywhere as the loop
  // variable for a single transaction — a same-named global would shadow
  // itself in half the finance code.
  var I18N={
    uk:{
      settings_kicker:'Керування застосунком', settings_title:'Налаштування',
      settings_search_placeholder:'Пошук: гаманці, категорії, push…', settings_search_empty:'Нічого не знайдено',
      settings_group_all:'Усі', settings_group_account:'Акаунт', settings_group_finance:'Фінанси', settings_group_security:'Безпека', settings_group_app:'Вигляд',
      settings_tip_search:'Порада: скористайся пошуком або групами вище, щоб швидко знайти потрібний розділ.',
      settings_profile:'Профіль', settings_nickname_placeholder:'Нікнейм', settings_avatar_picker_label:'Або обери іконку профілю',
      settings_finance:'Фінанси', settings_wallets:'Гаманці', settings_rates:'Курси валют',
      settings_categories:'Категорії', settings_tags:'Теги', settings_budgets:'Бюджети',
      settings_recurring:'Повторювані операції', settings_rules:'Автоматичні правила', settings_export:'Експорт CSV',
      settings_import:'Імпорт CSV',
      settings_security:'Безпека', settings_pin:'PIN-код доступу',
      settings_notifications:'Сповіщення', settings_notif_enable:'Увімкнути нагадування', settings_notif_disable:'Вимкнути нагадування',
      settings_notif_hint:'Нагадаємо, якщо за день не додано жодної операції. Працює, поки застосунок хоч раз відкривався в браузері протягом дня.',
      settings_notif_budget_title:'Бюджет перевищено', settings_notif_budget_hint:'Сповістимо, коли витрати за категорією перевищать місячний бюджет.',
      settings_notif_recurring_title:'Наближається платіж', settings_notif_recurring_hint:'Попередимо за день до автододавання повторюваної операції.',
      settings_notif_debt_title:'Наближається термін боргу', settings_notif_debt_hint:'Попередимо за день до дати, до якої треба віддати борг.',
      settings_notif_push_title:'Push-сповіщення', settings_notif_push_hint:'Приходять навіть коли застосунок закрито.',
      notif_budget_title:'Бюджет перевищено', notif_budget_body:'Витрати за категорією "{category}" перевищили місячний бюджет.',
      notif_recurring_title:'Наближається платіж', notif_recurring_body:'Завтра автоматично додасться операція "{category}" на {amount}.',
      notif_debt_title:'Наближається термін боргу', notif_debt_body:'Завтра настає дата, до якої треба віддати "{name}".',
      settings_appearance:'Зовнішній вигляд', theme_dark:'Темна', theme_light:'Світла',
      privacy_cache_title:'Локальний кеш даних', privacy_cache_hint_on:'Швидший запуск: фінансові дані зберігаються на цьому пристрої.',
      privacy_cache_hint_off:'Приватний режим: фінансовий кеш очищено, дані завантажуються з хмари після входу.',
      privacy_cache_cleared:'Локальний фінансовий кеш очищено', privacy_cache_on:'Локальний кеш увімкнено',
      settings_account:'Акаунт', settings_signout:'Вийти з акаунту', settings_delete_account:'Видалити акаунт',
      settings_phone:'Номер телефону', settings_phone_sub_empty:'Не додано',
      settings_phone_remove:'Прибрати номер', settings_phone_remove_confirm:'Прибрати цей номер телефону з акаунту?',
      settings_phone_remove_title:'Прибрати номер', settings_phone_linked:'Номер телефону додано',
      settings_phone_removed:'Номер телефону прибрано', settings_phone_remove_fail:'Не вдалося прибрати номер',
      settings_about:'Про застосунок', settings_terms:'Умови використання', settings_privacy:'Політика конфіденційності',
      settings_footer:'Rytm — трекер змін, фінансів і розрахунків', settings_signed_in_as:'Увійшли як',
      auth_tagline:'Фінанси, зміни та борги в одному місці',
      auth_login_tab:'Вхід', auth_register_tab:'Реєстрація', auth_password:'Пароль', auth_password_placeholder:'Мінімум 6 символів',
      auth_login_btn:'Увійти', auth_register_btn:'Зареєструватися', auth_forgot:'Забули пароль?', auth_or:'або',
      auth_google:'Продовжити через Google', auth_terms_pre:'Реєструючись, ти погоджуєшся з', auth_terms_and:'і',
      auth_phone_btn:'Увійти за номером телефону', auth_phone_label:'Номер телефону', auth_phone_send:'Надіслати код',
      auth_phone_code_label:'Код із SMS', auth_phone_verify:'Підтвердити', auth_phone_back:'← Назад',
      auth_phone_code_sent:'Код надіслано', auth_phone_bad_format:'Введи номер у форматі +380XXXXXXXXX',
      auth_phone_enter_code:'Введи код із SMS',
      topbar_sub:'Зміни · Фінанси · Хмара',
      nav_shifts:'Графік змін', nav_finance:'Фінанси', nav_debt:'Розрахунки', nav_settings:'Налаштування', nav_shopping:'Покупки',
      shopping_kicker:'Ваші покупки', shopping_title:'Список покупок',
      shopping_add_placeholder:'Назва товару', shopping_qty_placeholder:'К-сть', shopping_add_btn:'Додати',
      shopping_empty:'Список порожній', shopping_clear_bought:'Очистити куплені',
      shopping_stat_remaining:'Залишилось', shopping_stat_bought:'Куплено',
      shopping_clear_confirm:'Видалити всі куплені товари зі списку?', shopping_clear_title:'Очистити куплені',
      shifts_kicker:'Особистий робочий календар', shifts_title:'Робочі зміни',
      finance_kicker:'Особистий бюджет', finance_title:'Гаманці & Фінанси',
      debt_kicker:'Облік розрахунків', debt_title:'Розрахунки',
      finance_goto_settings:'Налаштувати екран', finance_budgets_title:'Бюджети цього місяця',
      finance_empty_title:'Операцій ще немає', finance_empty_desc:'Додай перший дохід або витрату, щоб побачити баланс, історію та аналітику.',
      finance_start_title:'Почни за хвилину', finance_start_desc:'Три кроки, щоб Rytm одразу став корисним.',
      finance_start_wallet:'Перевір гаманець', finance_start_wallet_sub:'Назва, валюта, баланс',
      finance_start_tx:'Додай операцію', finance_start_tx_sub:'Перший дохід або витрата',
      finance_start_widgets:'Налаштуй екран', finance_start_widgets_sub:'Приховай зайві блоки',
      shifts_empty_title:'Календар порожній', shifts_empty_desc:'Натисни на день у календарі або застосуй шаблон, щоб швидко заповнити графік.',
      debt_empty_title:'Платежів ще немає', debt_empty_desc:'Додай перший платіж, щоб бачити залишок, прогрес і історію розрахунку.',
      debt_empty_no_calc_title:'Розрахунків ще немає', debt_empty_no_calc_desc:'Створи перший розрахунок для позики, розстрочки або спільних витрат.',
      shopping_empty_title:'Список порожній', shopping_empty_desc:'Додай товари перед походом у магазин і відмічай куплене одним тапом.',
      finance_chart_title:'Баланс за останні 6 місяців', finance_new_tx:'Нова операція', common_cancel:'Скасувати',
      finance_chart_net:'Баланс', finance_chart_income:'Дохід', finance_chart_expense:'Витрата',
      finance_chart_forecast:'Прогноз', finance_chart_forecast_tip:'прогноз', finance_chart_avg:'Середнє',
      finance_chart_best:'Найкращий', finance_chart_worst:'Найгірший',
      finance_total_balance:'Загальний баланс (у грн)', finance_total_balance_approx:'Орієнтовний баланс (у грн)', finance_total_balance_hint:'Сума перерахована в гривню за поточними курсами гаманців.',
      finance_month_income:'Дохід цього місяця', finance_month_expense:'Витрата цього місяця',
      finance_type_income:'+ Дохід', finance_type_expense:'− Витрата', finance_type_transfer:'⇄ Переказ',
      finance_wallet:'Гаманець', finance_wallet_expense:'Звідки списати', finance_wallet_transfer:'Звідки переказати',
      finance_wallet_target:'Куди переказати', finance_category:'Категорія', finance_subcategory:'Підкатегорія',
      finance_transfer_hint:'Орієнтовно за поточним курсом:', finance_transfer_same_wallet_hint:'Оберіть інший гаманець для переказу.',
      finance_tags:'Теги', finance_date:'Дата', finance_comment:'Коментар', finance_comment_placeholder:'Деталі операції...',
      finance_amount_prefix:'Сума', finance_add_btn:'Додати запис', finance_history_title:'Історія операцій',
      finance_filter_all:'Всі', finance_filter_reset:'Скинути ✕', finance_records_suffix:'записів', finance_no_records:'Записів немає',
      finance_search_placeholder:'Пошук за коментарем, категорією, гаманцем…',
      finance_search_empty_title:'Нічого не знайдено', finance_search_empty_desc:'Зміни фільтр або пошуковий запит, щоб побачити операції.',
      finance_view_all:'Переглянути всі', finance_show_less:'Згорнути',
      toast_tx_updated:'Запис оновлено', toast_transfer_done:'Переказ виконано', toast_tx_added:'Запис додано',
      toast_avatar_bad_file:'Оберіть зображення', toast_avatar_updated:'Аватар оновлено',
      toast_reminders_off:'Нагадування вимкнено', toast_reminders_unsupported:'Браузер не підтримує сповіщення',
      toast_reminders_denied:'Дозвіл на сповіщення не надано', toast_reminders_on:'Нагадування увімкнено',
      toast_push_no_vapid:'Push ще не налаштовано (бракує VAPID-ключа)', toast_push_unsupported:'Браузер не підтримує push-сповіщення',
      toast_push_on:'Push-сповіщення увімкнено', toast_push_off:'Push-сповіщення вимкнено', toast_push_fail:'Не вдалося увімкнути push-сповіщення',
      reminder_body:'Ви записали свої операції сьогодні?',
      common_cancel:'Скасувати', common_done:'Готово', common_delete:'Видалити', common_name:'Назва', common_save:'Зберегти', common_edit:'Редагувати',
      toast_nickname_saved:'Нікнейм збережено',
      common_confirm_title:'Підтвердіть дію', common_yes:'Так', common_got_it:'Зрозуміло',
      cat_income:'Дохід', cat_expense:'Витрата', cat_other:'Інше',
      sync_saving:'Зберігаю...', sync_conflict:'Конфлікт',
      sync_conflict_msg:'Дані змінено на іншому пристрої з часу останнього завантаження тут. Записати поточну версію поверх, чи завантажити свіжі дані?',
      sync_conflict_title:'Конфлікт синхронізації', sync_conflict_overwrite:'Записати поверх', sync_conflict_load_fresh:'Завантажити свіже',
      sync_saved:'Збережено', sync_error:'Помилка', sync_autosave_error:'Помилка автозбереження',
      sync_loading:'Завантажую...', sync_synced:'Синхронізовано', sync_recur_added:'Додано', sync_recur_suffix:'повторюваних операцій',
      sync_offline:'Офлайн', sync_cloud_unavailable:'Хмара недоступна — локальні дані', sync_autosave:'Автозбереження...',
      recurring_comment_tag:'повторювана',
      auth_err_invalid_email:'Некоректний email.', auth_err_user_not_found:'Користувача не знайдено.',
      auth_err_wrong_password:'Невірний пароль.', auth_err_invalid_credential:'Невірний email або пароль.',
      auth_err_email_in_use:'Цей email вже зареєстровано.', auth_err_weak_password:'Пароль надто простий (мінімум 6 символів).',
      auth_err_too_many:'Забагато спроб. Спробуйте пізніше.', auth_err_popup_closed:'Вікно входу закрито.',
      auth_err_generic:'Помилка входу. Спробуйте ще раз.',
      auth_err_invalid_phone:'Некоректний номер телефону.', auth_err_invalid_code:'Невірний код із SMS.',
      auth_err_code_expired:'Код прострочено. Надішли новий.', auth_err_quota:'Забагато спроб. Спробуйте пізніше.',
      auth_err_phone_disabled:'Вхід за телефоном тимчасово недоступний.',
      auth_err_captcha:'Перевірка reCAPTCHA не пройшла. Перевір з\'єднання і спробуй ще раз.',
      auth_err_unauthorized_domain:'Цей сайт не додано до дозволених доменів Firebase — вхід за телефоном тут не працює.',
      auth_err_network:'Немає з\'єднання з мережею. Перевір інтернет і спробуй ще раз.',
      auth_err_sms_unavailable:'SMS тимчасово недоступні для цього номера (ліміт спроб або обмеження регіону). Спробуй ще раз пізніше або скористайся входом через email чи Google.',
      auth_enter_email:'Введіть email, щоб скинути пароль.', auth_reset_sent:'Лист для скидання пароля надіслано.',
      auth_signout_confirm:'Вийти з акаунту?', auth_signout_title:'Вихід', auth_signout_ok:'Вийти',
      auth_reauth_prompt:'Введи пароль ще раз, щоб підтвердити видалення акаунту:', auth_reauth_title:'Підтвердження',
      auth_delete_confirm:'Остаточно видалити акаунт і всі дані (зміни, фінанси, розрахунки)? Це незворотньо.', auth_delete_title:'Видалення акаунту',
      auth_delete_data_fail:'Не вдалося видалити дані акаунту. Спробуйте ще раз.',
      auth_delete_account_fail:'Дані видалено, але не вдалося видалити сам акаунт. Спробуй ще раз трохи згодом.',
      auth_delete_needs_login:'Дані видалено, але для видалення акаунту потрібен нещодавній вхід. Увійди ще раз і одразу повтори видалення акаунту.',
      auth_account_deleted:'Акаунт видалено',
      finance_pick_date:'Обери дату',
      finance_err_amount:'Введіть коректну суму', finance_err_amount_large:'Сума завелика', finance_err_date:'Оберіть дату', finance_err_date_format:'Некоректний формат дати', finance_err_wallet:'Оберіть гаманець', finance_err_comment_long:'Коментар занадто довгий', finance_err_field_long:'Назва категорії або підкатегорії занадто довга', finance_err_same_wallet:'Однакові рахунки',
      finance_delete_confirm:'Видалити цей запис?', finance_delete_title:'Видалити операцію',
      finance_edit_title:'Редагування операції', finance_save_changes:'Зберегти зміни',
      csv_empty:'Немає операцій для експорту', csv_date:'Дата', csv_type:'Тип', csv_currency:'Валюта',
      csv_to:'Куди', csv_transfer_amount:'Сума переказу', csv_transfer_currency:'Валюта переказу', csv_downloaded:'CSV завантажено',
      shifts_stat_earned:'Зароблено цього місяця', shifts_stat_hours:'Годин відпрацьовано', shifts_stat_this_month:'цього місяця',
      shifts_stat_shifts:'Змін', shifts_stat_workdays:'робочих днів', shifts_stat_off:'Вихідних', shifts_stat_marked:'позначених', shifts_chip_hours:'год',
      shifts_chart_title:'Динаміка заробітку — 6 місяців', shifts_quick_fill:'Швидке заповнення',
      shifts_template_type:'Тип зміни', shifts_template_pattern:'Періодичність',
      shifts_pattern_every:'Щодня', shifts_pattern_alt:'День через день', shifts_pattern_2_2:'2 через 2', shifts_pattern_3_3:'3 через 3',
      shifts_apply:'Застосувати', shifts_types_btn:'Типи змін', shifts_clear_month:'Очистити місяць',
      shifts_today:'Сьогодні', shifts_hint:'Натисни на день щоб редагувати зміни', shifts_goal_progress:'від цілі',
      shifts_pick_title:'Оберіть зміни', shifts_add_type:'Додати тип зміни',
      shifts_types_empty:'Немає типів змін', shifts_type_pay:'Оплата, грн', shifts_type_hours:'Години', shifts_type_off_label:'вихідний',
      shifts_default_name:'Зміна', shifts_add_type_prompt:'Назва типу зміни:', shifts_new_type_default:'Нова зміна',
      shifts_delete_type_confirm:'Видалити цей тип зміни? Його буде прибрано з усіх днів, де він був позначений.', shifts_delete_type_title:'Видалити тип',
      shifts_rest:'Відпочинок', shifts_hours_short:'год',
      shifts_clear_confirm:'Видалити всі зміни за цей місяць?', shifts_clear_ok:'Очистити', shifts_month_cleared:'Місяць очищено',
      shifts_add_type_first:'Спершу додай тип зміни', shifts_template_confirm:'Заповнити місяць шаблоном? Поточні зміни цього місяця буде замінено.',
      shifts_template_ok:'Заповнити', shifts_template_applied:'Шаблон застосовано',
      shifts_autofill_title:'Автозаповнення кожного дня', shifts_autofill_hint:'Коли настає новий день, потрібна зміна підставляється сама — без ручного заповнення місяця.',
      shifts_autofill_anchor:'Перша робоча зміна від', shifts_autofill_on:'Автозаповнення увімкнено', shifts_autofill_off:'Автозаповнення вимкнено',
      shifts_autofill_saved:'Налаштування збережено', shifts_autofill_added:'Автоматично заповнено змін:',
      wallets_empty:'Немає гаманців', wallets_default_name:'Гаманець', wallets_add_prompt:'Назва гаманця:', wallets_new_default:'Новий гаманець',
      wallets_need_one:'Має лишитись хоча б один гаманець', wallets_in_use:'Гаманець використовується в операціях',
      wallets_delete_confirm:'Видалити цей гаманець?', wallets_delete_title:'Видалити гаманець', wallets_add:'Додати гаманець',
      rates_desc:'Скільки гривень за 1 одиницю валюти. База — гривня (курс завжди 1).', rates_update_btn:'Оновити онлайн',
      rates_per_unit:'Грн за 1', rates_updated_at:'Оновлено —', rates_never_updated:'Курси ще не оновлювались онлайн',
      rates_update_success:'Курси валют оновлено', rates_update_fail:'Не вдалося отримати курси онлайн',
      rates_source_label:'Джерело курсу', rates_source_nbu:'НБУ (офіційний)', rates_source_privat:'ПриватБанк (готівка)',
      rates_source_nbu_short:'НБУ,', rates_source_privat_short:'ПриватБанк,',
      rates_source_hint:'ПриватБанк дає готівковий курс лише для USD/EUR; інші валюти — курс НБУ.',
      fx_widget_title:'Курси валют', fx_name_usd:'Долар США', fx_name_eur:'Євро', fx_name_gbp:'Фунт стерлінгів', fx_name_pln:'Злотий',
      fx_converter_title:'Конвертер валют', fx_converter_amount:'Сума', fx_converter_from:'З', fx_converter_to:'В',
      qa_rates:'Курси', qa_converter:'Конвертер', qa_analytics:'Аналітика', qa_transaction:'Операція', qa_tools:'Інструменти',
      tools_modal_title:'Інструменти',
      settings_widgets:'Віджети', settings_widgets_sub:'Що показувати на вкладці Фінанси', widgets_desc:'Увімкни, вимкни й переставляй блоки на вкладці "Фінанси".',
      widgets_move_up:'Вище', widgets_move_down:'Нижче',
      widgets_item_rates:'Курси валют', widgets_item_rates_sub:'Компактний список курсів', widgets_item_converter:'Конвертер валют', widgets_item_converter_sub:'Швидкий перерахунок суми',
      widgets_item_analytics:'Аналітика', widgets_item_analytics_sub:'Витрати й доходи за категоріями', widgets_item_chart:'Графік балансу', widgets_item_chart_sub:'Баланс за останні 6 місяців',
      widgets_item_goals:'Цілі', widgets_item_goals_sub:'Прогрес накопичення на гаманцях',
      settings_premium:'Преміум', premium_free_plan:'Безкоштовний план', premium_active_plan:'Преміум активний',
      premium_title:'Rytm Преміум', premium_subtitle:'Rytm зараз повністю безкоштовний — без лімітів. Ось що ми додаємо далі:',
      premium_perk_free_now:'Вже доступно безкоштовно', premium_perk_free_now_sub:'Необмежено гаманців, категорій, правил і повторюваних платежів + push-сповіщення',
      premium_perk_banks:'Інтеграція з банками', premium_perk_banks_sub:'Автоматичне підвантаження операцій — у розробці', premium_soon_badge:'Скоро',
      premium_limit_wallets:'Безкоштовний план дозволяє до 3 гаманців. Преміум знімає це обмеження.',
      premium_limit_categories:'Безкоштовний план дозволяє до 8 категорій одного типу. Преміум знімає це обмеження.',
      premium_limit_rules:'Безкоштовний план дозволяє до 3 авто-правил. Преміум знімає це обмеження.',
      premium_limit_recurring:'Безкоштовний план дозволяє до 3 повторюваних платежів. Преміум знімає це обмеження.',
      premium_limit_goals:'Безкоштовний план дозволяє 1 ціль. Преміум знімає це обмеження.',
      profiles_default_name:'Профіль 1',
      settings_profiles:'Профілі', settings_profiles_sub:'Перемикайтеся між окремими наборами даних',
      profiles_manager_title:'Профілі', profiles_active_badge:'Активний', profiles_switch_btn:'Перемкнути',
      profiles_add_btn:'Додати профіль', profiles_add_prompt:'Назва профілю', profiles_add_title:'Новий профіль',
      profiles_rename_prompt:'Нова назва профілю', profiles_rename_title:'Перейменувати профіль',
      profiles_delete_confirm:'Видалити профіль зі списку? Його дані залишаться в хмарі, але зникнуть з переліку профілів.',
      profiles_delete_title:'Видалити профіль',
      profiles_delete_active_blocked:'Неможливо видалити активний профіль. Спочатку перемкніться на інший.',
      profiles_delete_last_blocked:'Повинен залишитися хоча б один профіль.',
      profiles_switch_confirm:'Перемкнутися на цей профіль? Поточні дані буде збережено.',
      profiles_switch_title:'Перемкнути профіль', profiles_switched_toast:'Профіль перемкнено',
      profiles_share_btn:'Поділитись поточним профілем', profiles_join_btn:'Приєднатися за кодом',
      profiles_shared_badge:'Спільний', profiles_share_not_owner:'Не можна поділитись чужим спільним профілем',
      profiles_share_code_title:'Профіль тепер спільний', profiles_share_code_msg:'Надішли цей код тому, з ким хочеш поділитись профілем:',
      profiles_share_code_hint:'Код діє 24 години й одноразовий. Приєднатись можна через "Приєднатися за кодом" у Профілях.',
      profiles_join_title:'Приєднатися за кодом', profiles_join_prompt:'Введи код запрошення',
      profiles_join_err_own:'Це твій власний профіль', profiles_join_err_used:'Цей код уже використано',
      profiles_join_err_expired:'Код прострочено — попроси новий', profiles_join_err_generic:'Код не знайдено',
      profiles_join_success:'Ви приєднались до спільного профілю',
      profiles_leave_title:'Покинути спільний профіль', profiles_leave_btn:'Покинути',
      profiles_leave_confirm:'Покинути цей спільний профіль? Ти втратиш доступ до його даних, поки тебе не запросять знову.',
      profiles_left_toast:'Ви покинули спільний профіль',
      profiles_members_btn:'Учасники', profiles_members_title:'Учасники профілю',
      profiles_members_none:'Ще ніхто не приєднався. Поділись профілем, щоб запросити когось.',
      profiles_members_owner_label:'Ви (власник)',
      profiles_member_role_editor:'Редактор', profiles_member_role_viewer:'Тільки перегляд',
      profiles_member_make_viewer:'Зробити тільки переглядачем', profiles_member_make_editor:'Зробити редактором',
      profiles_member_role_changed:'Роль учасника змінено',
      shared_profile_readonly:'У тебе лише перегляд цього спільного профілю — редагувати не можна',
      profiles_avatar_pick:'Оберіть аватар', color_pick_title:'Оберіть колір',
      cat_icon_pick_title:'Оберіть іконку',
      finance_goals_title:'Цілі', settings_goals:'Цілі', settings_goals_sub:'Накопичення на гаманці з ціллю',
      goals_desc:'Прив\'яжи ціль до гаманця — прогрес рахується від його поточного балансу.',
      goals_add:'Додати ціль', goals_add_confirm:'Додати', goals_empty:'Немає цілей', goals_target:'Ціль', goals_target_date:'Дата (необов\'язково)',
      goals_reached:'Ціль досягнуто!', goals_added:'Ціль додано', goals_need_wallet:'Спочатку додай гаманець',
      goals_delete_confirm:'Видалити цю ціль?', goals_delete_title:'Видалення цілі',
      cat_empty:'Немає категорій', cat_subcat_short:'Підкат.', cat_this_month:'грн цього міс.', cat_drag_title:'Перетягни, щоб змінити порядок',
      cat_no_subcats:'Немає підкатегорій', cat_subcategory:'Підкатегорія',
      cat_add_subcat_prompt:'Назва підкатегорії:', cat_add_subcat_title:'Нова підкатегорія', cat_subcat_exists:'Така підкатегорія вже є',
      cat_delete_subcat_confirm:'Видалити цю підкатегорію? Старі операції збережуть свою назву.', cat_delete_subcat_title:'Видалити підкатегорію',
      cat_add_prompt:'Назва категорії:', cat_add_title:'Нова категорія', cat_already_exists:'Така категорія вже є',
      cat_delete_confirm:'Видалити категорію? Старі операції збережуть свою назву.', cat_delete_title:'Видалити категорію',
      cat_act_edit:'Редагувати', cat_act_show_tx:'Показати транзакції', cat_act_delete:'Видалити категорію',
      cat_showing:'Показано:', cat_add:'Додати категорію',
      budgets_empty:'Немає категорій витрат', budgets_limit_label:'Ліміт/міс, грн', budgets_over_by:'Перевищено на',
      budgets_title:'Бюджети по категоріях', budgets_desc:'Місячний ліміт витрат на категорію. Залиш порожнім або 0 — без бюджету.',
      budgets_no_limit:'Без ліміту',
      recurring_empty:'Немає повторюваних операцій', recurring_type:'Тип', recurring_amount:'Сума',
      recurring_frequency:'Частота', recurring_daily:'Щодня', recurring_weekly:'Щотижня', recurring_monthly:'Щомісяця',
      recurring_next_date:'Наступного разу', recurring_active:'активна', recurring_paused_label:'на паузі',
      recurring_added:'Шаблон додано — заповни суму', recurring_delete_confirm:'Видалити цю повторювану операцію? Вже додані транзакції залишаться.',
      recurring_delete_title:'Видалити шаблон', recurring_desc:'Автоматично додаються при вході в застосунок, коли настає дата.', recurring_add:'Додати повторювану операцію',
      tags_empty:'Немає тегів', tags_default_name:'Тег', tags_add_prompt:'Назва тегу:', tags_add_title:'Новий тег',
      tags_delete_confirm:'Видалити цей тег? Він буде прибраний зі старих операцій.', tags_delete_title:'Видалити тег',
      tags_desc:'Довільні мітки на операції, окремо від категорій — на одну операцію можна повісити кілька.', tags_add:'Додати тег',
      rules_empty:'Немає правил', rules_keyword:'Слово в коментарі', rules_keyword_placeholder:'напр. Сільпо',
      rules_delete_confirm:'Видалити це правило?', rules_delete_title:'Видалити правило', rules_auto_applied:'Категорія визначена автоматично:',
      ai_category_suggested:'ШІ на пристрої підказав категорію:',
      receipt_scan_btn:'Сканувати чек', receipt_scan_processing:'Розпізнаю чек…',
      receipt_scan_done:'Чек розпізнано', receipt_scan_not_found:'Не вдалося розпізнати суму чи дату — перевір вручну',
      receipt_scan_fail:'Не вдалося розпізнати чек',
      receipt_scan_timeout:'Розпізнавання зайняло надто багато часу — спробуй чіткіше фото зблизька',
      rules_desc:'Якщо коментар містить ключове слово — категорія підставляється сама, поки ти вводиш операцію.', rules_add:'Додати правило',
      debt_stat_start:'Початкова сума', debt_stat_balance:'Поточний залишок', debt_stat_paid:'Сплачено', debt_stat_count:'Платежів',
      debt_stat_due:'Термін сплати', debt_due_date:'Дата, до якої треба віддати',
      debt_due_in_days:'через {n} дн.', debt_due_today:'сьогодні', debt_due_overdue_days:'прострочено на {n} дн.',
      debt_progress_label:'Сплачено',
      debt_forecast_title:'Прогноз погашення',
      debt_forecast_pace:'За поточним темпом лишилось ще ≈ {n} платежів.',
      debt_forecast_avg:'Середній платіж — {amt}.',
      debt_forecast_done:'Розрахунок повністю погашено.',
      debt_forecast_no_pace:'Замало даних, щоб оцінити темп погашення.',
      debt_info_title:'Дані розрахунку', debt_name:'Назва', debt_name_placeholder:'Напр. Позика, Розстрочка…',
      debt_note:'Нотатка', debt_note_placeholder:'Додаткова нотатка...', debt_currency:'Валюта', debt_delete:'Видалити цей розрахунок',
      debt_new_payment:'Новий платіж', debt_amount:'Сума', debt_amount_placeholder:'напр. 500 або ***68',
      debt_balance_after:'Залишок після оплати', debt_balance_auto:'(рахується автоматично)', debt_auto_placeholder:'авто',
      debt_date_label:'Дата / мітка', debt_date_placeholder:'напр. 10.02.2026 або Березень', debt_add_payment:'Додати платіж', debt_add_calc:'Створити розрахунок',
      debt_history_title:'Історія платежів',
      debt_add_prompt:'Назва розрахунку:', debt_new_default:'Новий розрахунок', debt_added:'Розрахунок додано',
      debt_need_one:'Має лишитись хоча б один розрахунок', debt_delete_confirm:'Видалити цей розрахунок разом з усією історією платежів?', debt_delete_title:'Видалити розрахунок',
      debt_default_name:'Розрахунок',
      debt_enter_amount:'Введіть суму платежу', debt_enter_balance:'Введіть залишок після оплати', debt_payment_added:'Платіж додано',
      debt_delete_payment_confirm:'Видалити цей платіж?', debt_delete_payment_title:'Видалити платіж',
      debt_no_payments:'Платежів немає', debt_expected:'Очікувано', debt_discrepancy:'розбіжність',
      pin_locked_title:'Rytm заблоковано', pin_code:'PIN-код', pin_unlock:'Розблокувати', pin_forgot:'Забув PIN?',
      onboard_skip:'Пропустити', onboard_next:'Далі', onboard_start:'Почати',
      onboard_1_title:'Графік змін', onboard_1_desc:'Веди робочий календар: типи змін, кольори й автоматичний розрахунок зарплати за місяць.',
      onboard_2_title:'Фінанси в одному місці', onboard_2_desc:'Гаманці в різних валютах, категорії, бюджети та повна історія операцій.',
      onboard_3_title:'Курси та конвертер валют', onboard_3_desc:'Живі курси НБУ чи ПриватБанку і вбудований конвертер — завжди під рукою.',
      onboard_4_title:'Розрахунки', onboard_4_desc:'Веди облік боргів і розстрочок з історією платежів по кожному з них.',
      onboard_5_title:'Захист і налаштування', onboard_5_desc:'PIN-код, Face ID / Touch ID, а ще — обирай, які віджети показувати на вкладці "Фінанси".',
      pin_new:'Новий PIN (4–6 цифр)', pin_repeat:'Повторіть PIN', pin_set:'Встановити PIN', pin_lock_now:'Заблокувати зараз',
      pin_bio_enable:'Увімкнути Face ID / Touch ID', pin_bio_disable:'Вимкнути Face ID / Touch ID', pin_remove:'Прибрати PIN',
      pin_disclaimer:'PIN зберігається лише на цьому пристрої й не заміняє вхід в акаунт.',
      pin_enter:'Введіть PIN', pin_wrong:'Невірний PIN',
      pin_forgot_confirm:'Скинути PIN на цьому пристрої і вийти з акаунту? Після повторного входу зможеш встановити новий PIN.',
      pin_forgot_title:'Забули PIN?', pin_forgot_ok:'Скинути й вийти',
      pin_status_set:'PIN уже встановлено на цьому пристрої. Введи новий, щоб змінити його.', pin_status_unset:'PIN ще не встановлено на цьому пристрої.',
      pin_len_error:'PIN має бути з 4-6 цифр', pin_mismatch:'PIN не збігається', pin_set_success:'PIN встановлено',
      pin_removed:'PIN вимкнено', pin_set_first:'Спершу встанови PIN',
      pin_bio_on:'Face ID / Touch ID увімкнено', pin_bio_fail:'Не вдалося увімкнути біометрію', pin_bio_off:'Face ID / Touch ID вимкнено',
      analytics_title:'Аналітика витрат і доходів', analytics_by_expense_category:'Витрати за категоріями', analytics_by_income_category:'Доходи за категоріями',
      analytics_period_month:'Цей місяць', analytics_period_prev:'Минулий місяць', analytics_period_3m:'3 місяці', analytics_period_all:'Весь час',
      analytics_income:'Дохід', analytics_expense:'Витрата', analytics_net:'Різниця', analytics_savings_rate:'Норма заощаджень',
      analytics_prefix:'Витрачаєш на', analytics_suffix_more:'більше, ніж попереднього періоду', analytics_suffix_less:'менше, ніж попереднього періоду',
      analytics_same:'Витрати на рівні попереднього періоду', analytics_no_data:'Немає даних',
      analytics_cat_insight_prefix:'Найбільше зросли витрати на', analytics_cat_insight_suffix:'цього періоду',
      settings_wallets_sub:'Картки, готівка та інші рахунки', settings_rates_sub:'Автоматичне оновлення з НБУ',
      settings_categories_sub:'Власні категорії доходів і витрат', settings_tags_sub:'Мітки для операцій, окремо від категорій',
      settings_budgets_sub:'Місячні ліміти витрат по категоріях', settings_recurring_sub:'Автоматичні платежі за розкладом',
      settings_rules_sub:'Категорія за ключовим словом у коментарі', settings_export_sub:'Вивантажити всі операції у файл',
      settings_import_sub:'Завантажити операції з файлу, експортованого звідси',
      csv_import_empty:'У файлі немає операцій для імпорту', csv_import_bad_header:'Це не файл, експортований з Rytm',
      csv_import_row_short:'Рядок пропущено — недостатньо колонок', csv_import_bad_type:'Невідомий тип операції',
      csv_import_unknown_wallet:'Гаманець не знайдено', csv_import_confirm_title:'Імпорт CSV',
      csv_import_confirm_prefix:'Буде додано операцій:', csv_import_skip_prefix:'Пропущено через помилки:',
      csv_import_confirm_ok:'Імпортувати', csv_import_none_valid:'Жодного рядка не вдалося імпортувати. Помилок:',
      csv_import_read_fail:'Не вдалося прочитати файл', csv_import_done:'Імпортовано операцій:',
      settings_pin_sub:'Захисти застосунок кодом і Face ID/Touch ID', settings_notif_title:'Нагадування про облік',
      settings_signout_sub:'Завершити сеанс на цьому пристрої', settings_delete_account_sub:'Остаточно видалити акаунт і всі дані',
      settings_terms_sub:'Правила користування застосунком', settings_privacy_sub:'Як ми обробляємо твої дані',
      a11y_confirm:'Підтвердити', a11y_remove:'Прибрати', a11y_close:'Закрити', a11y_more:'Більше дій', a11y_prev_month:'Попередній місяць', a11y_next_month:'Наступний місяць',
    },
    en:{
      settings_kicker:'App settings', settings_title:'Settings',
      settings_search_placeholder:'Search: wallets, categories, push…', settings_search_empty:'No settings found',
      settings_group_all:'All', settings_group_account:'Account', settings_group_finance:'Finance', settings_group_security:'Security', settings_group_app:'Appearance',
      settings_tip_search:'Tip: use the search box or the group chips above to find a setting faster.',
      settings_profile:'Profile', settings_nickname_placeholder:'Nickname', settings_avatar_picker_label:'Or pick a profile icon',
      settings_finance:'Finance', settings_wallets:'Wallets', settings_rates:'Exchange rates',
      settings_categories:'Categories', settings_tags:'Tags', settings_budgets:'Budgets',
      settings_recurring:'Recurring transactions', settings_rules:'Auto-categorization rules', settings_export:'Export CSV',
      settings_import:'Import CSV',
      settings_security:'Security', settings_pin:'PIN lock',
      settings_notifications:'Notifications', settings_notif_enable:'Enable reminders', settings_notif_disable:'Disable reminders',
      settings_notif_hint:"We'll remind you if no transaction was logged today. Only works while the app has been opened in the browser at least once that day.",
      settings_notif_budget_title:'Budget exceeded', settings_notif_budget_hint:"We'll notify you when a category's spending goes over its monthly budget.",
      settings_notif_recurring_title:'Upcoming payment', settings_notif_recurring_hint:"We'll warn you a day before a recurring transaction auto-adds.",
      settings_notif_debt_title:'Debt due soon', settings_notif_debt_hint:"We'll warn you a day before a debt's due date.",
      settings_notif_push_title:'Push notifications', settings_notif_push_hint:'Delivered even when the app is closed.',
      notif_budget_title:'Budget exceeded', notif_budget_body:'Spending in "{category}" has gone over its monthly budget.',
      notif_recurring_title:'Upcoming payment', notif_recurring_body:'Tomorrow "{category}" will be auto-added for {amount}.',
      notif_debt_title:'Debt due soon', notif_debt_body:'Tomorrow is the due date to pay off "{name}".',
      settings_appearance:'Appearance', theme_dark:'Dark', theme_light:'Light',
      privacy_cache_title:'Local data cache', privacy_cache_hint_on:'Faster startup: financial data is stored on this device.',
      privacy_cache_hint_off:'Private mode: financial cache is cleared and data loads from the cloud after sign-in.',
      privacy_cache_cleared:'Local financial cache cleared', privacy_cache_on:'Local cache enabled',
      settings_account:'Account', settings_signout:'Sign out', settings_delete_account:'Delete account',
      settings_phone:'Phone number', settings_phone_sub_empty:'Not added',
      settings_phone_remove:'Remove number', settings_phone_remove_confirm:'Remove this phone number from your account?',
      settings_phone_remove_title:'Remove number', settings_phone_linked:'Phone number added',
      settings_phone_removed:'Phone number removed', settings_phone_remove_fail:'Failed to remove the number',
      settings_about:'About', settings_terms:'Terms of use', settings_privacy:'Privacy policy',
      settings_footer:'Rytm — shift, finance & settlement tracker', settings_signed_in_as:'Signed in as',
      auth_tagline:'Finances, shifts and settlements in one place',
      auth_login_tab:'Sign in', auth_register_tab:'Sign up', auth_password:'Password', auth_password_placeholder:'At least 6 characters',
      auth_login_btn:'Sign in', auth_register_btn:'Sign up', auth_forgot:'Forgot password?', auth_or:'or',
      auth_google:'Continue with Google', auth_terms_pre:'By signing up, you agree to the', auth_terms_and:'and',
      auth_phone_btn:'Sign in with phone number', auth_phone_label:'Phone number', auth_phone_send:'Send code',
      auth_phone_code_label:'SMS code', auth_phone_verify:'Verify', auth_phone_back:'← Back',
      auth_phone_code_sent:'Code sent', auth_phone_bad_format:'Enter a number in +380XXXXXXXXX format',
      auth_phone_enter_code:'Enter the SMS code',
      topbar_sub:'Shifts · Finance · Cloud',
      nav_shifts:'Shifts', nav_finance:'Finance', nav_debt:'Settlements', nav_settings:'Settings', nav_shopping:'Shopping',
      shopping_kicker:'Your shopping', shopping_title:'Shopping list',
      shopping_add_placeholder:'Item name', shopping_qty_placeholder:'Qty', shopping_add_btn:'Add',
      shopping_empty:'The list is empty', shopping_clear_bought:'Clear bought',
      shopping_stat_remaining:'Remaining', shopping_stat_bought:'Bought',
      shopping_clear_confirm:'Remove all bought items from the list?', shopping_clear_title:'Clear bought',
      shifts_kicker:'Personal work calendar', shifts_title:'Work shifts',
      finance_kicker:'Personal budget', finance_title:'Wallets & Finance',
      debt_kicker:'Settlement tracking', debt_title:'Settlements',
      finance_goto_settings:'Customize screen', finance_budgets_title:"This month's budgets",
      finance_empty_title:'No transactions yet', finance_empty_desc:'Add your first income or expense to see balance, history, and analytics.',
      finance_start_title:'Start in a minute', finance_start_desc:'Three steps to make Rytm useful right away.',
      finance_start_wallet:'Check wallet', finance_start_wallet_sub:'Name, currency, balance',
      finance_start_tx:'Add transaction', finance_start_tx_sub:'First income or expense',
      finance_start_widgets:'Customize screen', finance_start_widgets_sub:'Hide unnecessary blocks',
      shifts_empty_title:'Calendar is empty', shifts_empty_desc:'Tap a calendar day or apply a template to fill your schedule faster.',
      debt_empty_title:'No payments yet', debt_empty_desc:'Add the first payment to track balance, progress, and settlement history.',
      debt_empty_no_calc_title:'No settlements yet', debt_empty_no_calc_desc:'Create your first settlement for a loan, installment, or shared expense.',
      shopping_empty_title:'The list is empty', shopping_empty_desc:'Add items before going shopping and check them off with one tap.',
      finance_chart_title:'Balance over the last 6 months', finance_new_tx:'New transaction', common_cancel:'Cancel',
      finance_chart_net:'Balance', finance_chart_income:'Income', finance_chart_expense:'Expense',
      finance_chart_forecast:'Forecast', finance_chart_forecast_tip:'forecast', finance_chart_avg:'Average',
      finance_chart_best:'Best', finance_chart_worst:'Worst',
      finance_total_balance:'Total balance (UAH)', finance_total_balance_approx:'Approx. balance (UAH)', finance_total_balance_hint:'Converted to UAH using the current wallet exchange rates.',
      finance_month_income:'Income this month', finance_month_expense:'Expense this month',
      finance_type_income:'+ Income', finance_type_expense:'− Expense', finance_type_transfer:'⇄ Transfer',
      finance_wallet:'Wallet', finance_wallet_expense:'Pay from', finance_wallet_transfer:'Transfer from',
      finance_wallet_target:'Transfer to', finance_category:'Category', finance_subcategory:'Subcategory',
      finance_transfer_hint:'Approx. at current rate:', finance_transfer_same_wallet_hint:'Choose a different wallet for the transfer.',
      finance_tags:'Tags', finance_date:'Date', finance_comment:'Comment', finance_comment_placeholder:'Transaction details...',
      finance_amount_prefix:'Amount', finance_add_btn:'Add entry', finance_history_title:'Transaction history',
      finance_filter_all:'All', finance_filter_reset:'Reset ✕', finance_records_suffix:'records', finance_no_records:'No records yet',
      finance_search_placeholder:'Search by comment, category, wallet…',
      finance_search_empty_title:'Nothing found', finance_search_empty_desc:'Change the filter or search query to see transactions.',
      finance_view_all:'View all', finance_show_less:'Show less',
      toast_tx_updated:'Entry updated', toast_transfer_done:'Transfer completed', toast_tx_added:'Entry added',
      toast_avatar_bad_file:'Please choose an image', toast_avatar_updated:'Avatar updated',
      toast_reminders_off:'Reminders disabled', toast_reminders_unsupported:"Browser doesn't support notifications",
      toast_reminders_denied:'Notification permission not granted', toast_reminders_on:'Reminders enabled',
      toast_push_no_vapid:'Push isn’t configured yet (missing VAPID key)', toast_push_unsupported:'This browser doesn’t support push notifications',
      toast_push_on:'Push notifications enabled', toast_push_off:'Push notifications disabled', toast_push_fail:'Couldn’t enable push notifications',
      reminder_body:'Did you record your transactions today?',
      common_cancel:'Cancel', common_done:'Done', common_delete:'Delete', common_name:'Name', common_save:'Save', common_edit:'Edit',
      toast_nickname_saved:'Nickname saved',
      common_confirm_title:'Please confirm', common_yes:'Yes', common_got_it:'Got it',
      cat_income:'Income', cat_expense:'Expense', cat_other:'Other',
      sync_saving:'Saving...', sync_conflict:'Conflict',
      sync_conflict_msg:'Data was changed on another device since it was last loaded here. Overwrite with the current version, or load the fresh data?',
      sync_conflict_title:'Sync conflict', sync_conflict_overwrite:'Overwrite', sync_conflict_load_fresh:'Load fresh',
      sync_saved:'Saved', sync_error:'Error', sync_autosave_error:'Autosave error',
      sync_loading:'Loading...', sync_synced:'Synced', sync_recur_added:'Added', sync_recur_suffix:'recurring transactions',
      sync_offline:'Offline', sync_cloud_unavailable:'Cloud unavailable — local data', sync_autosave:'Autosaving...',
      recurring_comment_tag:'recurring',
      auth_err_invalid_email:'Invalid email.', auth_err_user_not_found:'User not found.',
      auth_err_wrong_password:'Wrong password.', auth_err_invalid_credential:'Invalid email or password.',
      auth_err_email_in_use:'This email is already registered.', auth_err_weak_password:'Password is too weak (minimum 6 characters).',
      auth_err_too_many:'Too many attempts. Try again later.', auth_err_popup_closed:'Sign-in window closed.',
      auth_err_generic:'Sign-in error. Please try again.',
      auth_err_invalid_phone:'Invalid phone number.', auth_err_invalid_code:'Wrong SMS code.',
      auth_err_code_expired:'Code expired. Request a new one.', auth_err_quota:'Too many attempts. Try again later.',
      auth_err_phone_disabled:'Phone sign-in is temporarily unavailable.',
      auth_err_captcha:'reCAPTCHA check failed. Check your connection and try again.',
      auth_err_unauthorized_domain:'This site isn\'t on Firebase\'s authorized domains list — phone sign-in won\'t work here.',
      auth_err_network:'No network connection. Check your internet and try again.',
      auth_err_sms_unavailable:'SMS is temporarily unavailable for this number (rate limit or regional restriction). Try again later, or sign in with email or Google instead.',
      auth_enter_email:'Enter your email to reset the password.', auth_reset_sent:'Password reset email sent.',
      auth_signout_confirm:'Sign out of your account?', auth_signout_title:'Sign out', auth_signout_ok:'Sign out',
      auth_reauth_prompt:'Enter your password again to confirm account deletion:', auth_reauth_title:'Confirmation',
      auth_delete_confirm:'Permanently delete your account and all data (shifts, finances, settlements)? This cannot be undone.', auth_delete_title:'Delete account',
      auth_delete_data_fail:'Failed to delete account data. Please try again.',
      auth_delete_account_fail:'Data deleted, but the account itself could not be deleted. Try again shortly.',
      auth_delete_needs_login:'Data deleted, but deleting the account needs a recent sign-in. Sign in again and repeat the account deletion right away.',
      auth_account_deleted:'Account deleted',
      finance_pick_date:'Pick a date',
      finance_err_amount:'Enter a valid amount', finance_err_amount_large:'Amount is too large', finance_err_date:'Choose a date', finance_err_date_format:'Invalid date format', finance_err_wallet:'Choose a wallet', finance_err_comment_long:'Comment is too long', finance_err_field_long:'Category or subcategory name is too long', finance_err_same_wallet:'Same account',
      finance_delete_confirm:'Delete this entry?', finance_delete_title:'Delete transaction',
      finance_edit_title:'Edit transaction', finance_save_changes:'Save changes',
      csv_empty:'No transactions to export', csv_date:'Date', csv_type:'Type', csv_currency:'Currency',
      csv_to:'To', csv_transfer_amount:'Transfer amount', csv_transfer_currency:'Transfer currency', csv_downloaded:'CSV downloaded',
      shifts_stat_earned:'Earned this month', shifts_stat_hours:'Hours worked', shifts_stat_this_month:'this month',
      shifts_stat_shifts:'Shifts', shifts_stat_workdays:'work days', shifts_stat_off:'Days off', shifts_stat_marked:'marked', shifts_chip_hours:'hrs',
      shifts_chart_title:'Earnings trend — 6 months', shifts_quick_fill:'Quick fill',
      shifts_template_type:'Shift type', shifts_template_pattern:'Repeat pattern',
      shifts_pattern_every:'Every day', shifts_pattern_alt:'Every other day', shifts_pattern_2_2:'2 on 2 off', shifts_pattern_3_3:'3 on 3 off',
      shifts_apply:'Apply', shifts_types_btn:'Shift types', shifts_clear_month:'Clear month',
      shifts_today:'Today', shifts_hint:'Tap a day to edit shifts', shifts_goal_progress:'of goal',
      shifts_pick_title:'Choose shifts', shifts_add_type:'Add shift type',
      shifts_types_empty:'No shift types', shifts_type_pay:'Pay, UAH', shifts_type_hours:'Hours', shifts_type_off_label:'day off',
      shifts_default_name:'Shift', shifts_add_type_prompt:'Shift type name:', shifts_new_type_default:'New shift',
      shifts_delete_type_confirm:'Delete this shift type? It will be removed from every day it was marked on.', shifts_delete_type_title:'Delete type',
      shifts_rest:'Rest', shifts_hours_short:'hrs',
      shifts_clear_confirm:'Delete all shifts for this month?', shifts_clear_ok:'Clear', shifts_month_cleared:'Month cleared',
      shifts_add_type_first:'Add a shift type first', shifts_template_confirm:'Fill the month with this template? The current shifts this month will be replaced.',
      shifts_template_ok:'Fill', shifts_template_applied:'Template applied',
      shifts_autofill_title:'Auto-fill every day', shifts_autofill_hint:'When a new day starts, the right shift is added automatically — no need to fill in the whole month by hand.',
      shifts_autofill_anchor:'First work shift from', shifts_autofill_on:'Auto-fill turned on', shifts_autofill_off:'Auto-fill turned off',
      shifts_autofill_saved:'Settings saved', shifts_autofill_added:'Auto-filled shifts:',
      wallets_empty:'No wallets', wallets_default_name:'Wallet', wallets_add_prompt:'Wallet name:', wallets_new_default:'New wallet',
      wallets_need_one:'At least one wallet must remain', wallets_in_use:'This wallet is used in transactions',
      wallets_delete_confirm:'Delete this wallet?', wallets_delete_title:'Delete wallet', wallets_add:'Add wallet',
      rates_desc:'How many hryvnias per 1 unit of currency. Base is UAH (rate always 1).', rates_update_btn:'Update online',
      rates_per_unit:'UAH per 1', rates_updated_at:'Updated —', rates_never_updated:'Rates have not been updated online yet',
      rates_update_success:'Exchange rates updated', rates_update_fail:'Failed to fetch rates online',
      rates_source_label:'Rate source', rates_source_nbu:'NBU (official)', rates_source_privat:'PrivatBank (cash)',
      rates_source_nbu_short:'NBU,', rates_source_privat_short:'PrivatBank,',
      rates_source_hint:'PrivatBank only provides cash rates for USD/EUR; other currencies use the NBU rate.',
      fx_widget_title:'Exchange rates', fx_name_usd:'US Dollar', fx_name_eur:'Euro', fx_name_gbp:'British Pound', fx_name_pln:'Polish Zloty',
      fx_converter_title:'Currency converter', fx_converter_amount:'Amount', fx_converter_from:'From', fx_converter_to:'To',
      qa_rates:'Rates', qa_converter:'Converter', qa_analytics:'Analytics', qa_transaction:'Transaction', qa_tools:'Tools',
      tools_modal_title:'Tools',
      settings_widgets:'Widgets', settings_widgets_sub:'What shows on the Finance tab', widgets_desc:'Turn Finance-tab blocks on or off, and reorder them.',
      widgets_move_up:'Move up', widgets_move_down:'Move down',
      widgets_item_rates:'Exchange rates', widgets_item_rates_sub:'Compact rates list', widgets_item_converter:'Currency converter', widgets_item_converter_sub:'Quick amount conversion',
      widgets_item_analytics:'Analytics', widgets_item_analytics_sub:'Income and expenses by category', widgets_item_chart:'Balance chart', widgets_item_chart_sub:'Balance over the last 6 months',
      widgets_item_goals:'Goals', widgets_item_goals_sub:'Savings progress on your wallets',
      settings_premium:'Premium', premium_free_plan:'Free plan', premium_active_plan:'Premium active',
      premium_title:'Rytm Premium', premium_subtitle:"Rytm is fully free right now — no limits. Here's what's next:",
      premium_perk_free_now:'Already free', premium_perk_free_now_sub:'Unlimited wallets, categories, rules, and recurring payments + push notifications',
      premium_perk_banks:'Bank integrations', premium_perk_banks_sub:'Automatic transaction import — in development', premium_soon_badge:'Soon',
      premium_limit_wallets:'The free plan allows up to 3 wallets. Premium removes this limit.',
      premium_limit_categories:'The free plan allows up to 8 categories per type. Premium removes this limit.',
      premium_limit_rules:'The free plan allows up to 3 auto-rules. Premium removes this limit.',
      premium_limit_recurring:'The free plan allows up to 3 recurring payments. Premium removes this limit.',
      premium_limit_goals:'The free plan allows 1 goal. Premium removes this limit.',
      profiles_default_name:'Profile 1',
      settings_profiles:'Profiles', settings_profiles_sub:'Switch between separate sets of data',
      profiles_manager_title:'Profiles', profiles_active_badge:'Active', profiles_switch_btn:'Switch',
      profiles_add_btn:'Add profile', profiles_add_prompt:'Profile name', profiles_add_title:'New profile',
      profiles_rename_prompt:'New profile name', profiles_rename_title:'Rename profile',
      profiles_delete_confirm:'Remove this profile from the list? Its data stays in the cloud but disappears from the profile list.',
      profiles_delete_title:'Delete profile',
      profiles_delete_active_blocked:'Cannot delete the active profile. Switch to another one first.',
      profiles_delete_last_blocked:'At least one profile must remain.',
      profiles_switch_confirm:'Switch to this profile? Current data will be saved first.',
      profiles_switch_title:'Switch profile', profiles_switched_toast:'Profile switched',
      profiles_share_btn:'Share current profile', profiles_join_btn:'Join via code',
      profiles_shared_badge:'Shared', profiles_share_not_owner:'Can\'t share a profile that isn\'t yours',
      profiles_share_code_title:'Profile is now shared', profiles_share_code_msg:'Send this code to whoever you want to share the profile with:',
      profiles_share_code_hint:'The code is valid for 24 hours and works once. They can join via "Join via code" in Profiles.',
      profiles_join_title:'Join via code', profiles_join_prompt:'Enter the invite code',
      profiles_join_err_own:'That\'s your own profile', profiles_join_err_used:'This code has already been used',
      profiles_join_err_expired:'The code expired — ask for a new one', profiles_join_err_generic:'Code not found',
      profiles_join_success:'You joined the shared profile',
      profiles_leave_title:'Leave shared profile', profiles_leave_btn:'Leave',
      profiles_leave_confirm:'Leave this shared profile? You\'ll lose access to its data until invited again.',
      profiles_left_toast:'You left the shared profile',
      profiles_members_btn:'Members', profiles_members_title:'Profile members',
      profiles_members_none:'No one has joined yet. Share the profile to invite someone.',
      profiles_members_owner_label:'You (owner)',
      profiles_member_role_editor:'Editor', profiles_member_role_viewer:'View only',
      profiles_member_make_viewer:'Make view-only', profiles_member_make_editor:'Make editor',
      profiles_member_role_changed:'Member role changed',
      shared_profile_readonly:'You have view-only access to this shared profile — editing is disabled',
      profiles_avatar_pick:'Choose an avatar', color_pick_title:'Choose a color',
      cat_icon_pick_title:'Choose an icon',
      finance_goals_title:'Goals', settings_goals:'Goals', settings_goals_sub:'Savings target on a wallet',
      goals_desc:'Link a goal to a wallet — progress is tracked from its current balance.',
      goals_add:'Add a goal', goals_add_confirm:'Add', goals_empty:'No goals yet', goals_target:'Target', goals_target_date:'Date (optional)',
      goals_reached:'Goal reached!', goals_added:'Goal added', goals_need_wallet:'Add a wallet first',
      goals_delete_confirm:'Delete this goal?', goals_delete_title:'Delete goal',
      cat_empty:'No categories', cat_subcat_short:'Subcat.', cat_this_month:'UAH this month', cat_drag_title:'Drag to reorder',
      cat_no_subcats:'No subcategories', cat_subcategory:'Subcategory',
      cat_add_subcat_prompt:'Subcategory name:', cat_add_subcat_title:'New subcategory', cat_subcat_exists:'That subcategory already exists',
      cat_delete_subcat_confirm:'Delete this subcategory? Old transactions will keep their name.', cat_delete_subcat_title:'Delete subcategory',
      cat_add_prompt:'Category name:', cat_add_title:'New category', cat_already_exists:'That category already exists',
      cat_delete_confirm:'Delete this category? Old transactions will keep their name.', cat_delete_title:'Delete category',
      cat_act_edit:'Edit', cat_act_show_tx:'Show transactions', cat_act_delete:'Delete category',
      cat_showing:'Showing:', cat_add:'Add category',
      budgets_empty:'No expense categories', budgets_limit_label:'Limit/month, UAH', budgets_over_by:'Over by',
      budgets_title:'Budgets by category', budgets_desc:'Monthly spending limit per category. Leave blank or 0 for no budget.',
      budgets_no_limit:'No limit',
      recurring_empty:'No recurring transactions', recurring_type:'Type', recurring_amount:'Amount',
      recurring_frequency:'Frequency', recurring_daily:'Daily', recurring_weekly:'Weekly', recurring_monthly:'Monthly',
      recurring_next_date:'Next time', recurring_active:'active', recurring_paused_label:'paused',
      recurring_added:'Template added — fill in the amount', recurring_delete_confirm:'Delete this recurring transaction? Already-added transactions stay.',
      recurring_delete_title:'Delete template', recurring_desc:'Automatically added when you open the app, once the date arrives.', recurring_add:'Add recurring transaction',
      tags_empty:'No tags', tags_default_name:'Tag', tags_add_prompt:'Tag name:', tags_add_title:'New tag',
      tags_delete_confirm:'Delete this tag? It will be removed from old transactions.', tags_delete_title:'Delete tag',
      tags_desc:'Freeform labels on transactions, separate from categories — a transaction can have several.', tags_add:'Add tag',
      rules_empty:'No rules', rules_keyword:'Word in comment', rules_keyword_placeholder:'e.g. Trader Joe\'s',
      rules_delete_confirm:'Delete this rule?', rules_delete_title:'Delete rule', rules_auto_applied:'Category auto-detected:',
      ai_category_suggested:'On-device AI suggested:',
      receipt_scan_btn:'Scan receipt', receipt_scan_processing:'Scanning receipt…',
      receipt_scan_done:'Receipt scanned', receipt_scan_not_found:'Could not detect an amount or date — check manually',
      receipt_scan_fail:'Could not scan the receipt',
      receipt_scan_timeout:'Scanning took too long — try a clearer, closer photo',
      rules_desc:'If the comment contains a keyword, the category fills in automatically while you enter the transaction.', rules_add:'Add rule',
      debt_stat_start:'Starting amount', debt_stat_balance:'Current balance', debt_stat_paid:'Paid', debt_stat_count:'Payments',
      debt_stat_due:'Due date', debt_due_date:'Date it needs to be paid off by',
      debt_due_in_days:'in {n} d.', debt_due_today:'today', debt_due_overdue_days:'overdue by {n} d.',
      debt_progress_label:'Paid off',
      debt_forecast_title:'Payoff forecast',
      debt_forecast_pace:'At this pace, about {n} payments to go.',
      debt_forecast_avg:'Average payment — {amt}.',
      debt_forecast_done:'This debt is fully paid off.',
      debt_forecast_no_pace:'Not enough data to estimate the payoff pace.',
      debt_info_title:'Settlement details', debt_name:'Name', debt_name_placeholder:'E.g. Loan, Installment plan…',
      debt_note:'Note', debt_note_placeholder:'Additional note...', debt_currency:'Currency', debt_delete:'Delete this settlement',
      debt_new_payment:'New payment', debt_amount:'Amount', debt_amount_placeholder:'e.g. 500 or ***68',
      debt_balance_after:'Balance after payment', debt_balance_auto:'(calculated automatically)', debt_auto_placeholder:'auto',
      debt_date_label:'Date / label', debt_date_placeholder:'e.g. 10.02.2026 or March', debt_add_payment:'Add payment', debt_add_calc:'Create settlement',
      debt_history_title:'Payment history',
      debt_add_prompt:'Settlement name:', debt_new_default:'New settlement', debt_added:'Settlement added',
      debt_need_one:'At least one settlement must remain', debt_delete_confirm:'Delete this settlement along with its entire payment history?', debt_delete_title:'Delete settlement',
      debt_default_name:'Settlement',
      debt_enter_amount:'Enter the payment amount', debt_enter_balance:'Enter the balance after payment', debt_payment_added:'Payment added',
      debt_delete_payment_confirm:'Delete this payment?', debt_delete_payment_title:'Delete payment',
      debt_no_payments:'No payments yet', debt_expected:'Expected', debt_discrepancy:'discrepancy',
      pin_locked_title:'Rytm locked', pin_code:'PIN code', pin_unlock:'Unlock', pin_forgot:'Forgot PIN?',
      onboard_skip:'Skip', onboard_next:'Next', onboard_start:'Get started',
      onboard_1_title:'Shift schedule', onboard_1_desc:'Keep a work calendar: shift types, colors, and automatic monthly salary calculation.',
      onboard_2_title:'Finances in one place', onboard_2_desc:'Multi-currency wallets, categories, budgets, and a full history of transactions.',
      onboard_3_title:'Rates and a converter', onboard_3_desc:'Live NBU or PrivatBank exchange rates plus a built-in converter, always at hand.',
      onboard_4_title:'Settlements', onboard_4_desc:'Track debts and installment plans with a full payment history for each one.',
      onboard_5_title:'Security and customization', onboard_5_desc:'PIN code, Face ID / Touch ID, and choose which widgets show up on the Finance tab.',
      pin_new:'New PIN (4–6 digits)', pin_repeat:'Repeat PIN', pin_set:'Set PIN', pin_lock_now:'Lock now',
      pin_bio_enable:'Enable Face ID / Touch ID', pin_bio_disable:'Disable Face ID / Touch ID', pin_remove:'Remove PIN',
      pin_disclaimer:'The PIN is stored only on this device and does not replace signing in to your account.',
      pin_enter:'Enter PIN', pin_wrong:'Wrong PIN',
      pin_forgot_confirm:'Reset the PIN on this device and sign out? You can set a new PIN after signing in again.',
      pin_forgot_title:'Forgot PIN?', pin_forgot_ok:'Reset and sign out',
      pin_status_set:'A PIN is already set on this device. Enter a new one to change it.', pin_status_unset:'No PIN set on this device yet.',
      pin_len_error:'PIN must be 4-6 digits', pin_mismatch:"PINs don't match", pin_set_success:'PIN set',
      pin_removed:'PIN disabled', pin_set_first:'Set a PIN first',
      pin_bio_on:'Face ID / Touch ID enabled', pin_bio_fail:'Failed to enable biometrics', pin_bio_off:'Face ID / Touch ID disabled',
      analytics_title:'Income & expense analytics', analytics_by_expense_category:'Expenses by category', analytics_by_income_category:'Income by category',
      analytics_period_month:'This month', analytics_period_prev:'Last month', analytics_period_3m:'3 months', analytics_period_all:'All time',
      analytics_income:'Income', analytics_expense:'Expense', analytics_net:'Net', analytics_savings_rate:'Savings rate',
      analytics_prefix:'You\'re spending', analytics_suffix_more:'more than the previous period', analytics_suffix_less:'less than the previous period',
      analytics_same:'Spending is level with the previous period', analytics_no_data:'No data yet',
      analytics_cat_insight_prefix:'Spending grew the most on', analytics_cat_insight_suffix:'this period',
      settings_wallets_sub:'Cards, cash, and other accounts', settings_rates_sub:'Automatic updates from NBU',
      settings_categories_sub:'Your own income and expense categories', settings_tags_sub:'Freeform labels for transactions, separate from categories',
      settings_budgets_sub:'Monthly spending limits per category', settings_recurring_sub:'Automatic payments on a schedule',
      settings_rules_sub:'Category by keyword in the comment', settings_export_sub:'Export all transactions to a file',
      settings_import_sub:'Load transactions from a file exported here',
      csv_import_empty:'This file has no transactions to import', csv_import_bad_header:'This isn’t a file exported from Rytm',
      csv_import_row_short:'Row skipped — not enough columns', csv_import_bad_type:'Unknown transaction type',
      csv_import_unknown_wallet:'Wallet not found', csv_import_confirm_title:'Import CSV',
      csv_import_confirm_prefix:'Transactions to add:', csv_import_skip_prefix:'Skipped due to errors:',
      csv_import_confirm_ok:'Import', csv_import_none_valid:'No rows could be imported. Errors:',
      csv_import_read_fail:'Could not read the file', csv_import_done:'Transactions imported:',
      settings_pin_sub:'Protect the app with a code and Face ID/Touch ID', settings_notif_title:'Tracking reminder',
      settings_signout_sub:'End the session on this device', settings_delete_account_sub:'Permanently delete the account and all data',
      settings_terms_sub:'Rules for using the app', settings_privacy_sub:'How we handle your data',
      a11y_confirm:'Confirm', a11y_remove:'Remove', a11y_close:'Close', a11y_more:'More actions', a11y_prev_month:'Previous month', a11y_next_month:'Next month',
    }
  };
  window.currentLang='uk';
  try{ var savedLang=localStorage.getItem('mxLang'); if(savedLang==='en'||savedLang==='uk') window.currentLang=savedLang; }catch(e){}
  window.tr=function(key){ return (I18N[window.currentLang]&&I18N[window.currentLang][key]) || I18N.uk[key] || key; };
  window.translateStaticDOM=function(){
    document.querySelectorAll('[data-i18n]').forEach(function(el){ el.textContent=window.tr(el.getAttribute('data-i18n')); });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(function(el){ el.setAttribute('placeholder', window.tr(el.getAttribute('data-i18n-placeholder'))); });
    document.querySelectorAll('.lang-opt').forEach(function(b){ b.classList.toggle('active', b.dataset.lang===window.currentLang); });
    document.documentElement.setAttribute('lang', window.currentLang);
  };
  window.setLang=function(lang){
    if(lang!=='en'&&lang!=='uk') return;
    window.currentLang=lang;
    try{localStorage.setItem('mxLang', lang);}catch(e){}
    window.translateStaticDOM();
    if(window.__applyLangDynamic) window.__applyLangDynamic(lang);
  };
  window.translateStaticDOM();

  // CSP hardening: these used to be inline onclick="" HTML attributes
  // (setTheme('dark')/setLang('uk')/etc., the profile-avatar-preview's
  // raw .click() forward, and every .modal-card/.dlg-card's
  // event.stopPropagation() backdrop guard) — a strict script-src can't
  // allow inline event handler attributes without 'unsafe-inline' (nonces
  // only cover <script> elements, not onclick="" attributes; 'unsafe-hashes'
  // covers attributes but isn't supported in all browsers this app targets,
  // notably older Safari/iOS TWA). Delegated here since .theme-opt/.lang-opt
  // already carry the data-theme/data-lang attribute the .active-toggle
  // logic above already reads, and every .modal-card/.dlg-card is static
  // markup already in the DOM by the time this classic script runs (it's
  // placed after all of it in index.html).
  document.addEventListener('click', function(e){
    var t=e.target.closest('.theme-opt'); if(t){ window.setTheme(t.dataset.theme); return; }
    var l=e.target.closest('.lang-opt'); if(l){ window.setLang(l.dataset.lang); return; }
  });
  document.querySelectorAll('.modal-card, .dlg-card').forEach(function(el){
    el.addEventListener('click', function(e){ e.stopPropagation(); });
  });
  var avatarPreview=$('profile-avatar-preview');
  if(avatarPreview) avatarPreview.addEventListener('click', function(){
    var inp=$('profile-avatar-input'); if(inp) inp.click();
  });
})();
