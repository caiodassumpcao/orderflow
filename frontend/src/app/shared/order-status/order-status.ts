import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus } from '../../core/api/models';

export const ORDER_STATUS_LABELS: Readonly<Record<OrderStatus, string>> = {
  CREATED: 'Aguardando estoque',
  STOCK_RESERVED: 'Estoque reservado',
  CONFIRMED: 'Confirmado',
  SHIPPED: 'Enviado',
  CANCELLED: 'Cancelado',
};

/** Status em que a saga de estoque ainda não terminou: a tela continua acompanhando. */
export function isAwaitingStock(status: OrderStatus): boolean {
  return status === 'CREATED' || status === 'STOCK_RESERVED';
}

/** Selo de status. A cor só reforça: o texto sempre diz o status (WCAG 1.4.1). */
@Component({
  selector: 'of-order-status',
  template: `<span class="badge" [class]="tone()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.125rem 0.625rem;
      border-radius: 1rem;
      font-size: 0.875rem;
      font-weight: 600;
      white-space: nowrap;
    }
    .pending {
      background: var(--of-status-pending-bg);
      color: var(--of-status-pending-fg);
    }
    .confirmed {
      background: var(--of-status-confirmed-bg);
      color: var(--of-status-confirmed-fg);
    }
    .shipped {
      background: var(--of-status-shipped-bg);
      color: var(--of-status-shipped-fg);
    }
    .cancelled {
      background: var(--of-status-cancelled-bg);
      color: var(--of-status-cancelled-fg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderStatusBadge {
  readonly status = input.required<OrderStatus>();

  protected readonly label = computed(() => ORDER_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'CONFIRMED':
        return 'badge confirmed';
      case 'SHIPPED':
        return 'badge shipped';
      case 'CANCELLED':
        return 'badge cancelled';
      default:
        return 'badge pending';
    }
  });
}
