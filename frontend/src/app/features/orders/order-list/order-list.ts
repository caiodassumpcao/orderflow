import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { ORDER_STATUSES, OrderStatus, OrderSummary } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { ORDER_STATUS_LABELS, OrderStatusBadge } from '../../../shared/order-status/order-status';
import { PagedList } from '../../../shared/paged-list';

@Component({
  selector: 'of-order-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorAlert,
    LoadingIndicator,
    OrderStatusBadge,
  ],
  templateUrl: './order-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderList {
  private readonly api = inject(OrdersApi);

  protected readonly statuses = ORDER_STATUSES;
  protected readonly statusLabels = ORDER_STATUS_LABELS;
  protected readonly status = signal<OrderStatus | null>(null);

  protected readonly orders = new PagedList<OrderSummary>((cursor) =>
    this.api.list({ status: this.status(), cursor }),
  );

  constructor() {
    this.orders.reload();
  }

  protected filterBy(value: string): void {
    this.status.set((value || null) as OrderStatus | null);
    this.orders.reload();
  }
}
