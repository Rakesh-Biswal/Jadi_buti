import { Family, FamilyMembership, FamilyInvite, User, clean } from '../models/index.js';
import { newId, inviteCode } from '../lib/ids.js';
import { badRequest, notFound, conflict } from '../lib/errors.js';

export async function createFamily(user, name) {
  const now = Date.now();
  const family = await Family.create({ id: newId(), name, ownerUserId: user.id, createdAt: now, updatedAt: now });
  const membership = await FamilyMembership.create({
    id: newId(),
    familyId: family.id,
    userId: user.id,
    role: 'OWNER',
    permissions: { canEdit: true, receiveMissedDoseAlerts: true },
  });
  return { family: clean(family), membership: clean(membership) };
}

export async function listFamiliesForUser(user) {
  const memberships = await FamilyMembership.find({ userId: user.id, status: 'ACTIVE' }).lean();
  const families = await Family.find({ id: { $in: memberships.map((m) => m.familyId) } }).lean();
  const byId = new Map(families.map((f) => [f.id, clean(f)]));
  return memberships
    .filter((m) => byId.has(m.familyId))
    .map((m) => ({ family: byId.get(m.familyId), membership: clean(m) }));
}

export async function createInvite(user, familyId, { canEdit, expiresInHours }) {
  let code;
  for (let attempt = 0; attempt < 5; attempt++) {
    code = inviteCode(8);
    if (!(await FamilyInvite.exists({ code }))) break;
  }
  const invite = await FamilyInvite.create({
    code,
    familyId,
    role: 'CAREGIVER',
    canEdit,
    createdByUserId: user.id,
    expiresAt: Date.now() + expiresInHours * 3600 * 1000,
  });
  return clean(invite);
}

export async function joinFamily(user, code) {
  const invite = await FamilyInvite.findOne({ code: code.toUpperCase() });
  if (!invite) throw notFound('Invite code not found');
  if (invite.usedAt) throw badRequest('This invite code has already been used');
  if (invite.expiresAt < Date.now()) throw badRequest('This invite code has expired');
  const family = await Family.findOne({ id: invite.familyId }).lean();
  if (!family) throw notFound('Family no longer exists');
  const existing = await FamilyMembership.findOne({ familyId: invite.familyId, userId: user.id });
  let membership;
  if (existing && existing.status === 'ACTIVE') throw conflict('You are already a member of this family');
  if (existing) {
    existing.status = 'ACTIVE';
    existing.role = 'CAREGIVER';
    existing.permissions = { canEdit: invite.canEdit, receiveMissedDoseAlerts: false };
    existing.updatedAt = Date.now();
    membership = await existing.save();
  } else {
    membership = await FamilyMembership.create({
      id: newId(),
      familyId: invite.familyId,
      userId: user.id,
      role: 'CAREGIVER',
      permissions: { canEdit: invite.canEdit, receiveMissedDoseAlerts: false },
    });
  }
  invite.usedByUserId = user.id;
  invite.usedAt = Date.now();
  await invite.save();
  return { family: clean(family), membership: clean(membership) };
}

export async function listMemberships(familyId) {
  const memberships = await FamilyMembership.find({ familyId, status: 'ACTIVE' }).lean();
  const users = await User.find({ id: { $in: memberships.map((m) => m.userId) } }).lean();
  const byId = new Map(users.map((u) => [u.id, u]));
  return memberships.map((m) => ({
    ...clean(m),
    user: byId.has(m.userId) ? { id: m.userId, name: byId.get(m.userId).name, email: byId.get(m.userId).email } : null,
  }));
}

export async function updateMembership(familyId, targetUserId, patch, actingMembership) {
  const target = await FamilyMembership.findOne({ familyId, userId: targetUserId, status: 'ACTIVE' });
  if (!target) throw notFound('Membership not found');
  const isSelf = actingMembership.userId === targetUserId;
  const isOwner = actingMembership.role === 'OWNER';
  if (patch.canEdit !== undefined) {
    if (!isOwner) throw badRequest('Only the owner can change edit permission');
    if (target.role === 'OWNER') throw badRequest('Owner always has edit permission');
    target.permissions.canEdit = patch.canEdit;
  }
  if (patch.receiveMissedDoseAlerts !== undefined) {
    if (!isOwner && !isSelf) throw badRequest('You can only change your own alert preference');
    target.permissions.receiveMissedDoseAlerts = patch.receiveMissedDoseAlerts;
  }
  target.updatedAt = Date.now();
  await target.save();
  return clean(target);
}

export async function removeMembership(familyId, targetUserId, actingMembership) {
  const target = await FamilyMembership.findOne({ familyId, userId: targetUserId, status: 'ACTIVE' });
  if (!target) throw notFound('Membership not found');
  if (target.role === 'OWNER') throw badRequest('The family owner cannot be removed');
  const isSelf = actingMembership.userId === targetUserId;
  if (actingMembership.role !== 'OWNER' && !isSelf) throw badRequest('Only the owner can remove caregivers');
  target.status = 'REMOVED';
  target.updatedAt = Date.now();
  await target.save();
  return clean(target);
}
