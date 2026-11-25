import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { AdminOrder } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ApiError, toApiError } from '../../../core/http/api-error';
import {
  ConfirmDialog,
  ConfirmDialogData,
  ConfirmDialogResult,
} from '../../../shared/confirm-dialog/confirm-dialog';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { OrderDetails } from '../../../shared/order-details/order-details';
import { OrderStatusBadge } from '../../../shared/order-status/order-status';

@Component({
  selector: 'of-admin-order-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatSnackBarModule,
    ErrorAlert,
    LoadingIndicator,
    OrderDetails,
    OrderStatusBadge,
  ],
  templateUrl: './admin-order-detail.html',
  styles: `
    .title-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem 0.875rem;
    }
    h1 {
      font-variant-numeric: tabular-nums;
    }
    .customer {
      overflow-wrap: anywhere;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminOrderDetail implements OnInit {
  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  private readonly api = inject(OrdersApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly detail = signal<AdminOrder | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<ApiError | null>(null);
  protected readonly actionError = signal<ApiError | null>(null);
  protected readonly shipping = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .getAny(this.id())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.detail.set(detail);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loadError.set(toApiError(error));
          this.loading.set(false);
        },
      });
  }

  protected ship(): void {
    const data: ConfirmDialogData = {
      title: 'Marcar como enviado?',
      message: 'O estoque reservado sai do inventário. Esta ação não pode ser desfeita.',
      confirmLabel: 'Marcar como enviado',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, ConfirmDialogResult>(ConfirmDialog, { data })
      .afterClosed()
      .pipe(
        filter((result) => result !== undefined),
        switchMap(() => {
          this.shipping.set(true);
          this.actionError.set(null);
          return this.api.ship(this.id());
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => {
          this.detail.update((detail) => (detail ? { ...detail, order } : detail));
          this.shipping.set(false);
          this.snackBar.open('Pedido marcado como enviado.', 'OK', { duration: 5000 });
        },
        error: (error: unknown) => {
          this.actionError.set(toApiError(error));
          this.shipping.set(false);
        },
      });
  }
}
