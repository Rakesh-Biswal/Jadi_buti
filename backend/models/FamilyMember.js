import { model, syncFields } from './_shared.js';

/** A person whose medicines are managed (father, mother, grandmother...). */
export const FamilyMember = model('FamilyMember', {
  ...syncFields,
  name: { type: String, required: true, trim: true },
  dateOfBirth: { type: String, default: null }, // ISO date YYYY-MM-DD, optional
  notes: { type: String, default: '' },
  photoPath: { type: String, default: null },
  active: { type: Boolean, default: true },
  createdAt: { type: Number, default: () => Date.now() },
});
