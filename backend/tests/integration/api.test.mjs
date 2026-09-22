/**
 * End-to-end API tests against a running server (BASE_URL, default http://localhost:3000).
 * Creates its own users/families with unique emails and removes them afterwards.
 * Run: npm run dev  (in another terminal)  then  npm run test:integration
 */
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import dns from 'node:dns';

const BASE = process.env.BASE_URL || 'http://localhost:3000';
const run = Date.now();
const created = { users: [], families: [] };

async function api(path, { method = 'GET', token, body, form } = {}) {
  const headers = {};
  if (token) headers.authorization = `Bearer ${token}`;
  if (body) headers['content-type'] = 'application/json';
  const res = await fetch(`${BASE}/api/v1${path}`, { method, headers, body: form ? form : body ? JSON.stringify(body) : undefined });
  const isJson = (res.headers.get('content-type') || '').includes('application/json');
  const json = isJson ? await res.json() : null;
  return { status: res.status, json, res };
}

async function register(tag) {
  const email = `it-${run}-${tag}@example.com`;
  const { status, json } = await api('/auth/register', { method: 'POST', body: { email, password: 'Password123', name: `IT ${tag}` } });
  assert.equal(status, 201, JSON.stringify(json));
  created.users.push(json.data.user.id);
  return { ...json.data, email };
}

const owner = await register('owner');
const caregiver = await register('caregiver');
const stranger = await register('stranger');
let familyId;
let memberId;
let medicineId;

test('owner creates a family and is OWNER', async () => {
  const { status, json } = await api('/families', { method: 'POST', token: owner.accessToken, body: { name: 'Patel Family' } });
  assert.equal(status, 201);
  familyId = json.data.family.id;
  created.families.push(familyId);
  assert.equal(json.data.membership.role, 'OWNER');
});

test('login + refresh + me work', async () => {
  const login = await api('/auth/login', { method: 'POST', body: { email: owner.email, password: 'Password123' } });
  assert.equal(login.status, 200);
  const refresh = await api('/auth/refresh', { method: 'POST', body: { refreshToken: login.json.data.refreshToken } });
  assert.equal(refresh.status, 200);
  const me = await api('/auth/me', { token: refresh.json.data.accessToken });
  assert.equal(me.status, 200);
  assert.equal(me.json.data.families.length, 1);
  const bad = await api('/auth/login', { method: 'POST', body: { email: owner.email, password: 'wrong-pass' } });
  assert.equal(bad.status, 401);
});

test('stranger cannot read the family; missing token is 401', async () => {
  assert.equal((await api(`/families/${familyId}/members`, { token: stranger.accessToken })).status, 403);
  assert.equal((await api(`/families/${familyId}/members`)).status, 401);
});

test('caregiver joins with invite code; used/expired codes are rejected', async () => {
  const inv = await api(`/families/${familyId}/invites`, { method: 'POST', token: owner.accessToken, body: { canEdit: true } });
  assert.equal(inv.status, 201);
  const code = inv.json.data.code;
  const joinAsCaregiverByCaregiver = await api('/families/join', { method: 'POST', token: caregiver.accessToken, body: { code } });
  assert.equal(joinAsCaregiverByCaregiver.status, 200);
  assert.equal(joinAsCaregiverByCaregiver.json.data.membership.role, 'CAREGIVER');
  const reuse = await api('/families/join', { method: 'POST', token: stranger.accessToken, body: { code } });
  assert.equal(reuse.status, 400);
  const nope = await api('/families/join', { method: 'POST', token: stranger.accessToken, body: { code: 'ZZZZZZZZ' } });
  assert.equal(nope.status, 404);
  const caregiverCannotInvite = await api(`/families/${familyId}/invites`, { method: 'POST', token: caregiver.accessToken, body: {} });
  assert.equal(caregiverCannotInvite.status, 403);
  const list = await api(`/families/${familyId}/memberships`, { token: caregiver.accessToken });
  assert.equal(list.json.data.length, 2);
});

test('members and medicines CRUD with validation', async () => {
  const m = await api(`/families/${familyId}/members`, { method: 'POST', token: owner.accessToken, body: { name: 'Father', notes: 'BP patient' } });
  assert.equal(m.status, 201);
  memberId = m.json.data.id;
  const bad = await api(`/families/${familyId}/medicines`, { method: 'POST', token: owner.accessToken, body: { memberId, name: 'X', startDate: '2026-09-21', schedule: { frequency: 'WEEKLY' }, doseTimes: [{ time: '08:00', amount: 1 }] } });
  assert.equal(bad.status, 400, 'weekly without weekdays rejected');
  const med = await api(`/families/${familyId}/medicines`, {
    method: 'POST',
    token: caregiver.accessToken,
    body: { memberId, name: 'Telvas 40', strength: '40 mg', startDate: '2026-09-21', foodInstruction: 'AFTER_FOOD', schedule: { frequency: 'ALTERNATE_DAYS', intervalDays: 2 }, doseTimes: [{ time: '08:00', amount: 1 }, { time: '20:00', amount: 2 }] },
  });
  assert.equal(med.status, 201, JSON.stringify(med.json));
  medicineId = med.json.data.id;
  const upd = await api(`/families/${familyId}/medicines/${medicineId}`, { method: 'PUT', token: owner.accessToken, body: { strength: '40 mg (updated)' } });
  assert.equal(upd.status, 200);
  assert.equal(upd.json.data.strength, '40 mg (updated)');
  assert.equal(upd.json.data.version, 2);
  const stale = await api(`/families/${familyId}/medicines/${medicineId}`, { method: 'PUT', token: owner.accessToken, body: { strength: 'stale', updatedAt: 1 } });
  assert.equal(stale.json.data.strength, '40 mg (updated)', 'stale write ignored');
});

