import { handler, ok, requireUser } from '../../../../../lib/http.js';
import { publicUser } from '../../../../../models/index.js';
import { listFamiliesForUser } from '../../../../../services/family.js';

export const GET = handler(async (request) => {
  const user = await requireUser(request);
  const families = await listFamiliesForUser(user);
  return ok({ user: publicUser(user), families });
});
