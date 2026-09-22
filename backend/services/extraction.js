import Anthropic from '@anthropic-ai/sdk';
import { z } from 'zod';
import { zodOutputFormat } from '@anthropic-ai/sdk/helpers/zod';
import { config } from '../lib/env.js';

/**
 * AI-assisted prescription extraction.
 *
 * Safety contract (see product requirement 27):
 *  - The model only transcribes what is legible. It must not guess, infer, correct
 *    or complete missing information.
 *  - Every field carries `uncertain: true` when the text was not clearly legible; the
 *    app shows "Unable to confidently read this field. Please verify." for those.
 *  - Output is always a DRAFT. Nothing is created until the user reviews and confirms.
 */

const Field = (inner) =>
  z.object({
    value: inner.nullable().describe('The transcribed value, or null if not present/legible'),
    uncertain: z.boolean().describe('true when the text was hard to read or ambiguous'),
  });

export const ExtractedMedicineSchema = z.object({
  name: Field(z.string()),
  strength: Field(z.string()),
  type: Field(z.enum(['TABLET', 'CAPSULE', 'SYRUP', 'LIQUID', 'DROPS', 'INJECTION', 'OTHER'])),
  doseAmount: Field(z.number()).describe('Numeric dose per intake, e.g. 1 for "1 tablet", 5 for "5 ml"'),
  doseUnit: Field(z.string()).describe('e.g. tablet, capsule, ml, drop'),
  frequency: Field(z.enum(['DAILY', 'ALTERNATE_DAYS', 'SPECIFIC_WEEKDAYS', 'WEEKLY', 'CUSTOM_INTERVAL'])),
  timesPerDay: Field(z.number().int()),
  times: Field(z.array(z.string())).describe('Explicit clock times in HH:mm if the prescription states them; otherwise null'),
  foodInstruction: Field(z.enum(['BEFORE_FOOD', 'AFTER_FOOD', 'WITH_FOOD', 'EMPTY_STOMACH', 'NONE'])),
  durationDays: Field(z.number().int()).describe('Duration in days if stated, e.g. "30 days" -> 30; "ongoing"/absent -> null'),
  additionalInstructions: Field(z.string()),
  rawText: z.string().describe('The exact line(s) on the prescription this item was read from'),
});

export const ExtractionResultSchema = z.object({
  legible: z.boolean().describe('false if the image is not a readable prescription at all'),
  notes: z.string().describe('Short note about image quality or anything the reviewer must check'),
  medicines: z.array(ExtractedMedicineSchema),
});

const SYSTEM_PROMPT = `You are a transcription assistant inside a family medication reminder app.
Your only job is to transcribe medicines that are legibly written on a prescription image into structured fields.

Strict rules:
- Transcribe only what is clearly written. Never guess, infer, normalise, correct or complete anything.
- If a field is not written on the prescription, set value to null. Do not fill defaults.
- If a value is partially legible or ambiguous (handwriting, blur, cut off), still transcribe your best reading BUT set uncertain to true.
- Abbreviations: only map universally standard ones (e.g. "1-0-1" means morning and night doses -> timesPerDay 2; "OD" once daily; "BD" twice daily; "TDS" three times daily; "AC" before food; "PC" after food). If unsure, keep the raw text and mark uncertain.
- Never add medicines that are not on the prescription. Never diagnose, advise, or comment on the treatment.
- If the image is not a prescription or is unreadable, set legible=false and return an empty list.`;

export const MedicinePhotoItemSchema = z.object({
  name: Field(z.string()).describe('Brand or product name exactly as printed'),
  strength: Field(z.string()).describe('Strength exactly as printed, e.g. "40 mg", "5 mg/5 ml"'),
  form: Field(z.enum(['TABLET', 'CAPSULE', 'SYRUP', 'LIQUID', 'DROPS', 'INJECTION', 'OTHER'])).describe('Dosage form if printed or unambiguous from the packaging'),
  composition: Field(z.string()).describe('Active ingredient(s) exactly as printed, e.g. "Telmisartan 40 mg"'),
  rawText: z.string().describe('The printed text this item was read from'),
});

export const MedicinePhotoResultSchema = z.object({
  legible: z.boolean().describe('false if the image does not show readable medicine packaging'),
  notes: z.string().describe('Short note about image quality or anything the reviewer must check'),
  medicines: z.array(MedicinePhotoItemSchema).describe('One entry per distinct medicine visible'),
});

