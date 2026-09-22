import { handler, ok, readJson, requireFamilyAccess } from '../../../../../../lib/http.js';
import { inviteCreateSchema } from '../../../../../../services/validation.js';
import { createInvite } from '../../../../../../services/family.js';
import { FamilyInvite, clean } from '../../../../../../models/index.js';

export const GET = handler(async (request, { params }) => {
  await requireFamilyAccess(request, params.familyId, { owner: true });
  const invites = await FamilyInvite.find({ familyId: params.familyId, usedAt: null, expiresAt: { $gt: Date.now() } }).lean();
  return ok(invites.map(clean));
});

export const POST = handler(async (request, { params }) => {
  const { user } = await requireFamilyAccess(request, params.familyId, { owner: true });
  const body = inviteCreateSchema.parse(await readJson(request).catch(() => ({})));
  return ok(await createInvite(user, params.familyId, body), 201);
});
