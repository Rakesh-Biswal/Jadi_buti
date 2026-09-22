import { handler, ok, notFound, readJson, requireFamilyAccess, badRequest } from './http.js';
import { applyChange } from '../services/sync.js';
import { FamilyMember, Medicine, MedicationEvent, InventoryTransaction, clean } from '../models/index.js';
import { newId } from './ids.js';

const MODELS = { member: FamilyMember, medicine: Medicine, event: MedicationEvent, transaction: InventoryTransaction };

/**
 * REST routes for a family-scoped syncable entity. All writes go through the same
 * applyChange() used by /sync/push so REST and sync never diverge.
 */
export function collectionRoutes(entity, { filter } = {}) {
  const Model = MODELS[entity];
  return {
    GET: handler(async (request, { params }) => {
      const { membership } = await requireFamilyAccess(request, params.familyId);
      const url = new URL(request.url);
      const q = { familyId: membership.familyId, deleted: { $ne: true } };
      if (url.searchParams.get('includeDeleted') === 'true') delete q.deleted;
      if (filter) Object.assign(q, filter(url.searchParams));
      const docs = await Model.find(q).sort({ updatedAt: -1 }).limit(2000).lean();
      return ok(docs.map(clean));
    }),
    POST: handler(async (request, { params }) => {
      const access = await requireFamilyAccess(request, params.familyId, { write: true });
      const body = await readJson(request);
      const payload = { ...body, id: body.id || newId(), familyId: access.membership.familyId };
      const { doc } = await applyChange(entity, payload, access);
      return ok(doc, 201);
    }),
  };
}

export function itemRoutes(entity) {
  const Model = MODELS[entity];
  return {
    GET: handler(async (request, { params }) => {
      const { membership } = await requireFamilyAccess(request, params.familyId);
      const doc = await Model.findOne({ id: params.id, familyId: membership.familyId }).lean();
      if (!doc) throw notFound();
      return ok(clean(doc));
    }),
    PUT: handler(async (request, { params }) => {
      const access = await requireFamilyAccess(request, params.familyId, { write: true });
      const body = await readJson(request);
      if (body.id && body.id !== params.id) throw badRequest('id mismatch');
      const existing = await Model.findOne({ id: params.id, familyId: access.membership.familyId }).lean();
      if (!existing) throw notFound();
      const payload = { ...clean(existing), ...body, id: params.id, familyId: access.membership.familyId, updatedAt: body.updatedAt ?? Date.now() };
      const { doc } = await applyChange(entity, payload, access);
      return ok(doc);
    }),
    DELETE: handler(async (request, { params }) => {
      const access = await requireFamilyAccess(request, params.familyId, { write: true });
      const existing = await Model.findOne({ id: params.id, familyId: access.membership.familyId }).lean();
      if (!existing) throw notFound();
      const { doc } = await applyChange(entity, { ...clean(existing), deleted: true, updatedAt: Date.now() }, access);
      return ok(doc);
    }),
  };
}
