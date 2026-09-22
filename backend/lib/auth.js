import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { config } from './env.js';

export async function hashPassword(plain) {
  return bcrypt.hash(plain, 10);
}

export async function verifyPassword(plain, hash) {
  return bcrypt.compare(plain, hash);
}

export function signAccessToken(user) {
  return jwt.sign({ sub: user.id, email: user.email, typ: 'access' }, config.accessSecret, { expiresIn: config.accessTtl });
}

export function signRefreshToken(user, sessionId) {
  return jwt.sign({ sub: user.id, sid: sessionId, typ: 'refresh' }, config.refreshSecret, { expiresIn: config.refreshTtl });
}

export function verifyAccessToken(token) {
  const payload = jwt.verify(token, config.accessSecret);
  if (payload.typ !== 'access') throw new Error('wrong token type');
  return payload;
}

export function verifyRefreshToken(token) {
  const payload = jwt.verify(token, config.refreshSecret);
  if (payload.typ !== 'refresh') throw new Error('wrong token type');
  return payload;
}
