import { model } from './_shared.js';

export const FamilyInvite = model('FamilyInvite', {
  code: { type: String, required: true, unique: true },
  familyId: { type: String, required: true, index: true },
  role: { type: String, enum: ['CAREGIVER'], default: 'CAREGIVER' },
  canEdit: { type: Boolean, default: true },
  createdByUserId: { type: String, required: true },
  createdAt: { type: Number, default: () => Date.now() },
  expiresAt: { type: Number, required: true },
  usedByUserId: { type: String, default: null },
  usedAt: { type: Number, default: null },
});
