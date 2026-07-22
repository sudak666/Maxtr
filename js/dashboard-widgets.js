// @ts-check
// ── DASHBOARD WIDGETS (Порада дня + Топ криптовалюти) ──────────────────
// New Finance-tab widgets, added alongside the existing "goals" widget in
// WIDGET_ORDER_DEFAULT/WIDGET_SECTION_IDS/WIDGET_DEFS (see js/state.js,
// js/core.js, js/settings-managers.js) — the account owner asked for a
// couple of small additions specifically because the Widgets manager had
// exactly one toggleable item (goals) and read oddly as a settings screen.
// Both widgets follow the same self-hiding convention renderGoals()
// already established: hidden via the AppState.widgets on/off toggle, and
// also hidden whenever there's genuinely nothing to show yet (no stale/
// empty markup left on screen — see CLAUDE.md's Multiple-profiles section
// note on this exact pattern).
import { AppState } from './state.js';
// Vendored (not an npm import), same reasoning as js/debt.js's and
// js/analytics-csv.js's own Preact imports — see CLAUDE.md's "Preact
// adoption" note. Used here for the crypto sparkline chart, reusing the
// exact same small-SVG-via-h()/render() pattern as
// js/debt.js's DebtBurndownChart.
import { h as _h, render, Fragment } from './vendor/preact/preact.module.js';
// See js/analytics-csv.js's identical comment for why this re-declaration
// is needed (TypeScript infers h()'s signature from the vendored file's
// own minified implementation rather than the real `preact` package's
// richer .d.ts, installed as a type-only devDependency - see
// types/preact-vendor.d.ts). Zero runtime difference.
/** @type {typeof import('preact').h} */
const h = /** @type {any} */ (_h);

// ── ПОРАДА ДНЯ ───────────────────────────────────────────────────────
// Deliberately zero external dependency: a plain local array, rotated by
// calendar day. No network call, no API key, no CORS/CSP concern at all —
// the cheapest and safest of the two new widgets, picked first for exactly
// that reason when the account owner was asked which ideas to build.
const FINANCIAL_TIPS_UK = [
  'Відклади хоча б 10% доходу одразу після отримання, а не "що залишиться" в кінці місяця.',
  'Веди облік витрат хоча б один місяць — це найшвидший спосіб побачити, куди насправді йдуть гроші.',
  'Перед великою покупкою почекай 24 години — імпульсивні витрати найчастіше не витримують цієї паузи.',
  'Створи окремий гаманець "подушка безпеки" на 3-6 місяців витрат — і не чіпай його без крайньої потреби.',
  'Автоматизуй заощадження: перекидай суму на окремий гаманець одразу в день зарплати, а не в кінці місяця.',
  'Раз на місяць переглядай підписки — сервіси, якими ти не користуєшся, тихо з\'їдають бюджет.',
  'Плануй великі витрати заздалегідь (одяг на сезон, подарунки) — так вони не стають несподіваним ударом по бюджету.',
  'Порівнюй ціну не в абсолютних сумах, а в годинах роботи, які на неї потрібні — це змінює сприйняття покупки.',
  'Тримай готівку й картку в різних гаманцях додатку — так простіше побачити, скільки реально лишилось "на руках".',
  'Борги з високим відсотком (кредитки, розстрочки) варто закривати раніше за накопичення — переплата зазвичай більша за дохід від заощаджень.',
  'Заведи окрему категорію "дрібниці" — кава, снеки, таксі — часто саме вона виявляється найбільшою статтею витрат.',
  'Раз на квартал звіряй фактичні витрати з планом — бюджет, який ніхто не перевіряє, перестає бути орієнтиром.',
  'Не плати мінімум по кредитці, якщо можеш більше — відсотки нараховуються на залишок щодня.',
  'Заощадь на страхуванні (авто, здоров\'я) — правильна страховка іноді дешевша за один серйозний випадок без неї.',
  'Записуй операції одразу, а не "потім по пам\'яті" — за тиждень половина дрібних витрат просто забувається.',
  'Плати собі першому: спочатку заощадження, потім рахунки, потім усе інше — а не навпаки.',
  'Великі покупки в кредит рахуй у повній вартості з відсотками, а не в розмірі щомісячного платежу.',
  'Тримай ціль накопичення конкретною і видимою (сума + дата) — розмите "відкладати щось" рідко доводиться до кінця.',
  'Раз на рік переглядай тарифи на мобільний/інтернет — постійні клієнти рідко отримують найкращу ціну автоматично.',
  'Порівнюй курс обміну валют у кількох місцях — різниця в 1-2% на великій сумі помітна.',
];

