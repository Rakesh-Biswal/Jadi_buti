import { handler, ok, requireFamilyAccess } from '../../../../../../lib/http.js';
import { listMemberships } from '../../../../../../services/family.js';

export const GET = handler(async (request, { params }) => {
  await requireFamilyAccess(request, params.familyId);
  return ok(await listMemberships(params.familyId));
});
