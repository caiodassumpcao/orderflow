import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { EventTypes, OidcSecurityService, PublicEventsService } from 'angular-auth-oidc-client';
import { Observable, filter, map, switchMap } from 'rxjs';
import { Role, rolesFromAccessToken } from './roles';

/** Claims do id_token usados na interface. */
interface IdTokenClaims {
  readonly name?: string;
  readonly preferred_username?: string;
}

/** Estado de login para a interface, em signals, sobre a biblioteca OIDC. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);

  readonly isAuthenticated = toSignal(
    this.oidc.isAuthenticated$.pipe(map((result) => result.isAuthenticated)),
    { initialValue: false },
  );

  private readonly claims = toSignal(
    this.oidc.userData$.pipe(map((result) => (result.userData ?? null) as IdTokenClaims | null)),
    { initialValue: null },
  );

  private readonly roles = toSignal(
    this.oidc.isAuthenticated$.pipe(
      switchMap(() => this.oidc.getAccessToken()),
      map(rolesFromAccessToken),
    ),
    { initialValue: [] as Role[] },
  );

  readonly displayName = computed(() => {
    const claims = this.claims();
    return claims?.name?.trim() || claims?.preferred_username || '';
  });

  readonly isCustomer = computed(() => this.roles().includes(Role.User));
  readonly isAdmin = computed(() => this.roles().includes(Role.Admin));

  private readonly unavailable = signal(false);
  /**
   * A descoberta OIDC falhou (provedor de login fora do ar). Acontece ao entrar ou ao processar o
   * retorno do login; volta a falso a cada nova tentativa. Se os endpoints já estavam em cache, o
   * navegador segue para o provedor e o erro aparece na página dele.
   */
  readonly loginUnavailable = this.unavailable.asReadonly();

  constructor() {
    inject(PublicEventsService)
      .registerForEvents()
      .pipe(
        filter((event) => event.type === EventTypes.ConfigLoadingFailed),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.unavailable.set(true));
  }

  /** Para guards: lê o token atual do storage, sem depender do estado dos signals. */
  hasRole(role: Role): Observable<boolean> {
    return this.oidc
      .getAccessToken()
      .pipe(map((token) => rolesFromAccessToken(token).includes(role)));
  }

  login(): void {
    this.unavailable.set(false);
    this.oidc.authorize();
  }

  /** Encerra a sessão no Keycloak (end_session) e volta para a página inicial. */
  logout(): void {
    this.oidc.logoff().subscribe();
  }
}
