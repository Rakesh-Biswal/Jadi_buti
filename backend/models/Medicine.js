import { model, syncFields } from './_shared.js';

export const MEDICINE_TYPES = ['TABLET', 'CAPSULE', 'SYRUP', 'LIQUID', 'DROPS', 'INJECTION', 'OTHER'];
export const FOOD_INSTRUCTIONS = ['BEFORE_FOOD', 'AFTER_FOOD', 'WITH_FOOD', 'EMPTY_STOMACH', 'NONE'];
export const FREQUENCIES = ['DAILY', 'ALTERNATE_DAYS', 'SPECIFIC_WEEKDAYS', 'WEEKLY', 'CUSTOM_INTERVAL'];

/**
 * Medicine + its schedule rules (embedded, since they are always read together).
 * Medication events are generated from these rules deterministically on each device.
 */
export const Medicine = model(
  'Medicine',
  {
    ...syncFields,
    memberId: { type: String, required: true, index: true },
    name: { type: String, required: true, trim: true },
    strength: { type: String, default: '' },
    type: { type: String, enum: MEDICINE_TYPES, default: 'TABLET' },
    doseUnit: { type: String, default: 'tablet' }, // tablet, capsule, ml, drop, ...
    foodInstruction: { type: String, enum: FOOD_INSTRUCTIONS, default: 'NONE' },
    instructions: { type: String, default: '' },
    startDate: { type: String, required: true }, // YYYY-MM-DD (local date)
    endDate: { type: String, default: null }, // null = ongoing
    schedule: {
      frequency: { type: String, enum: FREQUENCIES, default: 'DAILY' },
      weekdays: { type: [Number], default: [] }, // ISO weekdays 1=Mon..7=Sun (SPECIFIC_WEEKDAYS / WEEKLY)
      intervalDays: { type: Number, default: 1 }, // CUSTOM_INTERVAL / ALTERNATE_DAYS(=2)
      referenceDate: { type: String, default: null }, // anchor for interval schedules; defaults to startDate
    },
    doseTimes: {
      type: [{ _id: false, time: { type: String, required: true }, amount: { type: Number, required: true }, meal: { type: String, enum: ['BREAKFAST', 'LUNCH', 'DINNER', 'BEDTIME', null], default: null } }],
      default: [],
    },
    inventory: {
      track: { type: Boolean, default: true },
      lowStockType: { type: String, enum: ['PERCENT', 'QUANTITY'], default: 'PERCENT' },
      lowStockValue: { type: Number, default: 5 },
    },
    prescriptionId: { type: String, default: null },
    active: { type: Boolean, default: true },
    createdAt: { type: Number, default: () => Date.now() },
  },
  { indexes: [[{ familyId: 1, memberId: 1 }]] },
);
