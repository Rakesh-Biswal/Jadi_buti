import { randomUUID, randomBytes } from 'node:crypto';

export const newId = () => randomUUID();

// Alphabet without 0/O/1/I to avoid ambiguity when read aloud.
const INVITE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export function inviteCode(length = 8) {
  const bytes = randomBytes(length);
  let out = '';
  for (let i = 0; i < length; i++) out += INVITE_ALPHABET[bytes[i] % INVITE_ALPHABET.length];
  return out;
}
