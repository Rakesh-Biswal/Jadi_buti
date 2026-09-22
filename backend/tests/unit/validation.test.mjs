import { test } from 'node:test';
import assert from 'node:assert/strict';
import { medicineSchema, eventSchema } from '../../services/validation.js';

const valid = {
  id: 'med-0001',
  familyId: 'fam-0001',
  memberId: 'mem-0001',
  name: 'Telvas 40',
  strength: '40 mg',
  startDate: '2026-09-21',
  schedule: { frequency: 'DAILY' },
  doseTimes: [{ time: '08:00', amount: 1 }],
};

test('valid medicine parses with defaults', () => {
  const m = medicineSchema.parse(valid);
  assert.equal(m.type, 'TABLET');
  assert.equal(m.inventory.lowStockValue, 5);
});

test('rejects endDate before startDate', () => {
  assert.throws(() => medicineSchema.parse({ ...valid, endDate: '2026-01-01' }));
});

test('rejects weekday frequencies without weekdays', () => {
  assert.throws(() => medicineSchema.parse({ ...valid, schedule: { frequency: 'SPECIFIC_WEEKDAYS' } }));
  assert.ok(medicineSchema.parse({ ...valid, schedule: { frequency: 'SPECIFIC_WEEKDAYS', weekdays: [1, 4] } }));
});

test('rejects duplicate dose times and bad time format', () => {
  assert.throws(() => medicineSchema.parse({ ...valid, doseTimes: [{ time: '08:00', amount: 1 }, { time: '08:00', amount: 2 }] }));
  assert.throws(() => medicineSchema.parse({ ...valid, doseTimes: [{ time: '8am', amount: 1 }] }));
});

test('event requires known status', () => {
  const e = { id: 'med-0001:2026-09-21:08:00', familyId: 'fam-0001', memberId: 'mem-0001', medicineId: 'med-0001', localDate: '2026-09-21', time: '08:00', scheduledAt: 1, doseAmount: 1, status: 'DUE' };
  assert.ok(eventSchema.parse(e));
  assert.throws(() => eventSchema.parse({ ...e, status: 'LATE' }));
});
