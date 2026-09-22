import { handler, ok, readJson, unauthorized } from '../../../../../lib/http.js';
import { loginSchema } from '../../../../../services/validation.js';
import { User, Session, publicUser } from '../../../../../models/index.js';
import { verifyPassword, signAccessToken, signRefreshToken } from '../../../../../lib/auth.js';
import { newId } from '../../../../../lib/ids.js';

export const POST = handler(async (request) => {
  const body = loginSchema.parse(await readJson(request));
  const user = await User.findOne({ email: body.email.toLowerCase() });
  if (!user || user.disabled || !(await verifyPassword(body.password, user.passwordHash))) {
    throw unauthorized('Incorrect email or password');
  }
  const session = await Session.create({ id: newId(), userId: user.id, deviceName: body.deviceName });
  return ok({ user: publicUser(user), accessToken: signAccessToken(user), refreshToken: signRefreshToken(user, session.id) });
});