test('sync push/pull: event conflict rules, duplicate deductions, tombstones', async () => {
  const eventId = `${medicineId}:2026-09-21:08:00`;
  const base = { id: eventId, familyId, memberId, medicineId, localDate: '2026-09-21', time: '08:00', zoneId: 'Asia/Kolkata', scheduledAt: 1790000000000, doseAmount: 1, doseUnit: 'tablet' };
  // Device A (owner) marks TAKEN; device B (caregiver) later pushes MISSED for the same event.
  const a = await api('/sync/push', { method: 'POST', token: owner.accessToken, body: { familyId, changes: [{ entity: 'event', payload: { ...base, status: 'TAKEN', actualAt: 1790000100000, updatedAt: 1790000100000, statusHistory: [{ status: 'TAKEN', at: 1790000100000 }] } }, { entity: 'transaction', payload: { id: `deduct:${eventId}`, familyId, medicineId, type: 'DEDUCT_TAKEN', quantityDelta: -1, eventId, createdAt: 1790000100000 } }] } });
  assert.equal(a.status, 200);
  assert.ok(a.json.data.results.every((r) => r.ok), JSON.stringify(a.json.data.results));
  const b = await api('/sync/push', { method: 'POST', token: caregiver.accessToken, body: { familyId, changes: [{ entity: 'event', payload: { ...base, status: 'MISSED', updatedAt: 1790000900000, statusHistory: [{ status: 'MISSED', at: 1790000900000 }] } }, { entity: 'transaction', payload: { id: `deduct:${eventId}`, familyId, medicineId, type: 'DEDUCT_TAKEN', quantityDelta: -1, eventId, createdAt: 1790000900000 } }] } });
  assert.equal(b.json.data.results[0].doc.status, 'TAKEN', 'TAKEN is never downgraded');
  assert.equal(b.json.data.results[0].doc.statusHistory.length, 2);
  assert.equal(b.json.data.results[1].applied, false, 'duplicate deduction rejected');
  const pull = await api(`/sync/pull?familyId=${familyId}&since=0`, { token: caregiver.accessToken });
  assert.equal(pull.status, 200);
  assert.equal(pull.json.data.events.length, 1);
  assert.equal(pull.json.data.transactions.length, 1);
  assert.ok(pull.json.data.cursor > 0);
  const inc = await api(`/sync/pull?familyId=${familyId}&since=${pull.json.data.cursor}`, { token: owner.accessToken });
  assert.equal(inc.json.data.events.length, 0, 'incremental pull is empty');
  const strangerPush = await api('/sync/push', { method: 'POST', token: stranger.accessToken, body: { familyId, changes: [] } });
  assert.equal(strangerPush.status, 403);
  const inv = await api(`/families/${familyId}/inventory`, { token: owner.accessToken });
  assert.equal(inv.json.data[0].stock, -1 + 0, 'ledger sums deductions (no initial stock yet)');
  await api(`/families/${familyId}/inventory/transactions`, { method: 'POST', token: owner.accessToken, body: { id: `initial:${medicineId}`, medicineId, type: 'INITIAL', quantityDelta: 30, createdAt: 1789999000000 } });
  const inv2 = await api(`/families/${familyId}/inventory`, { token: owner.accessToken });
  assert.equal(inv2.json.data[0].stock, 29);
  assert.equal(inv2.json.data[0].estimatedDaysLeft, 19, '3 per 2 days = 1.5/day -> 19 days');
  const hist = await api(`/families/${familyId}/history?memberId=${memberId}`, { token: caregiver.accessToken });
  assert.equal(hist.json.data.adherence.taken, 1);
});

