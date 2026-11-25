import { TestBed } from '@angular/core/testing';
import { AuthenticatedResult, OidcSecurityService, UserDataResult } from 'angular-auth-oidc-client';
import { BehaviorSubject, firstValueFrom, of } from 'rxjs';
import { AuthService } from './auth.service';

function accessToken(roles: string[]): string {
  const payload = btoa(JSON.stringify({ realm_access: { roles } }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `h.${payload}.s`;
}

describe('AuthService', () => {
  const authenticated$ = new BehaviorSubject<AuthenticatedResult>({
    isAuthenticated: false,
    allConfigsAuthenticated: [],
  });
  const userData$ = new BehaviorSubject<UserDataResult>({ userData: null, allUserData: [] });
  let token = '';
  const oidc = {
    isAuthenticated$: authenticated$,
    userData$,
    getAccessToken: jest.fn(() => of(token)),
    authorize: jest.fn(),
    logoff: jest.fn(() => of(null)),
  };
  let auth: AuthService;

  beforeEach(() => {
    token = '';
    authenticated$.next({ isAuthenticated: false, allConfigsAuthenticated: [] });
    userData$.next({ userData: null, allUserData: [] });
    TestBed.configureTestingModule({
      providers: [{ provide: OidcSecurityService, useValue: oidc }],
    });
    auth = TestBed.inject(AuthService);
  });

  it('starts signed out without roles', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.isCustomer()).toBe(false);
    expect(auth.isAdmin()).toBe(false);
    expect(auth.displayName()).toBe('');
  });

  it('exposes the session, name and roles after login', () => {
    token = accessToken(['USER', 'ADMIN']);
    userData$.next({
      userData: { name: ' Dev Admin ', preferred_username: 'dev-admin' },
      allUserData: [],
    });
    authenticated$.next({ isAuthenticated: true, allConfigsAuthenticated: [] });

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.displayName()).toBe('Dev Admin');
    expect(auth.isCustomer()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
  });

  it('falls back to the username when the token has no name', () => {
    userData$.next({ userData: { preferred_username: 'dev-user' }, allUserData: [] });

    expect(auth.displayName()).toBe('dev-user');
  });

  it('answers role checks from the current token', async () => {
    token = accessToken(['USER']);

    await expect(firstValueFrom(auth.hasRole('USER'))).resolves.toBe(true);
    await expect(firstValueFrom(auth.hasRole('ADMIN'))).resolves.toBe(false);
  });

  it('starts the login and the logout with the identity provider', () => {
    auth.login();
    auth.logout();

    expect(oidc.authorize).toHaveBeenCalled();
    expect(oidc.logoff).toHaveBeenCalled();
  });
});
