import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Order } from '../../core/api/models';
import { describeCancellation } from '../cancellation-reason';
import { OrderStatusBadge } from '../order-status/order-status';

/** Dados do pedido e itens; usado no detalhe do cliente e no da operação. */
@Component({
  selector: 'of-order-details',
  imports: [CurrencyPipe, DatePipe, OrderStatusBadge],
  templateUrl: './order-details.html',
  styleUrl: './order-details.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetails {
  readonly order = input.required<Order>();

  protected readonly cancellation = computed(() =>
    describeCancellation(this.order().cancellationReason),
  );
}
