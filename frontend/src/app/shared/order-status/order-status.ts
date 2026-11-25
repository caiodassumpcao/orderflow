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

export type StatusTone = 'pending' | 'confirmed' | 'shipped' | 'cancelled';

export function statusTone(status: OrderStatus): StatusTone {
  switch (status) {
    case 'CONFIRMED':
      return 'confirmed';
    case 'SHIPPED':
      return 'shipped';
    case 'CANCELLED':
      return 'cancelled';
    default:
      return 'pending';
  }
}

/** Selo de status. A cor e o ponto só reforçam: o texto sempre diz o status (WCAG 1.4.1). */
@Component({
  selector: 'of-order-status',
  template: `<span [class]="classes()"
    ><span class="dot" aria-hidden="true"></span>{{ label() }}</span
  >`,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.2rem 0.625rem;
      border-radius: 999px;
      font-size: 0.85rem;
      font-weight: 600;
      white-space: nowrap;
    }
    .dot {
      width: 0.45rem;
      height: 0.45rem;
      border-radius: 50%;
    }
    .pending {
      background: var(--of-status-pending-bg);
      color: var(--of-status-pending-fg);
      .dot {
        background: var(--of-status-pending-dot);
      }
    }
    .confirmed {
      background: var(--of-status-confirmed-bg);
      color: var(--of-status-confirmed-fg);
      .dot {
        background: var(--of-status-confirmed-dot);
      }
    }
    .shipped {
      background: var(--of-status-shipped-bg);
      color: var(--of-status-shipped-fg);
      .dot {
        background: var(--of-status-shipped-dot);
      }
    }
    .cancelled {
      background: var(--of-status-cancelled-bg);
      color: var(--of-status-cancelled-fg);
      .dot {
        background: var(--of-status-cancelled-dot);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderStatusBadge {
  readonly status = input.required<OrderStatus>();

  protected readonly label = computed(() => ORDER_STATUS_LABELS[this.status()]);
  protected readonly classes = computed(() => `badge ${statusTone(this.status())}`);
}
