import { Prescription, clean } from '../models/index.js';
import { savePrescriptionImage, readPrescriptionImage, deletePrescriptionImage } from '../services/storage.js';
import { extractPrescription, extractMedicinePhoto, isExtractionConfigured } from '../services/extraction.js';
import { handler, ok, requireFamilyAccess, badRequest, notFound, ApiError } from './http.js';
import { newId } from './ids.js';

/**
 * Route factories shared by prescription uploads and medicine-photo scans.
 * Both are stored in the `prescriptions` collection, distinguished by `kind`.
 */
/** Rows created before the kind field existed are prescriptions. */
const kindFilter = (kind) => (kind === 'PRESCRIPTION' ? { $in: ['PRESCRIPTION', null] } : kind);

export function uploadCollectionRoutes(kind) {
  return {
    GET: handler(async (request, { params }) => {
      const { membership } = await requireFamilyAccess(request, params.familyId);
      const sp = new URL(request.url).searchParams;
      const q = { familyId: membership.familyId, kind: kindFilter(kind) };
      if (sp.get('memberId')) q.memberId = sp.get('memberId');
      const docs = await Prescription.find(q).sort({ createdAt: -1 }).limit(200).lean();
      return ok(docs.map(clean));
    }),
    /** multipart/form-data: image=<file>, memberId=<optional> */
    POST: handler(async (request, { params }) => {
      const { user, membership } = await requireFamilyAccess(request, params.familyId, { write: true });
      let form;
      try {
        form = await request.formData();
      } catch {
        throw badRequest('Expected multipart/form-data with an "image" file');
      }
      const file = form.get('image');
      if (!file || typeof file === 'string') throw badRequest('image file is required');
      const memberId = form.get('memberId') || null;
      const id = newId();
      const buffer = Buffer.from(await file.arrayBuffer());
      const imagePath = await savePrescriptionImage({ familyId: membership.familyId, id, mimeType: file.type, buffer });
      const doc = await Prescription.create({ id, kind, familyId: membership.familyId, memberId, imagePath, mimeType: file.type, sizeBytes: buffer.length, status: 'UPLOADED', createdByUserId: user.id });
      return ok(clean(doc), 201);
    }),
  };
}

export function uploadItemRoutes(kind) {
  return {
    GET: handler(async (request, { params }) => {
      const { membership } = await requireFamilyAccess(request, params.familyId);
      const doc = await Prescription.findOne({ id: params.id, familyId: membership.familyId, kind: kindFilter(kind) }).lean();
      if (!doc) throw notFound();
      return ok(clean(doc));
    }),
    DELETE: handler(async (request, { params }) => {
      const { membership } = await requireFamilyAccess(request, params.familyId, { write: true });
      const doc = await Prescription.findOne({ id: params.id, familyId: membership.familyId, kind: kindFilter(kind) });
      if (!doc) throw notFound();
      await deletePrescriptionImage(doc.imagePath);
      await doc.deleteOne();
      return ok({ deleted: true });
    }),
  };
}

/** Streams the original image. Requires family membership; never publicly reachable. */
export function uploadImageRoute(kind) {
  return handler(async (request, { params }) => {
    const { membership } = await requireFamilyAccess(request, params.familyId);
    const doc = await Prescription.findOne({ id: params.id, familyId: membership.familyId, kind: kindFilter(kind) }).lean();
    if (!doc) throw notFound();
    const bytes = await readPrescriptionImage(doc.imagePath);
    return new Response(bytes, { status: 200, headers: { 'content-type': doc.mimeType, 'cache-control': 'private, max-age=0, no-store', 'content-length': String(bytes.length) } });
  });
}

/**
 * Runs AI extraction and stores the result as a DRAFT on the upload.
 * Drafts never create medicines; the client must review and explicitly confirm.
 */
export function uploadExtractRoute(kind) {
  const extract = kind === 'MEDICINE_PHOTO' ? extractMedicinePhoto : extractPrescription;
  const notReadable = kind === 'MEDICINE_PHOTO' ? 'Image not readable as medicine packaging.' : 'Image not readable as a prescription.';
  return handler(async (request, { params }) => {
    const { membership } = await requireFamilyAccess(request, params.familyId, { write: true });
    const doc = await Prescription.findOne({ id: params.id, familyId: membership.familyId, kind: kindFilter(kind) });
    if (!doc) throw notFound();
    if (!isExtractionConfigured()) {
      throw new ApiError(503, 'extraction_not_configured', 'AI reading is not enabled on this server. Please enter the details manually.');
    }
    doc.status = 'EXTRACTING';
    doc.updatedAt = Date.now();
    await doc.save();
    try {
      const buffer = await readPrescriptionImage(doc.imagePath);
      const result = await extract({ buffer, mimeType: doc.mimeType });
      doc.extraction = {
        provider: result.provider,
        model: result.model,
        extractedAt: Date.now(),
        notes: result.legible ? result.notes : `${notReadable} ${result.notes}`.trim(),
        items: result.items,
        error: null,
      };
      doc.status = 'EXTRACTED';
    } catch (error) {
      doc.status = 'EXTRACTION_FAILED';
      doc.extraction = { ...(doc.extraction?.toObject?.() ?? {}), error: error.message, items: [] };
      doc.updatedAt = Date.now();
      await doc.save();
      console.error('[extract]', kind, 'failed', error.code || error.message);
      throw new ApiError(502, error.code || 'extraction_failed', 'Could not read the image. You can retry or enter the details manually.');
    }
    doc.updatedAt = Date.now();
    await doc.save();
    return ok(clean(doc));
  });
}
