import { handler, ok, readJson, unauthorized } from '../../../../../lib/http.js';
import { User, Session, publicUser } from '../../../../../models/index.js';
import { verifyRefreshToken, signAccessToken, signRefreshToken } from '../../../../../lib/auth.js';

export const POST = handler(async (request) => {
  const { refreshToken } = await readJson(request);
  if (!refreshToken) throw unauthorized('refreshToken required');
  let payload;
  try {
    payload = verifyRefreshToken(refreshToken);
  } catch {
    throw unauthorized('Invalid or expired refresh token');
  }
  const session = await Session.findOne({ id: payload.sid, userId: payload.sub });
  if (!session || session.revokedAt) throw unauthorized('Session revoked');
  const user = await User.findOne({ id: payload.sub });
  if (!user || user.disabled) throw unauthorized('Account not available');
  session.lastUsedAt = Date.now();
  await session.save();
  return ok({ user: publicUser(user), accessToken: signAccessToken(user), refreshToken: signRefreshToken(user, session.id) });
});
