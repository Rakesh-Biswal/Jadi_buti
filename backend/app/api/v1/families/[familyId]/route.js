import { handler, ok, requireFamilyAccess, notFound, readJson } from '../../../../../lib/http.js';
import { Family, clean } from '../../../../../models/index.js';
import { familyCreateSchema } from '../../../../../services/validation.js';

export const GET = handler(async (request, { params }) => {
  const { membership } = await requireFamilyAccess(request, params.familyId);
  const family = await Family.findOne({ id: params.familyId }).lean();
  if (!family) throw notFound();
  return ok({ family: clean(family), membership });
});

export const PATCH = handler(async (request, { params }) => {
  await requireFamilyAccess(request, params.familyId, { owner: true });
  const body = familyCreateSchema.parse(await readJson(request));
  const family = await Family.findOneAndUpdate({ id: params.familyId }, { $set: { name: body.name, updatedAt: Date.now() } }, { new: true }).lean();
  if (!family) throw notFound();
  return ok(clean(family));
});
