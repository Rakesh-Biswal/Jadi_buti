import mongoose from 'mongoose';
import { handler, ok } from '../../../lib/http.js';
import { isExtractionConfigured } from '../../../services/extraction.js';

export const GET = handler(async () => {
  return ok({
    service: 'jadi-buti-backend',
    version: '1.0.0',
    time: Date.now(),
    db: mongoose.connection.readyState === 1 ? 'connected' : 'disconnected',
    dbName: mongoose.connection.name,
    aiExtraction: isExtractionConfigured() ? 'configured' : 'not_configured',
  });
});
