import { test } from 'node:test';
import assert from 'node:assert/strict';
import { currentStock, initialStock, dailyConsumption, isLowStock, stockSummary } from '../../services/inventory.js';

const med = (over = {}) => ({
  doseUnit: 'tablet',
  doseTimes: [{ time: '08:00', amount: 1 }, { time: '20:00', amount: 2 }],
  schedule: { frequency: 'DAILY', weekdays: [], intervalDays: 1 },
  inventory: { track: true, lowStockType: 'PERCENT', lowStockValue: 5 },
  ...over,
});
const tx = (type, qty, at, extra = {}) => ({ type, quantityDelta: qty, createdAt: at, ...extra });

test('stock is the sum of the ledger; deleted entries ignored', () => {
  const t = [tx('INITIAL', 30, 1), tx('DEDUCT_TAKEN', -1, 2), tx('DEDUCT_TAKEN', -2, 3), tx('DEDUCT_TAKEN', -5, 4, { deleted: true })];
  assert.equal(currentStock(t), 27);
});

test('daily consumption by frequency', () => {
  assert.equal(dailyConsumption(med()), 3);
  assert.equal(dailyConsumption(med({ schedule: { frequency: 'ALTERNATE_DAYS' } })), 1.5);
  assert.equal(dailyConsumption(med({ schedule: { frequency: 'WEEKLY', weekdays: [1] } })), 3 / 7);
  assert.equal(dailyConsumption(med({ schedule: { frequency: 'SPECIFIC_WEEKDAYS', weekdays: [1, 3, 5] } })), (3 * 3) / 7);
  assert.equal(dailyConsumption(med({ schedule: { frequency: 'CUSTOM_INTERVAL', intervalDays: 3 } })), 1);
});

test('percent thresholds 5% and 3% against the baseline', () => {
  const t = [tx('INITIAL', 100, 1)];
  const at = (n) => [...t, tx('DEDUCT_TAKEN', -(100 - n), 2)];
  assert.equal(isLowStock(med(), currentStock(at(6)), initialStock(t)), false);
  assert.equal(isLowStock(med(), currentStock(at(5)), initialStock(t)), true);
  const m3 = med({ inventory: { track: true, lowStockType: 'PERCENT', lowStockValue: 3 } });
  assert.equal(isLowStock(m3, currentStock(at(4)), initialStock(t)), false);
  assert.equal(isLowStock(m3, currentStock(at(3)), initialStock(t)), true);
});

test('quantity threshold and custom values', () => {
  const m = med({ inventory: { track: true, lowStockType: 'QUANTITY', lowStockValue: 7 } });
  assert.equal(isLowStock(m, 8, 30), false);
  assert.equal(isLowStock(m, 7, 30), true);
});

test('refill resets the baseline used for percentage', () => {
  const t = [tx('INITIAL', 30, 1), tx('DEDUCT_TAKEN', -28, 2), tx('REFILL', 60, 3)];
  assert.equal(currentStock(t), 62);
  assert.equal(initialStock(t), 62);
});

test('summary estimates days of supply and never negative', () => {
  const s = stockSummary(med(), [tx('INITIAL', 30, 1), tx('DEDUCT_TAKEN', -3, 2)]);
  assert.equal(s.stock, 27);
  assert.equal(s.estimatedDaysLeft, 9);
  const s2 = stockSummary(med(), [tx('INITIAL', 1, 1), tx('DEDUCT_TAKEN', -2, 2)]);
  assert.equal(s2.estimatedDaysLeft, 0);
  assert.equal(s2.lowStock, true);
});

test('inventory tracking disabled never reports low stock', () => {
  assert.equal(isLowStock(med({ inventory: { track: false } }), 0, 100), false);
});
