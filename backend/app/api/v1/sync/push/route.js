import { handler, ok, readJson, requireFamilyAccess } from '../../../../../lib/http.js';
import { syncPushSchema } from '../../../../../services/validation.js';
import { applyChange } from '../../../../../services/sync.js';

/**
 * Apply a batch of offline changes. Each change is applied independently; a
 * failing change does not block the rest. Response echoes the authoritative doc
 * (or the error) per change so the client can reconcile its outbox.
 */
export const POST = handler(async (request) => {
  const body = syncPushSchema.parse(await readJson(request));
  const access = await requireFamilyAccess(request, body.familyId, { write: true });
  const results = [];
  for (const change of body.changes) {
    try {
      const { doc, applied } = await applyChange(change.entity, { ...change.payload, familyId: body.familyId }, access);
      results.push({ entity: change.entity, id: change.payload.id, ok: true, applied, doc });
    } catch (error) {
      const message = error?.name === 'ZodError' ? 'Invalid payload' : error?.message || 'Rejected';
      results.push({ entity: change.entity, id: change.payload.id, ok: false, error: message, details: error?.issues });
    }
  }
  return ok({ results, serverTime: Date.now() });
});
