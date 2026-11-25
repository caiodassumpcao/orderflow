import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Order } from '../../core/api/models';
import { describeCancellation } from '../cancellation-reason';
import { OrderTimeline } from '../order-timeline/order-timeline';

/**
 * Andamento, itens e resumo do pedido; usado no detalhe do cliente e no da operação. O conteúdo
 * projetado aparece no fim do resumo (ações da página, como cancelar ou enviar).
 */
@Component({
  selector: 'of-order-details',
  imports: [CurrencyPipe, DatePipe, OrderTimeline],
  templateUrl: './order-details.html',
  styleUrl: './order-details.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetails {
  readonly order = input.required<Order>();

  protected readonly cancellation = computed(() =>
    describeCancellation(this.order().cancellationReason),
  );
  protected readonly unitCount = computed(() =>
    this.order().items.reduce((sum, item) => sum + item.quantity, 0),
  );
}
