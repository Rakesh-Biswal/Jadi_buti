import { model } from './_shared.js';

export const PRESCRIPTION_STATUSES = ['UPLOADED', 'EXTRACTING', 'EXTRACTED', 'EXTRACTION_FAILED', 'CONFIRMED', 'REJECTED'];

/**
 * An uploaded prescription image and (optionally) its AI-extracted DRAFT.
 * Extractions are never applied automatically: the user reviews, edits and
 * explicitly confirms before any medicine is created.
 */
export const Prescription = model('Prescription', {
  id: { type: String, required: true, unique: true },
  kind: { type: String, enum: ['PRESCRIPTION', 'MEDICINE_PHOTO'], default: 'PRESCRIPTION', index: true },
  familyId: { type: String, required: true, index: true },
  memberId: { type: String, default: null },
  imagePath: { type: String, required: true }, // relative to STORAGE_DIR, never public
  mimeType: { type: String, required: true },
  sizeBytes: { type: Number, default: 0 },
  status: { type: String, enum: PRESCRIPTION_STATUSES, default: 'UPLOADED' },
  extraction: {
    provider: { type: String, default: null },
    model: { type: String, default: null },
    extractedAt: { type: Number, default: null },
    notes: { type: String, default: '' }, // provider-level notes (e.g. "image partially unreadable")
    items: { type: Array, default: [] }, // ExtractedMedicine[] – see services/extraction.js
    error: { type: String, default: null },
  },
  confirmedMedicineIds: { type: [String], default: [] },
  createdByUserId: { type: String, required: true },
  createdAt: { type: Number, default: () => Date.now() },
  updatedAt: { type: Number, default: () => Date.now() },
});
