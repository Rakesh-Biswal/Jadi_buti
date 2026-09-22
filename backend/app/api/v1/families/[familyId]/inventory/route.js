import { handler, ok, requireFamilyAccess } from '../../../../../../lib/http.js';
import { Medicine, InventoryTransaction, clean } from '../../../../../../models/index.js';
import { stockSummary } from '../../../../../../services/inventory.js';

/** Current stock per medicine, computed from the transaction ledger. */
export const GET = handler(async (request, { params }) => {
  const { membership } = await requireFamilyAccess(request, params.familyId);
  const medicines = await Medicine.find({ familyId: membership.familyId, deleted: { $ne: true }, active: true }).lean();
  const txs = await InventoryTransaction.find({ familyId: membership.familyId, deleted: { $ne: true } }).lean();
  const byMedicine = new Map();
  for (const t of txs) {
    if (!byMedicine.has(t.medicineId)) byMedicine.set(t.medicineId, []);
    byMedicine.get(t.medicineId).push(t);
  }
  return ok(medicines.map((m) => ({ medicine: clean(m), ...stockSummary(m, byMedicine.get(m.id) || []) })));
});