test('prescription upload is protected; extraction reports not configured; reject works', async () => {
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
  const form = new FormData();
  form.append('image', new Blob([png], { type: 'image/png' }), 'rx.png');
  form.append('memberId', memberId);
  const up = await api(`/families/${familyId}/prescriptions`, { method: 'POST', token: owner.accessToken, form });
  assert.equal(up.status, 201, JSON.stringify(up.json));
  const id = up.json.data.id;
  const img = await api(`/families/${familyId}/prescriptions/${id}/image`, { token: caregiver.accessToken });
  assert.equal(img.status, 200);
  assert.equal(img.res.headers.get('content-type'), 'image/png');
  assert.equal((await api(`/families/${familyId}/prescriptions/${id}/image`, { token: stranger.accessToken })).status, 403);
  assert.equal((await api(`/families/${familyId}/prescriptions/${id}/image`)).status, 401);
  const ext = await api(`/families/${familyId}/prescriptions/${id}/extract`, { method: 'POST', token: owner.accessToken });
  assert.ok([200, 502, 503].includes(ext.status));
  if (ext.status === 503) assert.equal(ext.json.error.code, 'extraction_not_configured');
  const rej = await api(`/families/${familyId}/prescriptions/${id}/reject`, { method: 'POST', token: owner.accessToken });
  assert.equal(rej.json.data.status, 'REJECTED');
  const del = await api(`/families/${familyId}/prescriptions/${id}`, { method: 'DELETE', token: owner.accessToken });
  assert.equal(del.status, 200);
});

test('medicine photo scans: upload, protected image, extraction status, kind isolation, delete', async () => {
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
  const form = new FormData();
  form.append('image', new Blob([png], { type: 'image/png' }), 'strip.png');
  const up = await api(`/families/${familyId}/medicine-scans`, { method: 'POST', token: owner.accessToken, form });
  assert.equal(up.status, 201, JSON.stringify(up.json));
  assert.equal(up.json.data.kind, 'MEDICINE_PHOTO');
  const id = up.json.data.id;
  assert.equal((await api(`/families/${familyId}/medicine-scans/${id}/image`, { token: caregiver.accessToken })).status, 200);
  assert.equal((await api(`/families/${familyId}/medicine-scans/${id}/image`, { token: stranger.accessToken })).status, 403);
  // A scan is not visible through the prescription endpoints and vice versa.
  assert.equal((await api(`/families/${familyId}/prescriptions/${id}`, { token: owner.accessToken })).status, 404);
  const list = await api(`/families/${familyId}/prescriptions`, { token: owner.accessToken });
  assert.ok(list.json.data.every((p) => p.id !== id));
  const ext = await api(`/families/${familyId}/medicine-scans/${id}/extract`, { method: 'POST', token: owner.accessToken });
  assert.ok([200, 502, 503].includes(ext.status));
  if (ext.status === 503) assert.equal(ext.json.error.code, 'extraction_not_configured');
  assert.equal((await api(`/families/${familyId}/medicine-scans/${id}`, { method: 'DELETE', token: owner.accessToken })).status, 200);
});

test('dose times accept an optional meal slot and reject unknown ones', async () => {
  const body = { memberId, name: 'Meal test', startDate: '2026-09-22', schedule: { frequency: 'DAILY' }, doseTimes: [{ time: '08:00', amount: 1, meal: 'BREAKFAST' }, { time: '20:00', amount: 1 }] };
  const ok = await api(`/families/${familyId}/medicines`, { method: 'POST', token: owner.accessToken, body });
  assert.equal(ok.status, 201, JSON.stringify(ok.json));
  assert.equal(ok.json.data.doseTimes[0].meal, 'BREAKFAST');
  assert.equal(ok.json.data.doseTimes[1].meal ?? null, null);
  const bad = await api(`/families/${familyId}/medicines`, { method: 'POST', token: owner.accessToken, body: { ...body, doseTimes: [{ time: '08:00', amount: 1, meal: 'BRUNCH' }] } });
  assert.equal(bad.status, 400);
});

test('owner can revoke caregiver; caregiver then loses access', async () => {
  const rm = await api(`/families/${familyId}/memberships/${caregiver.user.id}`, { method: 'DELETE', token: owner.accessToken });
  assert.equal(rm.status, 200);
  assert.equal((await api(`/families/${familyId}/members`, { token: caregiver.accessToken })).status, 403);
  const rmOwner = await api(`/families/${familyId}/memberships/${owner.user.id}`, { method: 'DELETE', token: owner.accessToken });
  assert.equal(rmOwner.status, 400);
});

after(async () => {
  // Clean up only what this run created.
  if (!process.env.MONGODB_URI) return;
  try {
    dns.setServers(['8.8.8.8', '1.1.1.1']);
    await mongoose.connect(process.env.MONGODB_URI, { dbName: process.env.MONGODB_DB || 'JadiButi', serverSelectionTimeoutMS: 15000 });
    const db = mongoose.connection.db;
    await db.collection('users').deleteMany({ id: { $in: created.users } });
    await db.collection('sessions').deleteMany({ userId: { $in: created.users } });
    for (const col of ['families', 'familymemberships', 'familyinvites', 'familymembers', 'medicines', 'medicationevents', 'inventorytransactions', 'prescriptions']) {
      await db.collection(col).deleteMany({ $or: [{ familyId: { $in: created.families } }, { id: { $in: created.families } }] });
    }
  } finally {
    await mongoose.disconnect();
  }
});
