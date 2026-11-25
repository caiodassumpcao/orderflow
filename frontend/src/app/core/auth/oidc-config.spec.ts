import { TEST_CONFIG } from '../../../testing/test-support';
import { oidcConfig } from './oidc-config';

describe('oidcConfig', () => {
  const config = oidcConfig(TEST_CONFIG, 'https://app.test');

  it('uses the authorization code flow with refresh tokens', () => {
    expect(config).toEqual(
      expect.objectContaining({
        authority: TEST_CONFIG.oidc.authority,
        clientId: 'orderflow-spa',
        responseType: 'code',
        useRefreshToken: true,
        silentRenew: true,
      }),
    );
  });

  it('returns to the app root after login and logout', () => {
    expect(config.redirectUrl).toBe('https://app.test/');
    expect(config.postLogoutRedirectUri).toBe('https://app.test/');
  });

  it('attaches the token only to API calls', () => {
    expect(config.secureRoutes).toEqual(['http://api.test/api/']);
  });
});
