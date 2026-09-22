import { handler, ok, readJson, conflict } from '../../../../../lib/http.js';
import { registerSchema } from '../../../../../services/validation.js';
import { User, Session, publicUser } from '../../../../../models/index.js';
import { hashPassword, signAccessToken, signRefreshToken } from '../../../../../lib/auth.js';
import { newId } from '../../../../../lib/ids.js';

export const POST = handler(async (request) => {
  const body = registerSchema.parse(await readJson(request));
  const email = body.email.toLowerCase();
  if (await User.exists({ email })) throw conflict('An account with this email already exists');
  const user = await User.create({ id: newId(), email, name: body.name, passwordHash: await hashPassword(body.password) });
  const session = await Session.create({ id: newId(), userId: user.id, deviceName: body.deviceName });
  return ok({ user: publicUser(user), accessToken: signAccessToken(user), refreshToken: signRefreshToken(user, session.id) }, 201);
});
