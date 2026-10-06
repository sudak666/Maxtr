// @ts-check
// Shifts ↔ money, mirroring the Android app's EarningsForecast /
// FinanceViewModel.monthOutlook / formShiftCost: what a purchase costs in
// shifts of work, and what is still coming before the month ends.
// Pure functions over plain data — no AppState/DOM import.

/** Same cycles as js/calendar.js's SHIFT_PATTERN_CYCLES. @type {Record<string, [number, number]>} */
const CYCLES = { every: [1, 0], alt: [1, 1], '2_2': [2, 2], '3_3': [3, 3] };

/** @param {Date} d */
function key(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Pay of a typical shift: the autofill type when set, otherwise the most used paid type.
 * @param {Record<string, string[]>} shifts
 * @param {{id: string, amount: number, isOff?: boolean}[]} types
 * @param {{typeId?: string}} schedule
 * @returns {number|null}
 */
export function typicalShiftPay(shifts, types, schedule) {
  const paid = types.filter((t) => !t.isOff && Number(t.amount) > 0);
  const auto = paid.find((t) => t.id === schedule.typeId);
  if (auto) return Number(auto.amount);
  /** @type {Record<string, number>} */
  const counts = {};
  Object.values(shifts).forEach((ids) => ids.forEach((id) => { counts[id] = (counts[id] || 0) + 1; }));
  let best = null;
  for (const t of paid) if (!best || (counts[t.id] || 0) > (counts[best.id] || 0)) best = t;
  return best ? Number(best.amount) : null;
}

/**
 * Shift pay placed after today plus what autofill will place, to month end.
 * @param {Date} today
 * @param {Record<string, string[]>} shifts
 * @param {{id: string, amount: number, isOff?: boolean}[]} types
 * @param {{enabled?: boolean, typeId?: string, pattern?: string, anchorDate?: string}} schedule
 */
export function remainingShiftPay(today, shifts, types, schedule) {
  const byId = Object.fromEntries(types.map((t) => [t.id, t]));
  const autoType = schedule.enabled ? byId[schedule.typeId || ''] : undefined;
  const scheduleType = autoType && !autoType.isOff ? autoType : null;
  const [on, off] = CYCLES[schedule.pattern || 'every'] || CYCLES.every;
  const anchor = schedule.anchorDate ? new Date(schedule.anchorDate + 'T00:00:00') : null;
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);
  const month = today.getMonth();
  let sum = 0;
  while (d.getMonth() === month) {
    const ids = shifts[key(d)] || [];
    if (ids.length) {
      sum += ids.reduce((s, id) => s + (Number(byId[id]?.amount) || 0), 0);
    } else if (scheduleType && anchor && on + off > 0) {
      const diff = Math.round((d.getTime() - anchor.getTime()) / 86400000);
      if (diff >= 0 && diff % (on + off) < on) sum += Number(scheduleType.amount) || 0;
    }
    d.setDate(d.getDate() + 1);
  }
  return sum;
}

/**
 * Active recurring occurrences due from today to month end, split by direction (UAH).
 * @param {Date} today
 * @param {{type: string, amount: number, active?: boolean, nextDate: string, frequency?: string, wallet?: string}[]} recurring
 * @param {(amount: number, wallet: string|undefined) => number} toUah
 */
export function recurringUntilMonthEnd(today, recurring, toUah) {
  const todayKey = key(today);
  const endKey = key(new Date(today.getFullYear(), today.getMonth() + 1, 0));
  let out = 0, inc = 0;
  recurring.forEach((r) => {
    if (r.active === false || !r.nextDate || !(Number(r.amount) > 0)) return;
    const d = new Date(r.nextDate + 'T00:00:00');
    let guard = 0;
    while (key(d) <= endKey && guard < 62) {
      if (key(d) >= todayKey) {
        const v = toUah(Number(r.amount), r.wallet);
        if (r.type === 'expense') out += v; else inc += v;
      }
      if (r.frequency === 'daily') d.setDate(d.getDate() + 1);
      else if (r.frequency === 'weekly') d.setDate(d.getDate() + 7);
      else d.setMonth(d.getMonth() + 1);
      guard++;
    }
  });
  return { out, inc };
}
