/**
 * Inventory math shared by the API. Pure functions, mirrored in the Android domain module.
 * Stock is the sum of the ledger; it never goes below zero for display purposes, but
 * the ledger itself remains the source of truth.
 */
export function currentStock(transactions) {
  return transactions.filter((t) => !t.deleted).reduce((sum, t) => sum + t.quantityDelta, 0);
}

export function initialStock(transactions) {
  // The "reference" quantity for percentage thresholds: the latest INITIAL/REFILL/CORRECTION baseline.
  const sorted = transactions.filter((t) => !t.deleted).sort((a, b) => a.createdAt - b.createdAt);
  let baseline = 0;
  let running = 0;
  for (const t of sorted) {
    running += t.quantityDelta;
    if (t.type === 'INITIAL' || t.type === 'REFILL' || t.type === 'CORRECTION') baseline = Math.max(running, 0);
  }
  return baseline;
}

/** Average dose per day implied by the schedule (used for estimated days of supply). */
export function dailyConsumption(medicine) {
  const perDoseDay = (medicine.doseTimes || []).reduce((s, d) => s + d.amount, 0);
  const s = medicine.schedule || {};
  switch (s.frequency) {
    case 'DAILY':
      return perDoseDay;
    case 'ALTERNATE_DAYS':
      return perDoseDay / 2;
    case 'CUSTOM_INTERVAL':
      return perDoseDay / Math.max(1, s.intervalDays || 1);
    case 'SPECIFIC_WEEKDAYS':
      return (perDoseDay * (s.weekdays?.length || 0)) / 7;
    case 'WEEKLY':
      return perDoseDay / 7;
    default:
      return perDoseDay;
  }
}

export function isLowStock(medicine, stock, baseline) {
  const inv = medicine.inventory || {};
  if (inv.track === false) return false;
  if (inv.lowStockType === 'QUANTITY') return stock <= (inv.lowStockValue ?? 0);
  if (baseline <= 0) return stock <= 0;
  return (stock / baseline) * 100 <= (inv.lowStockValue ?? 5);
}

export function stockSummary(medicine, transactions) {
  const stock = currentStock(transactions);
  const baseline = initialStock(transactions);
  const perDay = dailyConsumption(medicine);
  const estimatedDaysLeft = perDay > 0 ? Math.floor(Math.max(stock, 0) / perDay) : null;
  return { stock, baseline, dailyConsumption: perDay, estimatedDaysLeft, lowStock: isLowStock(medicine, stock, baseline), unit: medicine.doseUnit };
}
