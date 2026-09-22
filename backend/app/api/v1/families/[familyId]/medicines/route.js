import { collectionRoutes } from '../../../../../../lib/crud.js';

export const { GET, POST } = collectionRoutes('medicine', {
  filter: (sp) => (sp.get('memberId') ? { memberId: sp.get('memberId') } : {}),
});
