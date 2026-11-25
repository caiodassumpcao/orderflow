import { InvalidAppConfigError, loadAppConfig, parseAppConfig } from './app-config';

const valid = {
  apiUrl: 'http://localhost:8080/',
  oidc: { authority: 'http://localhost:8180/realms/orderflow/', clientId: ' orderflow-spa ' },
};

describe('parseAppConfig', () => {
  it('normalizes URLs without a trailing slash and trims the client id', () => {
    expect(parseAppConfig(valid)).toEqual({
      apiUrl: 'http://localhost:8080',
      oidc: { authority: 'http://localhost:8180/realms/orderflow', clientId: 'orderflow-spa' },
    });
  });

  it.each([
    ['not an object', null, 'objeto'],
    ['missing api url', { ...valid, apiUrl: '' }, 'apiUrl'],
    ['malformed api url', { ...valid, apiUrl: 'not a url' }, 'apiUrl'],
    ['non-http scheme', { ...valid, apiUrl: 'javascript:alert(1)' }, 'http'],
    ['missing authority', { ...valid, oidc: { clientId: 'x' } }, 'oidc.authority'],
    ['missing client id', { ...valid, oidc: { authority: 'http://idp' } }, 'oidc.clientId'],
  ])('rejects a config with %s', (_case, raw, field) => {
    expect(() => parseAppConfig(raw)).toThrow(InvalidAppConfigError);
    expect(() => parseAppConfig(raw)).toThrow(field);
  });
});

describe('loadAppConfig', () => {
  it('fetches /config.json without cache and parses it', async () => {
    const fetchFn = jest.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(valid) });

    const config = await loadAppConfig(fetchFn);

    expect(fetchFn).toHaveBeenCalledWith('/config.json', { cache: 'no-store' });
    expect(config.apiUrl).toBe('http://localhost:8080');
  });

  it('fails with the HTTP status when the file is missing', async () => {
    const fetchFn = jest.fn().mockResolvedValue({ ok: false, status: 404 });

    await expect(loadAppConfig(fetchFn)).rejects.toThrow('404');
  });
});
