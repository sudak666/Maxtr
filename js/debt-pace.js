// @ts-check
// Typical debt payment + cadence — same as Android's DebtPaceCalc. Pure, no
// DOM/AppState, so tests/debt-pace.mjs can import it in plain Node.

/** @param {number[]} v */
function median(v){
  const s=[...v].sort((a,b)=>a-b);
  return s.length%2 ? s[(s.length-1)/2] : (s[s.length/2-1]+s[s.length/2])/2;
}

/** @param {string} s @returns {number|null} epoch ms */
function parseDate(s){
  const t=String(s||'').trim();
  let m=t.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if(m) return Date.UTC(+m[3], +m[2]-1, +m[1]);
  m=t.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if(m) return Date.UTC(+m[1], +m[2]-1, +m[3]);
  return null;
}

/**
 * Median of the last 6 paydowns (payments that lowered the balance) — the
 * all-time mean let one early lump sum dominate (≈47 payments shown where the
 * real pace meant ≈549) — plus the median gap in days between them when the
 * dates parse.
 * @param {number} start
 * @param {{balance: number|string, date?: string}[]} entries
 * @returns {{typicalPayment: number, typicalGapDays: number|null}|null}
 */
export function debtPace(start, entries){
  let prev=start;
  /** @type {{d: number, date: string}[]} */
  const downs=[];
  entries.forEach(e=>{
    const bal=Number(e.balance)||0;
    const d=prev-bal;
    if(d>0) downs.push({d, date:e.date||''});
    prev=bal;
  });
  if(!downs.length) return null;
  const recent=downs.slice(-6);
  const dates=recent.map(x=>parseDate(x.date)).filter(/** @returns {x is number} */ x=>x!=null).sort((a,b)=>a-b);
  const gaps=[];
  for(let i=1;i<dates.length;i++){ const g=Math.round((dates[i]-dates[i-1])/86400000); if(g>0) gaps.push(g); }
  return { typicalPayment: median(recent.map(x=>x.d)), typicalGapDays: gaps.length>=2 ? Math.round(median(gaps)) : null };
}
