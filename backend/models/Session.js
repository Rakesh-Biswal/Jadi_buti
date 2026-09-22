import { model } from './_shared.js';

/** Refresh-token sessions. A revoked session makes its refresh token unusable. */
export const Session = model('Session', {
  id: { type: String, required: true, unique: true },
  userId: { type: String, required: true, index: true },
  deviceName: { type: String },
  createdAt: { type: Number, default: () => Date.now() },
  lastUsedAt: { type: Number, default: () => Date.now() },
  revokedAt: { type: Number, default: null },
});
