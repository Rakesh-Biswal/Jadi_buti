import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mergeEvent } from '../../services/sync.js';

const base = { id: 'm1:2026-09-21:08:00', familyId: 'f', scheduledAt: 1000, localDate: '2026-09-21', time: '08:00', statusHistory: [] };

test('terminal TAKEN is never downgraded by DUE/MISSED', () => {
  const existing = { ...base, status: 'TAKEN', actualAt: 2000, updatedAt: 2000, statusHistory: [{ status: 'TAKEN', at: 2000 }] };
  const incoming = { ...base, status: 'MISSED', updatedAt: 9000, statusHistory: [{ status: 'MISSED', at: 9000 }] };
  const merged = mergeEvent(existing, incoming);
  assert.equal(merged.status, 'TAKEN');
  assert.equal(merged.actualAt, 2000);
  assert.equal(merged.statusHistory.length, 2, 'history is the union');
});

test('incoming TAKEN overrides stored MISSED', () => {
  const existing = { ...base, status: 'MISSED', updatedAt: 5000 };
  const incoming = { ...base, status: 'TAKEN', actualAt: 6000, updatedAt: 6000, recordedByUserId: 'u2' };
  const merged = mergeEvent(existing, incoming);
  assert.equal(merged.status, 'TAKEN');
  assert.equal(merged.recordedByUserId, 'u2');
});

test('two terminal statuses: later recorded time wins', () => {
  const existing = { ...base, status: 'SKIPPED', actualAt: 7000, updatedAt: 7000 };
  const incoming = { ...base, status: 'TAKEN', actualAt: 6500, updatedAt: 9000 };
  assert.equal(mergeEvent(existing, incoming).status, 'SKIPPED');
  const incoming2 = { ...base, status: 'TAKEN', actualAt: 7500, updatedAt: 7500 };
  assert.equal(mergeEvent(existing, incoming2).status, 'TAKEN');
});

test('non-terminal: higher rank wins (SNOOZED over DUE), ties by updatedAt', () => {
  const existing = { ...base, status: 'DUE', updatedAt: 100 };
  assert.equal(mergeEvent(existing, { ...base, status: 'SNOOZED', snoozedUntil: 500, updatedAt: 50 }).status, 'SNOOZED');
  assert.equal(mergeEvent({ ...base, status: 'SNOOZED', snoozedUntil: 1, updatedAt: 100 }, { ...base, status: 'SNOOZED', snoozedUntil: 2, updatedAt: 200 }).snoozedUntil, 2);
});

test('schedule identity fields are never changed by a merge', () => {
  const existing = { ...base, status: 'DUE', updatedAt: 1 };
  const merged = mergeEvent(existing, { ...base, scheduledAt: 999999, localDate: '2030-01-01', status: 'TAKEN', actualAt: 5, updatedAt: 5 });
  assert.equal(merged.scheduledAt, 1000);
  assert.equal(merged.localDate, '2026-09-21');
});

test('duplicate history entries are de-duplicated', () => {
  const h = [{ status: 'DUE', at: 10 }];
  const merged = mergeEvent({ ...base, status: 'DUE', statusHistory: h }, { ...base, status: 'DUE', statusHistory: h, updatedAt: 2 });
  assert.equal(merged.statusHistory.length, 1);
});
