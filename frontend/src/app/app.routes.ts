import { Routes } from '@angular/router';
import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';
import { FORBIDDEN_PATH, roleGuard } from './core/auth/role.guard';
import { Role } from './core/auth/roles';

export const routes: Routes = [
  {
    path: '',
    title: 'Início',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    // Sem login, o guard guarda a rota e leva ao Keycloak; na volta, retoma a rota.
    path: 'pedidos',
    canActivate: [autoLoginPartialRoutesGuard, roleGuard(Role.User)],
    loadChildren: () => import('./features/orders/orders.routes').then((m) => m.ORDER_ROUTES),
  },
  {
    path: 'admin',
    canActivate: [autoLoginPartialRoutesGuard, roleGuard(Role.Admin)],
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: FORBIDDEN_PATH.slice(1),
    title: 'Acesso negado',
    loadComponent: () => import('./features/forbidden/forbidden').then((m) => m.Forbidden),
  },
  { path: '**', redirectTo: '' },
];
