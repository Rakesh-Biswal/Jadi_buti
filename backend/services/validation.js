import { z } from 'zod';
import { MEDICINE_TYPES, FOOD_INSTRUCTIONS, FREQUENCIES } from '../models/Medicine.js';
import { EVENT_STATUSES } from '../models/MedicationEvent.js';
import { TX_TYPES } from '../models/InventoryTransaction.js';

export const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Expected YYYY-MM-DD');
export const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Expected HH:mm');
const uuid = z.string().min(8).max(200);

export const registerSchema = z.object({
  email: z.string().email().max(200),
  password: z.string().min(8).max(200),
  name: z.string().trim().min(1).max(100),
  deviceName: z.string().max(100).optional(),
});

export const loginSchema = z.object({
  email: z.string().email().max(200),
  password: z.string().min(1).max(200),
  deviceName: z.string().max(100).optional(),
});

export const familyCreateSchema = z.object({ name: z.string().trim().min(1).max(100) });
export const joinSchema = z.object({ code: z.string().trim().min(4).max(20) });
export const inviteCreateSchema = z.object({
  canEdit: z.boolean().default(true),
  expiresInHours: z.number().int().min(1).max(24 * 30).default(72),
});
export const membershipUpdateSchema = z.object({
  canEdit: z.boolean().optional(),
  receiveMissedDoseAlerts: z.boolean().optional(),
});

const syncBase = {
  id: uuid,
  familyId: uuid,
  version: z.number().int().min(0).optional(),
  updatedAt: z.number().int().optional(),
  deleted: z.boolean().optional(),
};

export const memberSchema = z.object({
  ...syncBase,
  name: z.string().trim().min(1).max(100),
  dateOfBirth: isoDate.nullable().optional(),
  notes: z.string().max(2000).optional().default(''),
  photoPath: z.string().max(500).nullable().optional(),
  active: z.boolean().optional().default(true),
  createdAt: z.number().int().optional(),
});

export const medicineSchema = z
  .object({
    ...syncBase,
    memberId: uuid,
    name: z.string().trim().min(1).max(200),
    strength: z.string().max(100).optional().default(''),
    type: z.enum(MEDICINE_TYPES).optional().default('TABLET'),
    doseUnit: z.string().trim().min(1).max(30).optional().default('tablet'),
    foodInstruction: z.enum(FOOD_INSTRUCTIONS).optional().default('NONE'),
    instructions: z.string().max(2000).optional().default(''),
    startDate: isoDate,
    endDate: isoDate.nullable().optional(),
    schedule: z.object({
      frequency: z.enum(FREQUENCIES),
      weekdays: z.array(z.number().int().min(1).max(7)).optional().default([]),
      intervalDays: z.number().int().min(1).max(365).optional().default(1),
      referenceDate: isoDate.nullable().optional(),
    }),
    doseTimes: z.array(z.object({ time: hhmm, amount: z.number().positive().max(10000), meal: z.enum(['BREAKFAST', 'LUNCH', 'DINNER', 'BEDTIME']).nullable().optional() })).min(1).max(12),
    inventory: z
      .object({
        track: z.boolean().optional().default(true),
        lowStockType: z.enum(['PERCENT', 'QUANTITY']).optional().default('PERCENT'),
        lowStockValue: z.number().min(0).max(100000).optional().default(5),
      })
      .optional()
      .default({}),
    prescriptionId: z.string().nullable().optional(),
    active: z.boolean().optional().default(true),
    createdAt: z.number().int().optional(),
  })
  .superRefine((m, ctx) => {
    if (m.endDate && m.endDate < m.startDate) ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'endDate before startDate' });
    if ((m.schedule.frequency === 'SPECIFIC_WEEKDAYS' || m.schedule.frequency === 'WEEKLY') && m.schedule.weekdays.length === 0) {
      ctx.addIssue({ code: 'custom', path: ['schedule', 'weekdays'], message: 'weekdays required for this frequency' });
    }
    const times = new Set(m.doseTimes.map((d) => d.time));
    if (times.size !== m.doseTimes.length) ctx.addIssue({ code: 'custom', path: ['doseTimes'], message: 'duplicate dose times' });
  });

export const eventSchema = z.object({
  ...syncBase,
  memberId: uuid,
  medicineId: uuid,
  localDate: isoDate,
  time: hhmm,
  zoneId: z.string().max(64).optional().default('UTC'),
  scheduledAt: z.number().int(),
  doseAmount: z.number().positive(),
  doseUnit: z.string().max(30).optional().default('tablet'),
  status: z.enum(EVENT_STATUSES),
  actualAt: z.number().int().nullable().optional(),
  recordedByUserId: z.string().nullable().optional(),
  snoozedUntil: z.number().int().nullable().optional(),
  note: z.string().max(500).optional().default(''),
  statusHistory: z
    .array(z.object({ status: z.enum(EVENT_STATUSES), at: z.number().int(), byUserId: z.string().nullable().optional(), actualAt: z.number().int().nullable().optional() }))
    .optional()
    .default([]),
});

export const transactionSchema = z.object({
  ...syncBase,
  medicineId: uuid,
  type: z.enum(TX_TYPES),
  quantityDelta: z.number(),
  eventId: z.string().nullable().optional(),
  note: z.string().max(500).optional().default(''),
  createdAt: z.number().int().optional(),
  byUserId: z.string().nullable().optional(),
});

export const syncPushSchema = z.object({
  familyId: uuid,
  changes: z
    .array(
      z.object({
        entity: z.enum(['member', 'medicine', 'event', 'transaction']),
        payload: z.record(z.any()),
      }),
    )
    .max(500),
});
