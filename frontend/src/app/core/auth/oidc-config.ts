import { LogLevel, OpenIdConfiguration } from 'angular-auth-oidc-client';
import { AppConfig } from '../config/app-config';

/**
 * Authorization Code + PKCE (S256) no cliente público `orderflow-spa`. Tokens ficam no
 * sessionStorage (padrão da biblioteca) e são renovados com refresh token, sem iframe. O token só
 * é anexado às chamadas para a API.
 */
export function oidcConfig(config: AppConfig, origin: string): OpenIdConfiguration {
  return {
    authority: config.oidc.authority,
    clientId: config.oidc.clientId,
    redirectUrl: `${origin}/`,
    postLogoutRedirectUri: `${origin}/`,
    responseType: 'code',
    scope: 'openid profile email',
    silentRenew: true,
    useRefreshToken: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,
    // O Keycloak emite refresh token sem `offline_access`; pedir esse escopo criaria sessões
    // offline (que sobrevivem ao logout do SSO) sem necessidade.
    disableRefreshTokenOfflineAccessScopeWarning: true,
    // O Keycloak não repete o nonce no id_token renovado.
    ignoreNonceAfterRefresh: true,
    // Os dados do usuário vêm do id_token; sem chamada extra ao userinfo.
    autoUserInfo: false,
    secureRoutes: [`${config.apiUrl}/api/`],
    logLevel: LogLevel.Warn,
  };
}
