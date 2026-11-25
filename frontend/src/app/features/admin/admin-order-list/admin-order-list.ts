import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AdminOrderSummary, ORDER_STATUSES, OrderStatus } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { ORDER_STATUS_LABELS, OrderStatusBadge } from '../../../shared/order-status/order-status';
import { PagedList } from '../../../shared/paged-list';

/** Mesmo formato aceito pela API para o cliente (sub do token). */
const CUSTOMER_ID_PATTERN = /^[A-Za-z0-9._:@-]{1,64}$/;

@Component({
  selector: 'of-admin-order-list',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorAlert,
    LoadingIndicator,
    OrderStatusBadge,
  ],
  templateUrl: './admin-order-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminOrderList {
  private readonly api = inject(OrdersApi);

  protected readonly statuses = ORDER_STATUSES;
  protected readonly statusLabels = ORDER_STATUS_LABELS;

  protected readonly filters = new FormGroup({
    status: new FormControl<OrderStatus | ''>('', { nonNullable: true }),
    customerId: new FormControl('', {
      nonNullable: true,
      validators: Validators.pattern(CUSTOMER_ID_PATTERN),
    }),
  });

  private applied = this.filters.getRawValue();

  protected readonly orders = new PagedList<AdminOrderSummary>((cursor) =>
    this.api.listAll({
      status: this.applied.status || null,
      customerId: this.applied.customerId || null,
      cursor,
    }),
  );

  constructor() {
    this.orders.reload();
  }

  protected applyFilters(): void {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    this.applied = this.filters.getRawValue();
    this.orders.reload();
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.applyFilters();
  }
}
