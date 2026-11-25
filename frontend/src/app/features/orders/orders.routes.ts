import { Routes } from '@angular/router';
import { FORM_FIELD_DEFAULTS } from '../../shared/form-field-defaults';

export const ORDER_ROUTES: Routes = [
  {
    path: '',
    providers: [FORM_FIELD_DEFAULTS],
    children: [
      {
        path: '',
        title: 'Meus pedidos',
        loadComponent: () => import('./order-list/order-list').then((m) => m.OrderList),
      },
      {
        path: 'novo',
        title: 'Novo pedido',
        loadComponent: () => import('./order-create/order-create').then((m) => m.OrderCreate),
      },
      {
        path: ':id',
        title: 'Detalhe do pedido',
        loadComponent: () => import('./order-detail/order-detail').then((m) => m.OrderDetail),
      },
    ],
  },
];
