import { NextResponse } from 'next/server';
import { connectDb } from './db.js';
import { verifyAccessToken } from './auth.js';
import { User } from '../models/User.js';
import { FamilyMembership } from '../models/FamilyMembership.js';

export { ApiError, badRequest, unauthorized, forbidden, notFound, conflict } from './errors.js';
import { ApiError, badRequest, unauthorized, forbidden } from './errors.js';

export function ok(data, status = 200) {
  return NextResponse.json({ ok: true, data }, { status });
}

export function fail(error) {
  if (error instanceof ApiError) {
    return NextResponse.json(
      { ok: false, error: { code: error.code, message: error.message, details: error.details } },
      { status: error.status },
    );
  }
  if (error?.name === 'ZodError') {
    return NextResponse.json(
      { ok: false, error: { code: 'validation_error', message: 'Invalid request', details: error.issues } },
      { status: 400 },
    );
  }
  if (error?.name === 'JsonWebTokenError' || error?.name === 'TokenExpiredError') {
    return NextResponse.json({ ok: false, error: { code: 'unauthorized', message: 'Invalid or expired token' } }, { status: 401 });
  }
  console.error('[api] unhandled error', error);
  return NextResponse.json({ ok: false, error: { code: 'internal_error', message: 'Internal server error' } }, { status: 500 });
}

/** Wrap a route handler: connects DB, resolves params, catches errors, returns JSON. */
export function handler(fn) {
  return async (request, ctx) => {
    try {
      await connectDb();
      const params = ctx?.params ? await ctx.params : {};
      return await fn(request, { params });
    } catch (error) {
      return fail(error);
    }
  };
}

export async function requireUser(request) {
  const header = request.headers.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) throw unauthorized();
  let payload;
  try {
    payload = verifyAccessToken(token);
  } catch {
    throw unauthorized('Invalid or expired token');
  }
  const user = await User.findOne({ id: payload.sub }).lean();
  if (!user || user.disabled) throw unauthorized('Account not available');
  return user;
}

/** Require an active membership in the family. Returns { user, membership }. */
export async function requireFamilyAccess(request, familyId, { write = false, owner = false } = {}) {
  const user = await requireUser(request);
  if (!familyId) throw badRequest('familyId required');
  const membership = await FamilyMembership.findOne({ familyId, userId: user.id, status: 'ACTIVE' }).lean();
  if (!membership) throw forbidden();
  if (owner && membership.role !== 'OWNER') throw forbidden('Only the family owner can do this');
  if (write && membership.role !== 'OWNER' && !membership.permissions?.canEdit) {
    throw forbidden('You do not have edit permission in this family');
  }
  return { user, membership };
}

export async function readJson(request) {
  try {
    return await request.json();
  } catch {
    throw badRequest('Body must be valid JSON');
  }
}
