import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Subscription, filter, switchMap } from 'rxjs';
import { Order } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ApiError, toApiError } from '../../../core/http/api-error';
import {
  ConfirmDialog,
  ConfirmDialogData,
  ConfirmDialogResult,
} from '../../../shared/confirm-dialog/confirm-dialog';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { OrderDetails } from '../../../shared/order-details/order-details';
import { ORDER_POLL_INTERVAL_MS, pollOrder } from '../../../shared/order-poller';
import { ORDER_STATUS_LABELS, isAwaitingStock } from '../../../shared/order-status/order-status';

@Component({
  selector: 'of-order-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatSnackBarModule,
    ErrorAlert,
    OrderDetails,
  ],
  templateUrl: './order-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetail implements OnInit {
  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  private readonly api = inject(OrdersApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pollInterval = inject(ORDER_POLL_INTERVAL_MS);

  /** Vindo da criação (estado da navegação): aparece na hora, antes da projeção. */
  protected readonly order = signal<Order | null>(initialOrder());
  protected readonly loadError = signal<ApiError | null>(null);
  protected readonly actionError = signal<ApiError | null>(null);
  protected readonly polling = signal(false);
  protected readonly cancelling = signal(false);

  protected readonly awaitingStock = computed(() => {
    const order = this.order();
    return order !== null && isAwaitingStock(order.status);
  });
  protected readonly statusMessage = computed(() => {
    const order = this.order();
    if (!order) {
      return '';
    }
    return isAwaitingStock(order.status)
      ? 'Aguardando a reserva de estoque…'
      : `Status: ${ORDER_STATUS_LABELS[order.status]}.`;
  });

  private polling$?: Subscription;

  ngOnInit(): void {
    const initial = this.order();
    if (initial && initial.id !== this.id()) {
      this.order.set(null);
    }
    this.refresh();
  }

  protected refresh(): void {
    this.polling$?.unsubscribe();
    this.polling.set(true);
    this.loadError.set(null);
    this.polling$ = pollOrder(
      () => this.api.get(this.id()),
      (order) => order,
      this.pollInterval,
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if ('value' in result) {
            this.order.set(result.value);
            this.loadError.set(null);
          } else if (result.error.status !== 404 || !this.order()) {
            // 404 com o pedido já em mãos é só a projeção atrasada: continua mostrando o que tem.
            this.loadError.set(result.error);
          }
        },
        complete: () => this.polling.set(false),
      });
  }

  protected cancel(): void {
    const data: ConfirmDialogData = {
      title: 'Cancelar pedido?',
      message: 'O estoque reservado volta ao catálogo. Esta ação não pode ser desfeita.',
      confirmLabel: 'Cancelar pedido',
      noteLabel: 'Motivo (opcional)',
      noteMaxLength: 200,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, ConfirmDialogResult>(ConfirmDialog, { data })
      .afterClosed()
      .pipe(
        filter((reason): reason is string => reason !== undefined),
        switchMap((reason) => {
          this.cancelling.set(true);
          this.actionError.set(null);
          return this.api.cancel(this.id(), reason);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => {
          this.polling$?.unsubscribe();
          this.polling.set(false);
          this.order.set(order);
          this.cancelling.set(false);
          this.snackBar.open('Pedido cancelado.', 'OK', { duration: 5000 });
        },
        error: (error: unknown) => {
          this.actionError.set(toApiError(error));
          this.cancelling.set(false);
          this.refresh();
        },
      });
  }
}

function initialOrder(): Order | null {
  const state: unknown = history.state;
  const order = (state as { order?: Order } | null)?.order;
  return order && typeof order.id === 'string' ? order : null;
}
