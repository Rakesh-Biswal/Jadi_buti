import { handler, ok, readJson, requireFamilyAccess } from '../../../../../../../lib/http.js';
import { membershipUpdateSchema } from '../../../../../../../services/validation.js';
import { updateMembership, removeMembership } from '../../../../../../../services/family.js';

export const PATCH = handler(async (request, { params }) => {
  const { membership } = await requireFamilyAccess(request, params.familyId);
  const body = membershipUpdateSchema.parse(await readJson(request));
  return ok(await updateMembership(params.familyId, params.userId, body, membership));
});

export const DELETE = handler(async (request, { params }) => {
  const { membership } = await requireFamilyAccess(request, params.familyId);
  return ok(await removeMembership(params.familyId, params.userId, membership));
});
