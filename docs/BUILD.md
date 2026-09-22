# Building Jadi-Buti

## Requirements

- JDK 17 (`JAVA_HOME` pointing at it)
- Android SDK with platform 36 and build-tools 35+ (`android/local.properties` → `sdk.dir=...`)
- Node.js 20+ for the backend

## Backend

```bash
cd backend
npm install
npm run dev        # development
npm run build && npm start   # production
```

Environment (`backend/.env`, never committed):

| Variable | Purpose |
| --- | --- |
| `MONGODB_URI` | Atlas connection string (credentials stay server-side) |
| `MONGODB_DB` | `JadiButi` (separate from any Chefo database) |
| `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` | Token signing keys |
| `STORAGE_DIR` | Where prescription images are stored (private, served only through authenticated API) |
| `ANTHROPIC_API_KEY` | Optional. Enables AI prescription reading. Without it the app offers manual entry. |
| `DNS_FALLBACK_SERVERS` | Optional. Public resolvers used if the local DNS refuses `mongodb+srv` SRV lookups. |

## Android

### Debug APK

```bash
cd android
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Debug builds default to `http://localhost:3000/api/v1/` so a USB-connected phone can reach the dev server through `adb reverse tcp:3000 tcp:3000`. The server address can also be changed inside the app (sign-in screen → "Server", or Settings → Server address).

### Release APK

1. Create a keystore once (keep it private, it is git-ignored):

   ```bash
   keytool -genkeypair -v -keystore android/jadibuti-release.jks -alias jadibuti -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `android/keystore.properties`:

   ```properties
   storeFile=jadibuti-release.jks
   storePassword=...
   keyAlias=jadibuti
   keyPassword=...
   ```

3. Set the production API URL in `android/app/build.gradle.kts` (`DEFAULT_API_BASE_URL` in `defaultConfig`), then:

   ```bash
   ./gradlew :app:assembleRelease
   # → app/build/outputs/apk/release/app-release.apk
   ```

Release builds are minified (R8) and only allow HTTPS to the API, except `localhost` for testing.

### Versioning

`versionCode` / `versionName` live in `android/app/build.gradle.kts` (`defaultConfig`). Bump both for every distributed build.

## Tests

```bash
cd backend && npm run test:unit                 # merge rules, inventory math, validation
cd backend && npm run test:integration          # end-to-end API (dev server must be running)
cd android && ./gradlew :domain:test            # scheduling engine, inventory, status/merge policies
cd android && ./gradlew :app:testDebugUnitTest  # app-level unit tests
```
