import { handler, ok, readJson, requireUser } from '../../../../../lib/http.js';
import { Session } from '../../../../../models/index.js';
import { verifyRefreshToken } from '../../../../../lib/auth.js';

export const POST = handler(async (request) => {
  const user = await requireUser(request);
  const { refreshToken } = await readJson(request).catch(() => ({}));
  if (refreshToken) {
    try {
      const payload = verifyRefreshToken(refreshToken);
      if (payload.sub === user.id) await Session.updateOne({ id: payload.sid }, { $set: { revokedAt: Date.now() } });
    } catch {
      // ignore invalid token on logout
    }
  }
  return ok({ loggedOut: true });
});
