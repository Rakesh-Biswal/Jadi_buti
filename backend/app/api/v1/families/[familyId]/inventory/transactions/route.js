import { collectionRoutes } from '../../../../../../../lib/crud.js';

export const { GET, POST } = collectionRoutes('transaction', {
  filter: (sp) => (sp.get('medicineId') ? { medicineId: sp.get('medicineId') } : {}),
});
