import { model, syncFields } from './_shared.js';

export const EVENT_STATUSES = ['UPCOMING', 'DUE', 'TAKEN', 'MISSED', 'SKIPPED', 'SNOOZED'];
export const TERMINAL_STATUSES = ['TAKEN', 'SKIPPED'];

/**
 * One scheduled dose. id is deterministic: `${medicineId}:${localDate}:${time}`
 * so that every device and the server agree on identity without coordination.
 * statusHistory is append-only: earlier states are never overwritten.
 */
export const MedicationEvent = model(
  'MedicationEvent',
  {
    ...syncFields,
    memberId: { type: String, required: true, index: true },
    medicineId: { type: String, required: true, index: true },
    localDate: { type: String, required: true }, // YYYY-MM-DD in the schedule's zone
    time: { type: String, required: true }, // HH:mm
    zoneId: { type: String, default: 'UTC' },
    scheduledAt: { type: Number, required: true, index: true }, // epoch ms
    doseAmount: { type: Number, required: true },
    doseUnit: { type: String, default: 'tablet' },
    status: { type: String, enum: EVENT_STATUSES, default: 'UPCOMING' },
    actualAt: { type: Number, default: null },
    recordedByUserId: { type: String, default: null },
    snoozedUntil: { type: Number, default: null },
    note: { type: String, default: '' },
    statusHistory: {
      type: [{ _id: false, status: String, at: Number, byUserId: String, actualAt: Number }],
      default: [],
    },
  },
  { indexes: [[{ familyId: 1, localDate: 1 }], [{ familyId: 1, memberId: 1, scheduledAt: -1 }]] },
);