const MEDICINE_PHOTO_PROMPT = `You are a transcription assistant inside a family medication reminder app.
Your only job is to transcribe what is printed on medicine packaging (strip, box, bottle, label) into structured fields.

Strict rules:
- Transcribe only text that is clearly printed and legible. Never guess, infer, normalise, correct or complete anything.
- If a field is not printed or not visible, set value to null. Do not fill defaults.
- If text is partially legible or ambiguous (blur, glare, cut off), transcribe your best reading BUT set uncertain to true.
- Form: only set it if the packaging says it (e.g. "Tablets", "Capsules", "Syrup") or it is unambiguous (a blister strip of tablets). Otherwise null.
- Composition: copy the active ingredient line as printed; do not expand abbreviations or add salts you cannot read.
- Never suggest, recommend or comment on any medicine, dose or condition.
- If the image is not medicine packaging or is unreadable, set legible=false and return an empty list.`;

export function isExtractionConfigured() {
  return Boolean(config.anthropicKey);
}

/**
 * @param {{ buffer: Buffer, mimeType: string }} image
 * @returns {Promise<{ provider: string, model: string, legible: boolean, notes: string, items: object[] }>}
 */
export async function extractPrescription(image) {
  if (!isExtractionConfigured()) {
    const err = new Error('AI extraction is not configured on this server');
    err.code = 'extraction_not_configured';
    throw err;
  }
  const client = new Anthropic({ apiKey: config.anthropicKey });
  const model = config.extractionModel;
  const response = await client.messages.parse({
    model,
    max_tokens: 16000,
    system: SYSTEM_PROMPT,
    messages: [
      {
        role: 'user',
        content: [
          { type: 'image', source: { type: 'base64', media_type: image.mimeType, data: image.buffer.toString('base64') } },
          { type: 'text', text: 'Transcribe the medicines on this prescription into the requested structure. Mark anything unclear as uncertain.' },
        ],
      },
    ],
    output_config: { format: zodOutputFormat(ExtractionResultSchema) },
  });
  if (response.stop_reason === 'refusal') {
    const err = new Error('The AI service declined to process this image');
    err.code = 'extraction_refused';
    throw err;
  }
  const parsed = response.parsed_output;
  if (!parsed) {
    const err = new Error('AI response could not be parsed');
    err.code = 'extraction_parse_failed';
    throw err;
  }
  return {
    provider: 'anthropic',
    model,
    legible: parsed.legible,
    notes: parsed.notes,
    items: parsed.medicines.map(postProcess),
  };
}

/**
 * Medicine packaging photo -> name / strength / form / composition (draft, per-field uncertainty).
 * @param {{ buffer: Buffer, mimeType: string }} image
 */
export async function extractMedicinePhoto(image) {
  if (!isExtractionConfigured()) {
    const err = new Error('AI extraction is not configured on this server');
    err.code = 'extraction_not_configured';
    throw err;
  }
  const client = new Anthropic({ apiKey: config.anthropicKey });
  const model = config.extractionModel;
  const response = await client.messages.parse({
    model,
    max_tokens: 8000,
    system: MEDICINE_PHOTO_PROMPT,
    messages: [
      {
        role: 'user',
        content: [
          { type: 'image', source: { type: 'base64', media_type: image.mimeType, data: image.buffer.toString('base64') } },
          { type: 'text', text: 'Transcribe the medicine name, strength, form and composition printed on this packaging. Mark anything unclear as uncertain.' },
        ],
      },
    ],
    output_config: { format: zodOutputFormat(MedicinePhotoResultSchema) },
  });
  if (response.stop_reason === 'refusal') {
    const err = new Error('The AI service declined to process this image');
    err.code = 'extraction_refused';
    throw err;
  }
  const parsed = response.parsed_output;
  if (!parsed) {
    const err = new Error('AI response could not be parsed');
    err.code = 'extraction_parse_failed';
    throw err;
  }
  return { provider: 'anthropic', model, legible: parsed.legible, notes: parsed.notes, items: parsed.medicines.map(postProcess) };
}

/** Defensive normalisation: uncertain is forced on when a value is missing but the item exists. */
export function postProcess(item) {
  const out = { ...item };
  for (const key of Object.keys(out)) {
    const f = out[key];
    if (f && typeof f === 'object' && 'value' in f && 'uncertain' in f) {
      if (f.value === null || f.value === '') out[key] = { value: null, uncertain: true };
      if (Array.isArray(f.value) && f.value.length === 0) out[key] = { value: null, uncertain: true };
    }
  }
  return out;
}
