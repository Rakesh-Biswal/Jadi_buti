import { collectionRoutes } from '../../../../../../lib/crud.js';

/** Medication events. Filter by ?from=YYYY-MM-DD&to=YYYY-MM-DD&memberId=&medicineId=&status= */
export const { GET, POST } = collectionRoutes('event', {
  filter: (sp) => {
    const q = {};
    if (sp.get('from') || sp.get('to')) {
      q.localDate = {};
      if (sp.get('from')) q.localDate.$gte = sp.get('from');
      if (sp.get('to')) q.localDate.$lte = sp.get('to');
    }
    for (const k of ['memberId', 'medicineId', 'status']) if (sp.get(k)) q[k] = sp.get(k);
    return q;
  },
});
