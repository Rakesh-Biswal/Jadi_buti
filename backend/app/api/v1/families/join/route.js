import { handler, ok, readJson, requireUser } from '../../../../../lib/http.js';
import { joinSchema } from '../../../../../services/validation.js';
import { joinFamily } from '../../../../../services/family.js';

export const POST = handler(async (request) => {
  const user = await requireUser(request);
  const body = joinSchema.parse(await readJson(request));
  return ok(await joinFamily(user, body.code));
});
