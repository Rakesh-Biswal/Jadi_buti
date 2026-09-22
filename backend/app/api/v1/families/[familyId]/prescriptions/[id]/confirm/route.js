import { z } from 'zod';
import { handler, ok, requireFamilyAccess, notFound, readJson } from '../../../../../../../../lib/http.js';
import { Prescription, clean } from '../../../../../../../../models/index.js';
import { applyChange } from '../../../../../../../../services/sync.js';
import { newId } from '../../../../../../../../lib/ids.js';

const bodySchema = z.object({
  memberId: z.string().min(1),
  // Fully reviewed/edited medicine payloads from the client (same shape as POST /medicines).
  medicines: z.array(z.record(z.any())).min(1).max(20),
  initialStock: z.record(z.number().min(0)).optional().default({}), // medicineId -> quantity
});

/**
 * Explicit user confirmation step. Creates the reviewed medicines (and their
 * initial inventory) and links them to the prescription.
 */
export const POST = handler(async (request, { params }) => {
  const access = await requireFamilyAccess(request, params.familyId, { write: true });
  const doc = await Prescription.findOne({ id: params.id, familyId: access.membership.familyId });
  if (!doc) throw notFound();
  const body = bodySchema.parse(await readJson(request));
  const created = [];
  for (const raw of body.medicines) {
    const medId = raw.id || newId();
    const { doc: medicine } = await applyChange(
      'medicine',
      { ...raw, id: medId, familyId: access.membership.familyId, memberId: body.memberId, prescriptionId: doc.id, updatedAt: Date.now() },
      access,
    );
    created.push(medicine);
    const qty = body.initialStock[medId] ?? body.initialStock[raw.id];
    if (qty !== undefined) {
      await applyChange(
        'transaction',
        { id: `initial:${medId}`, familyId: access.membership.familyId, medicineId: medId, type: 'INITIAL', quantityDelta: qty, note: 'Initial stock', createdAt: Date.now(), byUserId: access.user.id },
        access,
      );
    }
  }
  doc.status = 'CONFIRMED';
  doc.memberId = body.memberId;
  doc.confirmedMedicineIds = created.map((m) => m.id);
  doc.updatedAt = Date.now();
  await doc.save();
  return ok({ prescription: clean(doc), medicines: created });
});
