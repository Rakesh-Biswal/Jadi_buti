import { model, syncFields } from './_shared.js';

export const TX_TYPES = ['INITIAL', 'DEDUCT_TAKEN', 'REVERSAL', 'REFILL', 'CORRECTION'];

/**
 * Append-only inventory ledger. Current stock = sum(quantityDelta).
 * Deductions for a medication event use id `deduct:${eventId}` so the same
 * dose can never be deducted twice, even across devices.
 */
export const InventoryTransaction = model(
  'InventoryTransaction',
  {
    ...syncFields,
    medicineId: { type: String, required: true, index: true },
    type: { type: String, enum: TX_TYPES, required: true },
    quantityDelta: { type: Number, required: true },
    eventId: { type: String, default: null },
    note: { type: String, default: '' },
    createdAt: { type: Number, default: () => Date.now() },
    byUserId: { type: String, default: null },
  },
  { indexes: [[{ familyId: 1, medicineId: 1, createdAt: 1 }]] },
);
