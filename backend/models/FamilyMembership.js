import { model } from './_shared.js';

export const ROLES = ['OWNER', 'CAREGIVER'];

export const FamilyMembership = model(
  'FamilyMembership',
  {
    id: { type: String, required: true, unique: true },
    familyId: { type: String, required: true, index: true },
    userId: { type: String, required: true, index: true },
    role: { type: String, enum: ROLES, required: true },
    status: { type: String, enum: ['ACTIVE', 'REMOVED'], default: 'ACTIVE' },
    permissions: {
      canEdit: { type: Boolean, default: true },
      // Receive a local alert on this caregiver's devices when a dose stays unacknowledged past the escalation window.
      receiveMissedDoseAlerts: { type: Boolean, default: false },
    },
    createdAt: { type: Number, default: () => Date.now() },
    updatedAt: { type: Number, default: () => Date.now() },
  },
  { indexes: [[{ familyId: 1, userId: 1 }, { unique: true }]] },
);
