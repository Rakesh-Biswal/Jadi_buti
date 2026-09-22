const required = ['MONGODB_URI', 'JWT_ACCESS_SECRET', 'JWT_REFRESH_SECRET'];

export function env(name, fallback) {
  const v = process.env[name];
  if (v === undefined || v === '') {
    if (fallback !== undefined) return fallback;
    if (required.includes(name)) throw new Error(`Missing required environment variable ${name}`);
    return undefined;
  }
  return v;
}

export const config = {
  get mongoUri() { return env('MONGODB_URI'); },
  get mongoDb() { return env('MONGODB_DB', 'JadiButi'); },
  get accessSecret() { return env('JWT_ACCESS_SECRET'); },
  get refreshSecret() { return env('JWT_REFRESH_SECRET'); },
  get accessTtl() { return env('ACCESS_TOKEN_TTL', '1h'); },
  get refreshTtl() { return env('REFRESH_TOKEN_TTL', '60d'); },
  get storageDir() { return env('STORAGE_DIR', './storage'); },
  get anthropicKey() { return env('ANTHROPIC_API_KEY', ''); },
  get extractionModel() { return env('EXTRACTION_MODEL', 'claude-opus-5'); },
};
