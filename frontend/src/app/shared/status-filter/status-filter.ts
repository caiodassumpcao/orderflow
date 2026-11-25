import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ORDER_STATUSES, OrderStatus } from '../../core/api/models';
import { ORDER_STATUS_LABELS } from '../order-status/order-status';

/**
 * Filtro por status em botões de alternância (chips): um grupo rotulado, cada botão com
 * `aria-pressed`, alcançável por Tab e acionável por Enter ou Espaço.
 */
@Component({
  selector: 'of-status-filter',
  template: `
    <div class="chips" role="group" [attr.aria-label]="label()">
      <button type="button" [attr.aria-pressed]="value() === null" (click)="changed.emit(null)">
        Todos
      </button>
      @for (status of statuses; track status) {
        <button
          type="button"
          [attr.aria-pressed]="value() === status"
          (click)="changed.emit(status)"
        >
          {{ labels[status] }}
        </button>
      }
    </div>
  `,
  styles: `
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    button {
      min-height: 2.25rem;
      padding: 0 0.875rem;
      border: 1px solid var(--of-border-strong);
      border-radius: 999px;
      background: var(--of-surface);
      color: var(--of-ink-2);
      font: inherit;
      font-size: 0.9rem;
      font-weight: 500;
      cursor: pointer;
    }
    button:hover {
      border-color: var(--of-primary);
    }
    button[aria-pressed='true'] {
      border-color: var(--of-primary);
      background: var(--of-primary-soft);
      color: var(--of-on-primary-soft);
      font-weight: 600;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusFilter {
  readonly value = input<OrderStatus | null>(null);
  readonly label = input('Filtrar por status');
  readonly changed = output<OrderStatus | null>();

  protected readonly statuses = ORDER_STATUSES;
  protected readonly labels = ORDER_STATUS_LABELS;
}
