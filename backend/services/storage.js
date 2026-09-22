import fs from 'node:fs';
import path from 'node:path';
import { config } from '../lib/env.js';
import { badRequest } from '../lib/errors.js';

const ALLOWED = new Map([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
]);
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

function root() {
  return path.resolve(process.cwd(), config.storageDir);
}

/** Save an uploaded prescription image under STORAGE_DIR/prescriptions/<familyId>/<id>.<ext>. */
export async function savePrescriptionImage({ familyId, id, mimeType, buffer }) {
  const ext = ALLOWED.get(mimeType);
  if (!ext) throw badRequest('Only JPEG, PNG or WebP images are accepted');
  if (buffer.length === 0) throw badRequest('Empty file');
  if (buffer.length > MAX_IMAGE_BYTES) throw badRequest('Image larger than 10 MB');
  const rel = path.posix.join('prescriptions', familyId, `${id}.${ext}`);
  const abs = path.join(root(), rel);
  await fs.promises.mkdir(path.dirname(abs), { recursive: true });
  await fs.promises.writeFile(abs, buffer);
  return rel;
}

export async function readPrescriptionImage(rel) {
  const abs = path.join(root(), rel);
  if (!abs.startsWith(root())) throw badRequest('Invalid path');
  return fs.promises.readFile(abs);
}

export async function deletePrescriptionImage(rel) {
  const abs = path.join(root(), rel);
  if (!abs.startsWith(root())) return;
  await fs.promises.rm(abs, { force: true });
}
