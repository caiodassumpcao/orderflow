import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AdminOrderSummary, OrderStatus } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { OrderStatusBadge } from '../../../shared/order-status/order-status';
import { PagedList } from '../../../shared/paged-list';
import { StatusFilter } from '../../../shared/status-filter/status-filter';

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
    StatusFilter,
  ],
  templateUrl: './admin-order-list.html',
  styleUrl: './admin-order-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminOrderList {
  private readonly api = inject(OrdersApi);

  protected readonly status = signal<OrderStatus | null>(null);
  protected readonly customerId = new FormControl('', {
    nonNullable: true,
    validators: Validators.pattern(CUSTOMER_ID_PATTERN),
  });
  protected readonly searchForm = new FormGroup({ customerId: this.customerId });
  private appliedCustomer = '';

  protected readonly orders = new PagedList<AdminOrderSummary>((cursor) =>
    this.api.listAll({
      status: this.status(),
      customerId: this.appliedCustomer || null,
      cursor,
    }),
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

  protected searchCustomer(): void {
    // Um ID colado com espaços em volta continua válido.
    this.customerId.setValue(this.customerId.value.trim());
    if (this.customerId.invalid) {
      this.customerId.markAsTouched();
      return;
    }
    this.appliedCustomer = this.customerId.value.trim();
    this.orders.reload();
  }

  protected clearCustomer(): void {
    this.customerId.reset();
    this.searchCustomer();
  }
}
