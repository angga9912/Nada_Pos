import { generateKeyPairSync } from 'node:crypto';

// Pasangan kunci uji dalam format yang sama dengan tools/generate_license.py --init.
export function buatKunciUji() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const d = privateKey.export({ format: 'jwk' }).d; // base64url
  const hex = Buffer.from(d, 'base64url').toString('hex').padStart(64, '0');
  const publicBase64 = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  return { hex, publicBase64, publicKey };
}
