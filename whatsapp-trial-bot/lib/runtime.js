// Merakit dependensi dari environment variable Vercel. Dibuat lazy dan di-cache antar pemanggilan.
import { bacaKonfigurasi } from './handler.js';
import { kunciPrivatDari } from './license.js';
import { buatUpstashStore } from './store.js';

let cache;

export function ambilRuntime(env = process.env) {
  if (!cache) {
    cache = {
      config: bacaKonfigurasi(env),
      store: buatUpstashStore({
        url: env.KV_REST_API_URL ?? env.UPSTASH_REDIS_REST_URL,
        token: env.KV_REST_API_TOKEN ?? env.UPSTASH_REDIS_REST_TOKEN,
      }),
      privateKey: kunciPrivatDari(env.TRIAL_PRIVATE_KEY_HEX, env.TRIAL_PUBLIC_KEY),
    };
  }
  return cache;
}
