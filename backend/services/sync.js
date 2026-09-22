import { FamilyMember, Medicine, MedicationEvent, InventoryTransaction, clean } from '../models/index.js';
import { TERMINAL_STATUSES } from '../models/MedicationEvent.js';
import { memberSchema, medicineSchema, eventSchema, transactionSchema } from './validation.js';
import { forbidden } from '../lib/errors.js';

const ENTITIES = {
  member: { Model: FamilyMember, schema: memberSchema },
  medicine: { Model: Medicine, schema: medicineSchema },
  event: { Model: MedicationEvent, schema: eventSchema },
  transaction: { Model: InventoryTransaction, schema: transactionSchema },
};

const STATUS_RANK = { UPCOMING: 0, DUE: 1, SNOOZED: 2, MISSED: 3, SKIPPED: 4, TAKEN: 5 };

/**
 * Merge an incoming medication event with the stored one.
 * Rules (safety first):
 *  - A terminal status (TAKEN/SKIPPED) is never downgraded by a non-terminal one.
 *  - Between two terminal statuses, the one with the later recorded time wins.
 *  - Between two non-terminal statuses, the higher rank wins, then latest updatedAt.
 *  - statusHistory is the union of both histories (append-only).
 */
export function mergeEvent(existing, incoming) {
  if (!existing) return incoming;
  const exTerminal = TERMINAL_STATUSES.includes(existing.status);
  const inTerminal = TERMINAL_STATUSES.includes(incoming.status);
  let winner;
  if (exTerminal && !inTerminal) winner = existing;
  else if (inTerminal && !exTerminal) winner = incoming;
  else if (exTerminal && inTerminal) {
    const exAt = existing.actualAt ?? existing.updatedAt ?? 0;
    const inAt = incoming.actualAt ?? incoming.updatedAt ?? 0;
    winner = inAt >= exAt ? incoming : existing;
  } else {
    const r = STATUS_RANK[incoming.status] - STATUS_RANK[existing.status];
    if (r > 0) winner = incoming;
    else if (r < 0) winner = existing;
    else winner = (incoming.updatedAt ?? 0) >= (existing.updatedAt ?? 0) ? incoming : existing;
  }
  const historyKey = (h) => `${h.status}:${h.at}`;
  const seen = new Set();
  const history = [...(existing.statusHistory || []), ...(incoming.statusHistory || [])]
    .filter((h) => {
      const k = historyKey(h);
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    })
    .sort((a, b) => a.at - b.at);
  return {
    ...existing,
    ...winner,
    statusHistory: history,
    // Never lose the schedule identity fields.
    id: existing.id,
    familyId: existing.familyId,
    scheduledAt: existing.scheduledAt,
    localDate: existing.localDate,
    time: existing.time,
  };
}

/**
 * Apply one change from a client. Returns the stored (authoritative) document.
 * - New docs are inserted.
 * - Existing docs: events use mergeEvent; transactions are immutable (first write wins);
 *   other entities use last-writer-wins by client updatedAt.
 */
export async function applyChange(entity, rawPayload, { user, membership }) {
  const def = ENTITIES[entity];
  if (!def) throw new Error(`Unknown entity ${entity}`);
  const payload = def.schema.parse(rawPayload);
  if (payload.familyId !== membership.familyId) throw forbidden('Change does not belong to this family');
  const now = Date.now();
  const existing = await def.Model.findOne({ id: payload.id }).lean();
  if (existing && existing.familyId !== membership.familyId) throw forbidden('Change does not belong to this family');

  let next;
  if (!existing) {
    next = { ...payload, version: 1, updatedAt: payload.updatedAt ?? now, serverUpdatedAt: now, updatedByUserId: user.id };
  } else if (entity === 'transaction') {
    // Ledger entries are immutable; only the tombstone can change (and only forward).
    if (payload.deleted && !existing.deleted) {
      next = { ...existing, deleted: true, version: existing.version + 1, updatedAt: payload.updatedAt ?? now, serverUpdatedAt: now, updatedByUserId: user.id };
    } else {
      return { doc: clean(existing), applied: false };
    }
  } else if (entity === 'event') {
    const merged = mergeEvent(existing, payload);
    const changed = merged.status !== existing.status || merged.actualAt !== existing.actualAt || merged.snoozedUntil !== existing.snoozedUntil || merged.note !== existing.note || (merged.statusHistory?.length ?? 0) !== (existing.statusHistory?.length ?? 0) || (payload.deleted ?? false) !== (existing.deleted ?? false);
    if (!changed) return { doc: clean(existing), applied: false };
    next = { ...merged, deleted: payload.deleted ?? existing.deleted ?? false, version: existing.version + 1, updatedAt: Math.max(existing.updatedAt ?? 0, payload.updatedAt ?? now), serverUpdatedAt: now, updatedByUserId: user.id };
  } else {
    // Last-writer-wins on client wall-clock; a stale write is ignored (server copy returned).
    if ((payload.updatedAt ?? now) < (existing.updatedAt ?? 0)) return { doc: clean(existing), applied: false };
    next = { ...existing, ...payload, version: existing.version + 1, updatedAt: payload.updatedAt ?? now, serverUpdatedAt: now, updatedByUserId: user.id };
  }
  delete next._id;
  const saved = await def.Model.findOneAndUpdate({ id: payload.id }, { $set: next }, { upsert: true, new: true, setDefaultsOnInsert: true }).lean();
  return { doc: clean(saved), applied: true };
}

/** All family documents changed since the cursor (server time). */
export async function pullChanges(familyId, since = 0, limit = 1000) {
  const q = { familyId, serverUpdatedAt: { $gt: since } };
  const [members, medicines, events, transactions] = await Promise.all([
    FamilyMember.find(q).sort({ serverUpdatedAt: 1 }).limit(limit).lean(),
    Medicine.find(q).sort({ serverUpdatedAt: 1 }).limit(limit).lean(),
    MedicationEvent.find(q).sort({ serverUpdatedAt: 1 }).limit(limit).lean(),
    InventoryTransaction.find(q).sort({ serverUpdatedAt: 1 }).limit(limit).lean(),
  ]);
  const all = [...members, ...medicines, ...events, ...transactions];
  const cursor = all.reduce((m, d) => Math.max(m, d.serverUpdatedAt ?? 0), since);
  const hasMore = [members, medicines, events, transactions].some((a) => a.length >= limit);
  return {
    cursor,
    hasMore,
    members: members.map(clean),
    medicines: medicines.map(clean),
    events: events.map(clean),
    transactions: transactions.map(clean),
  };
}

export const entityNames = Object.keys(ENTITIES);