/** @returns {void} */
export function renderDailyTip(){
  const section=document.getElementById('daily-tip-section');
  const box=document.getElementById('daily-tip-text');
  if(!section||!box) return;
  if(!AppState.widgets.dailyTip){ section.style.display='none'; return; }
  section.style.display='block';
  // A plain epoch-day index — this is a cosmetic once-a-day rotation, not
  // something that needs local-timezone-exact "today" math (unlike the
  // daily push-reminder dedup logic in functions/lib/sweep.js, where the
  // exact calendar day genuinely matters).
  const dayIndex=Math.floor(Date.now()/86400000);
  box.textContent=FINANCIAL_TIPS_UK[dayIndex % FINANCIAL_TIPS_UK.length];
}

// ── ТОП КРИПТОВАЛЮТИ ─────────────────────────────────────────────────
// CoinGecko's public /coins/markets endpoint — no API key needed, and
// (unlike api.privatbank.ua/bank.gov.ua, see js/settings-managers.js's own
// comments on those) it's designed to be called directly from a browser
// and sends CORS headers for this use case, so no same-origin Cloud
// Function proxy is needed here. firebase.json's CSP connect-src has
// https://api.coingecko.com added to allow this fetch under the deployed
// CSP. `sparkline=true` returns each coin's last-7-days hourly price
// series, which is what feeds the mini chart below.
const CRYPTO_COINGECKO_URL='https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=bitcoin,ethereum&sparkline=true&price_change_percentage=24h';
const CRYPTO_DEFS=[
  {id:'bitcoin', symbol:'BTC', color:'#f7931a'},
  {id:'ethereum', symbol:'ETH', color:'#627eea'},
];
// Device-global, not per-user/per-profile — market prices are the same for
// everyone, so this doesn't belong behind lsKey()'s per-uid keying (same
// reasoning as mxRatesUpdatedAt/mxTheme, see CLAUDE.md's Firebase data
// model section on device-global preference keys).
const CRYPTO_CACHE_KEY='mxCryptoTopCache';
// 30 minutes: frequent enough to feel "live" for a glance-at-it widget,
// comfortably inside CoinGecko's free-tier rate limit for a single device
// polling on its own schedule (this isn't shared across users/devices).
const CRYPTO_REFRESH_MS=30*60*1000;

/** @returns {{list?: CryptoMarketEntry[], at?: number} | null} */
function loadCryptoCache(){
  try{
    const raw=localStorage.getItem(CRYPTO_CACHE_KEY);
    return raw?JSON.parse(raw):null;
  }catch(e){ return null; }
}
/** @typedef {{id: string, current_price?: number, price_change_percentage_24h?: number, sparkline_in_7d?: {price?: number[]}}} CryptoMarketEntry */

/** @param {CryptoMarketEntry[]} list */
function saveCryptoCache(list){
  try{ localStorage.setItem(CRYPTO_CACHE_KEY, JSON.stringify({list, at:Date.now()})); }catch(e){}
}

// Plain stateless components, same pattern as js/debt.js's
// DebtBurndownChart/js/analytics-csv.js's AnalyticsDonut/
// js/settings-managers.js's FxWidgetRow — no JSX/hooks/classes.
/** @param {{W: number, H: number, points: string, color: string}} props */
function CryptoSparkline({W, H, points, color}){
  if(!points) return null;
  return h('svg', {viewBox:`0 0 ${W} ${H}`, preserveAspectRatio:'none'},
    h('polyline', {points, fill:'none', stroke:color, 'stroke-width':'1.5', 'stroke-linejoin':'round', 'stroke-linecap':'round', 'vector-effect':'non-scaling-stroke'})
  );
}

