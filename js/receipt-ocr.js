// ── RECEIPT OCR ──────────────────────────────────────────────────
// Scans a photographed/picked receipt image and extracts a likely amount +
// date to prefill the new-transaction form. Runs entirely on-device via
// Tesseract.js (WASM), vendored under js/vendor/tesseract/ rather than
// loaded from a CDN — this app's CSP has no jsdelivr/unpkg allowance
// (script-src is a tight 'self' + a few Google origins only), and
// self-hosting means the feature keeps working offline once the browser's
// cache/SW have it, matching how every Firebase SDK asset is already
// vendored/precached rather than pulled from gstatic at parse time. The
// library + wasm core + two language packs are a combined ~8MB, so this
// whole module is only ever touched via a dynamic import() when a user
// actually opens the scan flow — never part of the app's base page weight.

// Two different base paths for the same directory, deliberately: a dynamic
// import() specifier resolves relative to *this module's own URL*
// (js/receipt-ocr.js), while the plain path strings tesseract.js's
// createWorker() takes (workerPath/corePath/langPath) are resolved by the
// library itself relative to window.location.href (index.html, always at
// the app's root whichever host it's served from — Firebase Hosting at
// "/", GitHub Pages under "/Maxtr/"). Mixing these up silently 404s.
const TESS_IMPORT_BASE = './vendor/tesseract';
const TESS_PAGE_BASE = './js/vendor/tesseract';

let tesseractLibPromise = null;
function loadTesseractLib(){
  if(!tesseractLibPromise) tesseractLibPromise = import(`${TESS_IMPORT_BASE}/tesseract.esm.min.js`).then(m => m.default || m);
  return tesseractLibPromise;
}

// Amount extraction: prefer a line carrying a Ukrainian/English "total"
// keyword (real receipts print several numbers — line items, subtotal,
// change due — the grand total is the one worth trusting). Falls back to
// the largest *decimal-formatted* number anywhere in the text (a real
// receipt prints money as X.XX/X,XX; a bare whole-number fallback is only
// used as a last resort, and only after date digits have been masked out —
// see parseReceiptText, where a plain "2026" from an unmatched date line
// was originally winning "largest number" over the real 97.50 total).
// Handles both comma and dot as the decimal separator and a space as the
// thousands separator ("1 250,00" is a real receipt format here).
const AMOUNT_KEYWORD_RE = /(сума|разом|усього|всього|іт[оo]г[оo]|до\s*сплати|к\s*сплате|итого|total|amount\s*due|grand\s*total)/i;
const DECIMAL_RE = /\d[\d ]*[.,]\d{2}\b/g;
const INTEGER_RE = /\b\d+\b/g;

function extractAmounts(text, re){
  return (text.match(re) || [])
    .map(s => Number(s.replace(/\s/g, '').replace(',', '.')))
    .filter(n => Number.isFinite(n) && n > 0);
}

// dd.mm.yyyy / dd/mm/yy / dd-mm-yyyy-shaped, the dominant receipt date
// format here. Exposed as a matcher (not just a parser) because
// parseReceiptText also uses the matched span to mask date digits out of
// the text before amount extraction runs.
const DATE_RE = /\b(\d{1,2})[.\/-](\d{1,2})[.\/-](\d{2,4})\b/;

// Normalizes a date regex match to the yyyy-mm-dd <input type=date> expects.
// Returns null (rather than a best guess) if month/day are out of range,
// since OCR misreads are common and a garbage date is worse than no date —
// the user still has to glance at the form either way.
function normalizeDate(m){
  let [, d, mo, y] = m;
  if(y.length === 2) y = '20' + y;
  const dn = Number(d), mn = Number(mo), yn = Number(y);
  if(mn < 1 || mn > 12 || dn < 1 || dn > 31 || yn < 2000 || yn > 2100) return null;
  return `${y}-${String(mn).padStart(2, '0')}-${String(dn).padStart(2, '0')}`;
}

// Pure, zero-import text→{amount,date} parsing, kept separate from the
// OCR call itself so it's unit-testable in plain Node the same way
// js/tx-validation.js and functions/lib/pure.js are — no need to spin up
// Tesseract/a browser just to test the extraction regexes.
export function parseReceiptText(text){
  const raw = String(text || '');
  const dateMatch = raw.match(DATE_RE);
  const date = dateMatch ? normalizeDate(dateMatch) : null;
  // Mask out the matched date substring (whether or not it normalized to a
  // valid date) before amount extraction — otherwise a bare year or a
  // "16.07" date fragment reads exactly like a plausible money amount to
  // the numeric fallback below.
  const textForAmount = dateMatch ? raw.replace(dateMatch[0], ' ') : raw;
  const lines = textForAmount.split(/\r?\n/).map(l => l.trim()).filter(Boolean);

  let amount = null;
  for(const line of lines){
    if(AMOUNT_KEYWORD_RE.test(line)){
      const nums = extractAmounts(line, DECIMAL_RE);
      const found = nums.length ? nums : extractAmounts(line, INTEGER_RE);
      if(found.length){ amount = found[found.length - 1]; break; }
    }
  }
  if(amount === null){
    const decimals = extractAmounts(textForAmount, DECIMAL_RE);
    if(decimals.length) amount = Math.max(...decimals);
    else{
      const integers = extractAmounts(textForAmount, INTEGER_RE);
      if(integers.length) amount = Math.max(...integers);
    }
  }
  return { amount, date, rawText: raw };
}

// Runs OCR on a File/Blob (typically from <input type=file accept=image/*
// capture=environment>) and returns the parsed {amount, date, rawText}.
// workerBlobURL:false forces the library to spawn the worker via a direct
// same-origin `new Worker(workerPath)` rather than wrapping it in a blob:
// URL (its browser default) — this app's CSP worker-src is 'self' only,
// with no blob: allowance, and a same-origin worker script URL needs none.
export async function scanReceiptImage(file){
  const { createWorker, OEM } = await loadTesseractLib();
  const worker = await createWorker('ukr+eng', OEM.LSTM_ONLY, {
    workerPath: `${TESS_PAGE_BASE}/worker.min.js`,
    corePath: `${TESS_PAGE_BASE}/tesseract-core-lstm.wasm.js`,
    langPath: `${TESS_PAGE_BASE}/lang-data`,
    workerBlobURL: false,
    gzip: true,
  });
  try{
    const { data } = await worker.recognize(file);
    return parseReceiptText(data.text);
  } finally {
    await worker.terminate();
  }
}
