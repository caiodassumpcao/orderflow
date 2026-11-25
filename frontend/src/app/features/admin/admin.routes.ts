import { Routes } from '@angular/router';
import { FORM_FIELD_DEFAULTS } from '../../shared/form-field-defaults';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    providers: [FORM_FIELD_DEFAULTS],
    children: [
      {
        path: '',
        title: 'Painel da operação',
        loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'pedidos',
        title: 'Todos os pedidos',
        loadComponent: () =>
          import('./admin-order-list/admin-order-list').then((m) => m.AdminOrderList),
      },
      {
        path: 'pedidos/:id',
        title: 'Pedido (operação)',
        loadComponent: () =>
          import('./admin-order-detail/admin-order-detail').then((m) => m.AdminOrderDetail),
      },
      {
        path: 'estoque',
        title: 'Estoque',
        loadComponent: () => import('./inventory/inventory').then((m) => m.Inventory),
      },
    ],
  },
];
