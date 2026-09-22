import mongoose from 'mongoose';
import dns from 'node:dns';
import { config } from './env.js';

// Cache the connection across hot reloads / route invocations (Next.js pattern).
const globalCache = globalThis.__jadiButiMongoose ?? (globalThis.__jadiButiMongoose = { conn: null, promise: null });

const connectOptions = () => ({ dbName: config.mongoDb, serverSelectionTimeoutMS: 15000, maxPoolSize: 10 });

/**
 * Some local DNS resolvers refuse the SRV lookups that mongodb+srv:// URIs need.
 * If the first attempt fails on SRV resolution, retry once using public resolvers
 * (override with DNS_FALLBACK_SERVERS=comma,separated or set it empty to disable).
 */
async function connectWithDnsFallback() {
  mongoose.set('strictQuery', true);
  try {
    return await mongoose.connect(config.mongoUri, connectOptions());
  } catch (error) {
    const fallback = (process.env.DNS_FALLBACK_SERVERS ?? '8.8.8.8,1.1.1.1').split(',').map((s) => s.trim()).filter(Boolean);
    const isSrvFailure = /querySrv|ENOTFOUND|ECONNREFUSED/i.test(error?.message || '');
    if (!isSrvFailure || fallback.length === 0) throw error;
    console.warn(`[db] SRV lookup failed (${error.message}); retrying with DNS servers ${fallback.join(', ')}`);
    dns.setServers(fallback);
    return mongoose.connect(config.mongoUri, connectOptions());
  }
}

export async function connectDb() {
  if (globalCache.conn) return globalCache.conn;
  if (!globalCache.promise) {
    globalCache.promise = connectWithDnsFallback().catch((e) => {
      globalCache.promise = null;
      throw e;
    });
  }
  globalCache.conn = await globalCache.promise;
  return globalCache.conn;
}

export async function disconnectDb() {
  if (globalCache.conn) {
    await mongoose.disconnect();
    globalCache.conn = null;
    globalCache.promise = null;
  }
}
