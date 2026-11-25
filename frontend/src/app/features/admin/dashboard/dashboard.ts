import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../../core/api/dashboard-api';
import { InventoryApi } from '../../../core/api/inventory-api';
import { DashboardSummary, ORDER_STATUSES, StockItem } from '../../../core/api/models';
import { ApiError, toApiError } from '../../../core/http/api-error';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { ORDER_STATUS_LABELS, statusTone } from '../../../shared/order-status/order-status';
import { LOW_STOCK_THRESHOLD } from '../../../shared/stock';

export const DASHBOARD_PERIODS = [7, 30] as const;
export const DASHBOARD_DAYS = DASHBOARD_PERIODS[0];
const LOW_STOCK_SHOWN = 5;

/** Fuso do navegador; a série diária usa os dias locais de quem está olhando. */
export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

@Component({
  selector: 'of-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorAlert,
    LoadingIndicator,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly api = inject(DashboardApi);
  private readonly inventory = inject(InventoryApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly periods = DASHBOARD_PERIODS;
  protected readonly lowThreshold = LOW_STOCK_THRESHOLD;
  protected readonly days = signal<number>(DASHBOARD_DAYS);
  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly lowStock = signal<readonly StockItem[] | null>(null);

  protected readonly periodOrders = computed(() =>
    (this.summary()?.daily ?? []).reduce((sum, day) => sum + day.orders, 0),
  );

  protected readonly awaitingStock = computed(() => {
    const byStatus = this.summary()?.ordersByStatus;
    return byStatus ? (byStatus.CREATED ?? 0) + (byStatus.STOCK_RESERVED ?? 0) : 0;
  });

  /** Distribuição por status: largura proporcional na barra; o número sempre aparece em texto. */
  protected readonly statusRows = computed(() => {
    const summary = this.summary();
    if (!summary) {
      return [];
    }
    return ORDER_STATUSES.map((status) => ({
      status,
      label: ORDER_STATUS_LABELS[status],
      tone: statusTone(status),
      count: summary.ordersByStatus[status] ?? 0,
    }));
  });

  /** Barras proporcionais ao maior dia, com a parte cancelada empilhada por baixo. */
  protected readonly dailyBars = computed(() => {
    const daily = this.summary()?.daily ?? [];
    const peak = Math.max(1, ...daily.map((day) => day.orders));
    const labelEvery = daily.length > 10 ? 5 : 1;
    return daily.map((day, index) => ({
      ...day,
      height: Math.round((day.orders / peak) * 100),
      cancelledShare: day.orders ? Math.round((day.cancelled / day.orders) * 100) : 0,
      today: index === daily.length - 1,
      showLabel: index === daily.length - 1 || index % labelEvery === 0,
    }));
  });

  constructor() {
    this.refresh();
  }

  protected changePeriod(value: string): void {
    this.days.set(Number(value));
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .summary(this.days(), browserTimeZone())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.error.set(toApiError(error));
          this.loading.set(false);
        },
      });
    // Estoque baixo entre os produtos da primeira página (100, em ordem de SKU).
    this.inventory
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) =>
          this.lowStock.set(
            page.items
              .filter((item) => item.available <= LOW_STOCK_THRESHOLD)
              .sort((a, b) => a.available - b.available)
              .slice(0, LOW_STOCK_SHOWN),
          ),
        error: () => this.lowStock.set(null),
      });
  }
}
