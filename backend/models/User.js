import { model } from './_shared.js';

export const User = model('User', {
  id: { type: String, required: true, unique: true },
  email: { type: String, required: true, unique: true, lowercase: true, trim: true },
  name: { type: String, required: true, trim: true },
  passwordHash: { type: String, required: true },
  disabled: { type: Boolean, default: false },
  createdAt: { type: Number, default: () => Date.now() },
});

export function publicUser(u) {
  return { id: u.id, email: u.email, name: u.name, createdAt: u.createdAt };
}
