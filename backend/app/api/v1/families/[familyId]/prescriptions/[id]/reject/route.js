import { handler, ok, requireFamilyAccess, notFound } from '../../../../../../../../lib/http.js';
import { Prescription, clean } from '../../../../../../../../models/index.js';

/** User rejected the draft: nothing is created; the draft is kept for audit but marked rejected. */
export const POST = handler(async (request, { params }) => {
  const { membership } = await requireFamilyAccess(request, params.familyId, { write: true });
  const doc = await Prescription.findOne({ id: params.id, familyId: membership.familyId });
  if (!doc) throw notFound();
  doc.status = 'REJECTED';
  doc.updatedAt = Date.now();
  await doc.save();
  return ok(clean(doc));
});
