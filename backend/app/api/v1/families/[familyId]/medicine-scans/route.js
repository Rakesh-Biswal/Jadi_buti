import { uploadCollectionRoutes } from '../../../../../../lib/uploads.js';

/** Photos of medicine packaging (strip/box/bottle) for name/strength/form/composition extraction. */
export const { GET, POST } = uploadCollectionRoutes('MEDICINE_PHOTO');
