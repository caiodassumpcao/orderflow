import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Order } from '../../core/api/models';

type StepState = 'done' | 'current' | 'todo' | 'cancelled';

interface Step {
  readonly label: string;
  readonly state: StepState;
  readonly at: string | null;
  readonly hint: string;
}

const FLOW = ['CREATED', 'STOCK_RESERVED', 'CONFIRMED', 'SHIPPED'] as const;
const LABELS = ['Pedido criado', 'Estoque reservado', 'Confirmado', 'Enviado'];
const WAITING = ['', 'Aguardando o estoque', 'Aguardando o estoque', 'Aguardando a operação'];

/**
 * Andamento do pedido na saga: criado → estoque reservado → confirmado → enviado. Um pedido
 * cancelado mostra até onde chegou e o passo de cancelamento. As datas conhecidas são a de criação
 * e a da última mudança; os passos intermediários não guardam horário próprio.
 */
@Component({
  selector: 'of-order-timeline',
  imports: [DatePipe],
  templateUrl: './order-timeline.html',
  styleUrl: './order-timeline.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderTimeline {
  readonly order = input.required<Order>();

  protected readonly steps = computed<Step[]>(() => {
    const order = this.order();
    if (order.status === 'CANCELLED') {
      return [
        { label: LABELS[0], state: 'done', at: order.createdAt, hint: '' },
        { label: 'Cancelado', state: 'cancelled', at: order.updatedAt, hint: '' },
      ];
    }
    const reached = FLOW.indexOf(order.status);
    return FLOW.map((_, index) => ({
      label: LABELS[index],
      state: index < reached ? 'done' : index === reached ? 'current' : 'todo',
      at: index === 0 ? order.createdAt : index === reached ? order.updatedAt : null,
      hint: index > reached ? WAITING[index] : '',
    }));
  });
}
