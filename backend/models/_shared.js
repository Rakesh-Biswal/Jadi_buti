import mongoose from 'mongoose';

/**
 * Fields shared by every family-scoped, syncable entity.
 *  - id: client-generated UUID (stable across devices)
 *  - version: incremented by the server on each accepted write
 *  - updatedAt: client-side wall-clock time of the change (epoch ms)
 *  - serverUpdatedAt: server time of the last accepted write; pull cursor
 *  - deleted: tombstone for sync
 */
export const syncFields = {
  id: { type: String, required: true, unique: true },
  familyId: { type: String, required: true, index: true },
  version: { type: Number, default: 1 },
  updatedAt: { type: Number, default: () => Date.now() },
  serverUpdatedAt: { type: Number, default: () => Date.now(), index: true },
  updatedByUserId: { type: String },
  deleted: { type: Boolean, default: false },
};

export function model(name, definition, options = {}) {
  const schema = new mongoose.Schema(definition, { versionKey: false, minimize: false, ...options });
  if (options.indexes) for (const idx of options.indexes) schema.index(...idx);
  // Reuse compiled model across Next.js hot reloads.
  return mongoose.models[name] || mongoose.model(name, schema);
}

/** Strip Mongo internals before returning documents to clients. */
export function clean(doc) {
  if (!doc) return doc;
  const obj = typeof doc.toObject === 'function' ? doc.toObject() : { ...doc };
  delete obj._id;
  delete obj.__v;
  return obj;
}
