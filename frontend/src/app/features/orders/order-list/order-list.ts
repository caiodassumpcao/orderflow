import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { OrderStatus, OrderSummary } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { OrderStatusBadge } from '../../../shared/order-status/order-status';
import { PagedList } from '../../../shared/paged-list';
import { StatusFilter } from '../../../shared/status-filter/status-filter';

@Component({
  selector: 'of-order-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    ErrorAlert,
    LoadingIndicator,
    OrderStatusBadge,
    StatusFilter,
  ],
  templateUrl: './order-list.html',
  styles: `
    .empty {
      margin: 0;
    }
    .note {
      margin: 0;
      font-size: 0.875rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderList {
  private readonly api = inject(OrdersApi);

  protected readonly status = signal<OrderStatus | null>(null);

  protected readonly orders = new PagedList<OrderSummary>((cursor) =>
    this.api.list({ status: this.status(), cursor }),
  );

  constructor() {
    this.orders.reload();
  }

  protected filterBy(status: OrderStatus | null): void {
    if (status !== this.status()) {
      this.status.set(status);
      this.orders.reload();
    }
  }
}
