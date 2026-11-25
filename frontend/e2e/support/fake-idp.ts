import { BrowserContext } from '@playwright/test';
import { createSign, generateKeyPairSync } from 'node:crypto';

/**
 * Provedor OIDC falso para os testes e2e: responde descoberta, JWKS, autorização, token e logout
 * do realm configurado em `public/config.json`, sem Keycloak. Os tokens são JWT RS256 de verdade,
 * assinados com uma chave gerada a cada execução, então a SPA valida assinatura, emissor,
 * audiência, nonce e state como faria em produção.
 */
export const APP_ORIGIN = 'http://localhost:4200';
export const ISSUER = 'http://localhost:8180/realms/orderflow';
const CLIENT_ID = 'orderflow-spa';
const KEY_ID = 'e2e-key';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: KEY_ID, alg: 'RS256', use: 'sig' };

export interface Persona {
  readonly sub: string;
  readonly username: string;
  readonly name: string;
  readonly roles: readonly string[];
}

export const CUSTOMER: Persona = {
  sub: 'e2e-customer',
  username: 'ana',
  name: 'Ana Cliente',
  roles: ['USER'],
};

export const ADMIN: Persona = {
  sub: 'e2e-admin',
  username: 'olga',
  name: 'Olga Operação',
  roles: ['USER', 'ADMIN'],
};

function base64url(value: string | Buffer): string {
  return Buffer.from(value).toString('base64url');
}

export function signJwt(claims: Record<string, unknown>): string {
  const header = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: KEY_ID }));
  const payload = base64url(JSON.stringify(claims));
  const signature = createSign('RSA-SHA256').update(`${header}.${payload}`).sign(privateKey);
  return `${header}.${payload}.${base64url(signature)}`;
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}

/** Página que volta para a SPA, como o Keycloak faz depois do login ou do logout. */
function redirectPage(target: string) {
  return {
    status: 200,
    contentType: 'text/html',
    body: `<!doctype html><html lang="pt-BR"><title>Redirecionando</title><meta http-equiv="refresh" content="0;url=${escapeHtml(target)}"></html>`,
  };
}

const endpoint = (path: string) => `${ISSUER}/protocol/openid-connect/${path}`;

export async function useFakeIdentityProvider(
  context: BrowserContext,
  persona: Persona,
): Promise<void> {
  const cors = { 'Access-Control-Allow-Origin': APP_ORIGIN };
  let nonce = '';

  await context.route(`${ISSUER}/.well-known/openid-configuration`, (route) =>
    route.fulfill({
      headers: cors,
      json: {
        issuer: ISSUER,
        authorization_endpoint: endpoint('auth'),
        token_endpoint: endpoint('token'),
        jwks_uri: endpoint('certs'),
        end_session_endpoint: endpoint('logout'),
        userinfo_endpoint: endpoint('userinfo'),
        response_types_supported: ['code'],
        code_challenge_methods_supported: ['S256'],
        id_token_signing_alg_values_supported: ['RS256'],
      },
    }),
  );

  await context.route(endpoint('certs'), (route) =>
    route.fulfill({ headers: cors, json: { keys: [jwk] } }),
  );

  await context.route(
    (url) => url.href.startsWith(endpoint('auth')),
    (route) => {
      const request = new URL(route.request().url());
      if (request.searchParams.get('code_challenge_method') !== 'S256') {
        return route.fulfill({ status: 400, body: 'PKCE S256 is required' });
      }
      nonce = request.searchParams.get('nonce') ?? '';
      const back = new URL(request.searchParams.get('redirect_uri') ?? APP_ORIGIN);
      back.searchParams.set('code', 'e2e-authorization-code');
      back.searchParams.set('state', request.searchParams.get('state') ?? '');
      back.searchParams.set('session_state', 'e2e-session');
      return route.fulfill(redirectPage(back.href));
    },
  );

  await context.route(endpoint('token'), (route) => {
    const now = Math.floor(Date.now() / 1000);
    const common = { iss: ISSUER, sub: persona.sub, azp: CLIENT_ID, iat: now, exp: now + 3600 };
    return route.fulfill({
      headers: cors,
      json: {
        token_type: 'Bearer',
        expires_in: 3600,
        refresh_expires_in: 7200,
        scope: 'openid profile email',
        refresh_token: 'e2e-refresh-token',
        access_token: signJwt({
          ...common,
          aud: 'account',
          typ: 'Bearer',
          realm_access: { roles: persona.roles },
        }),
        id_token: signJwt({
          ...common,
          aud: CLIENT_ID,
          typ: 'ID',
          nonce,
          auth_time: now,
          name: persona.name,
          preferred_username: persona.username,
        }),
      },
    });
  });

  await context.route(
    (url) => url.href.startsWith(endpoint('logout')),
    (route) => {
      const request = new URL(route.request().url());
      return route.fulfill(
        redirectPage(request.searchParams.get('post_logout_redirect_uri') ?? `${APP_ORIGIN}/`),
      );
    },
  );
}