/** @param {{symbol: string, color: string, priceStr: string, changeStr: string, positive: boolean, sparkPoints: string}} props */
function CryptoRow({symbol, color, priceStr, changeStr, positive, sparkPoints}){
  return h('div', {class:'crypto-top-row'},
    h('span', {class:'icon-badge icon-badge-symbol', style:{background:color}}, symbol[0]),
    h('div', {class:'settings-row-text'},
      h('div', {class:'settings-row-title'}, symbol),
      h('div', {class:'settings-row-sub', style:{color: positive?'var(--green2)':'var(--red2)'}}, changeStr)
    ),
    h('div', {class:'crypto-top-spark'}, h(CryptoSparkline, {W:60, H:24, points:sparkPoints, color: positive?'var(--green2)':'var(--red2)'})),
    h('div', {class:'crypto-top-price'}, priceStr)
  );
}

/**
 * @param {number[]} prices
 * @param {number} W
 * @param {number} H
 * @returns {string}
 */
function sparklinePoints(prices, W, H){
  if(!Array.isArray(prices) || prices.length<2) return '';
  const pad=1;
  const maxV=Math.max(...prices), minV=Math.min(...prices), span=(maxV-minV)||1;
  return prices.map((v,i)=>{
    const x=pad+(W-2*pad)*(i/(prices.length-1));
    const y=pad+(H-2*pad)*(1-(v-minV)/span);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
}

// Renders whatever's currently cached (may be stale if the last fetch
// failed — same "show the last known value rather than nothing" approach
// as the FX rates widget's own seed-rate fallback), or self-hides if
// there's no cached data at all yet (first-ever load, before the first
// fetch has completed) — same convention renderGoals() uses for an empty
// goals list, rather than showing empty/broken rows.
/** @returns {void} */
export function renderCryptoTop(){
  const section=document.getElementById('crypto-top-section');
  const box=document.getElementById('crypto-top-list');
  if(!section||!box) return;
  if(!AppState.widgets.cryptoTop){ section.style.display='none'; return; }
  const cache=loadCryptoCache();
  const list=cache && Array.isArray(cache.list) ? cache.list : null;
  if(!list || !list.length){ section.style.display='none'; return; }
  section.style.display='block';
  const rows=CRYPTO_DEFS.map(def=>{
    const c=list.find(x=>x.id===def.id);
    if(!c || typeof c.current_price!=='number') return null;
    const price=c.current_price;
    const change=c.price_change_percentage_24h||0;
    const positive=change>=0;
    const sparkPrices=(c.sparkline_in_7d && Array.isArray(c.sparkline_in_7d.price)) ? c.sparkline_in_7d.price : [];
    return h(CryptoRow, {
      key: def.id,
      symbol: def.symbol,
      color: def.color,
      priceStr: `$${price.toLocaleString('uk-UA', {maximumFractionDigits: price<10?4:0})}`,
      changeStr: `${positive?'+':''}${change.toFixed(1)}%`,
      positive,
      sparkPoints: sparklinePoints(sparkPrices, 60, 24),
    });
  }).filter(Boolean);
  if(!rows.length){ section.style.display='none'; return; }
  render(h(Fragment, null, rows), box);
}

async function fetchCryptoTop(){
  const res=await fetch(CRYPTO_COINGECKO_URL);
  if(!res.ok) throw new Error('CoinGecko HTTP '+res.status);
  const list=await res.json();
  if(!Array.isArray(list)) throw new Error('unexpected CoinGecko response shape');
  return list;
}

// Called once from js/app-init.js's init(), same call site as
// maybeAutoUpdateRates() — fetches at most once per CRYPTO_REFRESH_MS
// regardless of how often the Finance tab is revisited, never blocks
// rendering (renderCryptoTop() above always renders from whatever's
// already cached first). A failed fetch (network blocked, CORS changed,
// rate-limited) is swallowed the same way updateRatesOnline()'s live-rates
// fetch already is elsewhere in this app — the widget just keeps showing
// its last-known values, or stays hidden if it never had any.
/** @returns {Promise<void>} */
export async function maybeRefreshCryptoTop(){
  if(!AppState.widgets.cryptoTop) return;
  const cache=loadCryptoCache();
  if(cache && Date.now()-(cache.at ?? 0) < CRYPTO_REFRESH_MS) return;
  try{
    const list=await fetchCryptoTop();
    saveCryptoCache(list);
    renderCryptoTop();
  }catch(e){
    console.warn('crypto top rates fetch failed', e);
  }
}
