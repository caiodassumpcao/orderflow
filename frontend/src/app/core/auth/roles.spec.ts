import { rolesFromAccessToken } from './roles';

function token(payload: unknown): string {
  const json = new TextEncoder().encode(JSON.stringify(payload));
  const base64 = btoa(String.fromCharCode(...json));
  const base64url = base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `header.${base64url}.signature`;
}

describe('rolesFromAccessToken', () => {
  it('returns the known realm roles of the token', () => {
    expect(
      rolesFromAccessToken(token({ realm_access: { roles: ['USER', 'ADMIN', 'offline_access'] } })),
    ).toEqual(['USER', 'ADMIN']);
  });

  it('decodes base64url payloads with non-ASCII claims', () => {
    expect(
      rolesFromAccessToken(token({ name: 'João Ação ✓', realm_access: { roles: ['USER'] } })),
    ).toEqual(['USER']);
  });

  it.each([
    ['no token', null],
    ['an empty token', ''],
    ['a token without payload', 'only-one-part'],
    ['a payload that is not JSON', 'h.bm90IGpzb24.s'],
    ['no realm roles', token({ sub: 'x' })],
    ['roles that are not a list', token({ realm_access: { roles: 'ADMIN' } })],
  ])('returns no roles for %s', (_case, value) => {
    expect(rolesFromAccessToken(value)).toEqual([]);
  });
});
