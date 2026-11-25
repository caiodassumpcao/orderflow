import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';
import { Role } from './roles';

export const FORBIDDEN_PATH = '/acesso-negado';

/**
 * Exige um papel do realm. Usado depois do `autoLoginPartialRoutesGuard` (que garante o login);
 * sem o papel, leva à página de acesso negado em vez de mostrar uma tela que a API recusaria.
 */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const router = inject(Router);
    return inject(AuthService)
      .hasRole(role)
      .pipe(map((allowed) => allowed || router.parseUrl(FORBIDDEN_PATH)));
  };
}
