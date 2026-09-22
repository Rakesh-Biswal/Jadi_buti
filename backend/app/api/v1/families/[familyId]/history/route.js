import { handler, ok, requireFamilyAccess } from '../../../../../../lib/http.js';
import { MedicationEvent, clean } from '../../../../../../models/index.js';

/** Medication history: events that have been acted on or missed, newest first, with adherence counts. */
export const GET = handler(async (request, { params }) => {
  const { membership } = await requireFamilyAccess(request, params.familyId);
  const sp = new URL(request.url).searchParams;
  const q = { familyId: membership.familyId, deleted: { $ne: true } };
  if (sp.get('from') || sp.get('to')) {
    q.localDate = {};
    if (sp.get('from')) q.localDate.$gte = sp.get('from');
    if (sp.get('to')) q.localDate.$lte = sp.get('to');
  }
  for (const k of ['memberId', 'medicineId']) if (sp.get(k)) q[k] = sp.get(k);
  q.status = sp.get('status') ? sp.get('status') : { $in: ['TAKEN', 'MISSED', 'SKIPPED'] };
  const events = await MedicationEvent.find(q).sort({ scheduledAt: -1 }).limit(1000).lean();
  const counts = { scheduled: events.length, taken: 0, missed: 0, skipped: 0 };
  for (const e of events) {
    if (e.status === 'TAKEN') counts.taken++;
    else if (e.status === 'MISSED') counts.missed++;
    else if (e.status === 'SKIPPED') counts.skipped++;
  }
  return ok({ events: events.map(clean), adherence: counts });
});
