// @ts-check
// Pure helpers behind three competitor-inspired features, kept import-free so
// plain Node can test them (tests/smart-insights.mjs). Mirrors Android's
// FinanceInsights.safeToSpendPerDay, MerchantBrands and SmartSearch 1:1.

/**
 * "Safe to spend": (balance − recurring expenses still due this month) ÷ days
 * left, today included. Expected shift pay is left out on purpose — it often
 * lands next month. null when the bills already exceed the balance.
 * @param {number} balance @param {number} bills @param {Date} today
 * @returns {number|null}
 */
export function safeToSpendPerDay(balance, bills, today){
  const daysInMonth=new Date(today.getFullYear(), today.getMonth()+1, 0).getDate();
  const daysLeft=daysInMonth-today.getDate()+1;
  const free=balance-bills;
  return free<=0 ? null : free/daysLeft;
}

/** @typedef {{name:string, monogram:string, color:string, keywords:string[]}} Brand */

/**
 * Known Ukrainian merchants → a monogram disc in the brand color instead of the
 * generic category icon. Monograms on purpose, not the companies' logos: no
 * third-party artwork is bundled and it works offline.
 * @type {Brand[]}
 */
export const MERCHANT_BRANDS=[
  ['АТБ','АТБ','#E30613',['атб','atb']],['Сільпо','С','#FF6A13',['сільпо','silpo']],
  ['Novus','N','#00A651',['novus','новус']],['Фора','Ф','#E4002B',['фора','fora']],
  ['Varus','V','#009639',['varus','варус']],['Metro','M','#003D7C',['metro cash','метро кеш','metro ']],
  ['Auchan','A','#E2001A',['auchan','ашан']],['Коло','К','#2E7D32',['kolo ','коло ','kolo kiyev']],
  ['Велмарт','В','#0072BC',['velmart','велмарт']],['Таврія В','ТВ','#D71920',['tavria','таврія']],
  ['Епіцентр','Е','#F39200',['epicentr','епіцентр']],['Rozetka','R','#00A046',['rozetka','розетка']],
  ['Comfy','C','#E5007E',['comfy','комфі']],['Фокстрот','Ф','#FF5800',['foxtrot','фокстрот']],
  ['Glovo','G','#FFC244',['glovo','глово']],['Bolt','B','#34D186',['bolt']],
  ['Uklon','U','#2F54EB',['uklon','уклон']],['Uber','U','#8A8A8A',['uber']],
  ['Нова пошта','НП','#DA291C',['nova poshta','нова пошта','novaposhta','nova pay']],['Укрпошта','УП','#FFC72C',['ukrposhta','укрпошта']],
  ["McDonald's",'M','#FFBC0D',['mcdonald','макдональдс','макдональц']],['KFC','KFC','#E4002B',['kfc']],
  ['Пузата хата','ПХ','#8B5A2B',['puzata','пузата']],['Aroma Kava','AK','#6F4E37',['aroma kava','арома кава']],
  ['Львівські круасани','ЛК','#C8102E',['круасани','kruasany']],['WOG','W','#00843D',['wog']],
  ['OKKO','O','#E30613',['okko','окко']],['SOCAR','S','#0057B8',['socar']],['UPG','U','#FDB913',['upg']],
  ['Київстар','КС','#0090D7',['kyivstar','київстар']],['Vodafone','V','#E60000',['vodafone','водафон']],
  ['lifecell','L','#FFD100',['lifecell','лайфсел']],['Netflix','N','#E50914',['netflix']],
  ['Spotify','S','#1DB954',['spotify']],['YouTube','YT','#FF0000',['youtube']],['Google','G','#4285F4',['google']],
  ['Apple','A','#9AA0A6',['apple.com','itunes','apple ']],['Steam','S','#66C0F4',['steam']],
  ['Prom','P','#7B2CBF',['prom.ua','prom ']],['EVA','EVA','#E6007E',['eva ','єва']],
  ['Watsons','W','#00A39C',['watsons','ватсонс']],['Аптека АНЦ','АНЦ','#0072BC',['анц','anc ']],
  ['Аптека 9-1-1','911','#00A651',['9-1-1','аптека 911']],['Подорожник','П','#00A651',['подорожник','podorozhnyk']],
  ['Космо','К','#E6007E',['kosmo','космо']],['Multiplex','MX','#6A1B9A',['multiplex','мультиплекс']],
  ['Планета Кіно','ПК','#1E88E5',['planeta kino','планета кіно']],
].map(([name,monogram,color,keywords])=>({name:/** @type {string} */ (name), monogram:/** @type {string} */ (monogram), color:/** @type {string} */ (color), keywords:/** @type {string[]} */ (keywords)}));

