import { handler, ok, readJson, requireUser } from '../../../../lib/http.js';
import { familyCreateSchema } from '../../../../services/validation.js';
import { createFamily, listFamiliesForUser } from '../../../../services/family.js';

export const GET = handler(async (request) => {
  const user = await requireUser(request);
  return ok(await listFamiliesForUser(user));
});

export const POST = handler(async (request) => {
  const user = await requireUser(request);
  const body = familyCreateSchema.parse(await readJson(request));
  return ok(await createFamily(user, body.name), 201);
});
