import { handler, ok, requireFamilyAccess, badRequest } from '../../../../../lib/http.js';
import { pullChanges } from '../../../../../services/sync.js';

/** GET /sync/pull?familyId=&since=<serverUpdatedAt cursor> */
export const GET = handler(async (request) => {
  const sp = new URL(request.url).searchParams;
  const familyId = sp.get('familyId');
  if (!familyId) throw badRequest('familyId required');
  await requireFamilyAccess(request, familyId);
  const since = Number(sp.get('since') || 0);
  if (!Number.isFinite(since) || since < 0) throw badRequest('since must be a non-negative number');
  const limit = Math.min(Number(sp.get('limit') || 1000), 2000);
  const result = await pullChanges(familyId, since, limit);
  return ok({ ...result, serverTime: Date.now() });
});
