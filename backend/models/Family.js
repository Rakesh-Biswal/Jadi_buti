import { model } from './_shared.js';

export const Family = model('Family', {
  id: { type: String, required: true, unique: true },
  name: { type: String, required: true, trim: true },
  ownerUserId: { type: String, required: true, index: true },
  createdAt: { type: Number, default: () => Date.now() },
  updatedAt: { type: Number, default: () => Date.now() },
});
