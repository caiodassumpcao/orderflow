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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../../core/api/dashboard-api';
import { DashboardSummary, ORDER_STATUSES } from '../../../core/api/models';
import { ApiError, toApiError } from '../../../core/http/api-error';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { ORDER_STATUS_LABELS } from '../../../shared/order-status/order-status';

export const DASHBOARD_DAYS = 7;

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
    MatProgressBarModule,
    ErrorAlert,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly api = inject(DashboardApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly days = DASHBOARD_DAYS;
  protected readonly statusLabels = ORDER_STATUS_LABELS;
  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly statusRows = computed(() => {
    const summary = this.summary();
    return summary
      ? ORDER_STATUSES.map((status) => ({ status, count: summary.ordersByStatus[status] ?? 0 }))
      : [];
  });

  protected readonly awaitingStock = computed(() => {
    const byStatus = this.summary()?.ordersByStatus;
    return byStatus ? (byStatus.CREATED ?? 0) + (byStatus.STOCK_RESERVED ?? 0) : 0;
  });

  /** Barras proporcionais ao maior dia; o número sempre aparece em texto ao lado. */
  protected readonly dailyRows = computed(() => {
    const daily = this.summary()?.daily ?? [];
    const peak = Math.max(1, ...daily.map((day) => day.orders));
    return daily.map((day) => ({ ...day, percent: Math.round((day.orders / peak) * 100) }));
  });

  constructor() {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .summary(DASHBOARD_DAYS, browserTimeZone())
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
  }
}