/** @param {string|null|undefined} comment @returns {Brand|null} */
export function matchMerchant(comment){
  if(!comment || !String(comment).trim()) return null;
  const text=' '+String(comment).toLowerCase().replace(/[^\p{L}\p{Nd}.\-]+/gu,' ')+' ';
  return MERCHANT_BRANDS.find(b=>b.keywords.some(k=>text.includes(' '+k)))||null;
}

/** @type {Record<string, number>} */
const MONTH_WORDS={};
['січень січня січні january','лютий лютого лютому february','березень березня березні march',
 'квітень квітня квітні april','травень травня травні may','червень червня червні june',
 'липень липня липні july','серпень серпня серпні august','вересень вересня вересні september',
 'жовтень жовтня жовтні october','листопад листопада листопаді november','грудень грудня грудні december']
  .forEach((forms,i)=>forms.split(' ').forEach(w=>{ MONTH_WORDS[w]=i+1; }));

/** @typedef {{text:string, min:number|null, max:number|null, month:string|null, date:string|null}} SmartQuery */

/** @param {Date} d */
function iso(d){ return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }

/**
 * History search in plain words: "кафе жовтень", "більше 500", "від 100 до 300",
 * "вчора". Recognised pieces become filters; the rest is the usual text search.
 * Exact month word forms, not stems ("черв" would also catch "червоний").
 * @param {string} raw @param {Date} today @returns {SmartQuery}
 */
export function parseSmartSearch(raw, today){
  let s=' '+String(raw||'').trim().toLowerCase()+' ';
  /** @type {number|null} */ let min=null;
  /** @type {number|null} */ let max=null;
  const num='(\\d+(?:[.,]\\d+)?)';
  const n=(/** @type {string} */ x)=>parseFloat(x.replace(',','.'));
  const between=new RegExp(`(?:від|from)\\s*${num}\\s*(?:до|to|-)\\s*${num}`);
  const lo=new RegExp(`(?:більше|понад|більш ніж|від|over|more than|above|from|>\\s*=?)\\s*${num}`);
  const hi=new RegExp(`(?:менше|до|under|less than|below|<\\s*=?)\\s*${num}`);
  let m=s.match(between);
  if(m){ min=n(m[1]); max=n(m[2]); s=s.replace(m[0],' '); }
  if(min===null && (m=s.match(lo))){ min=n(m[1]); s=s.replace(m[0],' '); }
  if(max===null && (m=s.match(hi))){ max=n(m[1]); s=s.replace(m[0],' '); }
  /** @type {string|null} */ let date=null;
  if(/\s(сьогодні|today)\s/.test(s)){ date=iso(today); s=s.replace(/\s(сьогодні|today)\s/,' '); }
  else if(/\s(вчора|yesterday)\s/.test(s)){ const y=new Date(today); y.setDate(y.getDate()-1); date=iso(y); s=s.replace(/\s(вчора|yesterday)\s/,' '); }
  /** @type {string|null} */ let month=null;
  /** @type {string[]} */ const kept=[];
  for(const w of s.split(/\s+/).filter(Boolean)){
    const mo=MONTH_WORDS[w];
    if(month===null && mo){
      // "грудень" typed in October means last December.
      const year=mo>today.getMonth()+1 ? today.getFullYear()-1 : today.getFullYear();
      month=`${year}-${String(mo).padStart(2,'0')}`;
    }else if(!['у','в','за','in','for'].includes(w)){
      kept.push(w);
    }
  }
  return {text:kept.join(' '), min, max, month, date};
}

/**
 * @param {SmartQuery} q
 * @param {{amount?:number, date?:string}} t
 */
export function smartQueryMatches(q, t){
  const a=Number(t.amount)||0, d=t.date||'';
  return (q.min===null || a>=q.min) && (q.max===null || a<=q.max) && (q.month===null || d.startsWith(q.month)) && (q.date===null || d===q.date);
}
