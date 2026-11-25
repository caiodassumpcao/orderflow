import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ApiError } from '../../core/http/api-error';

/** Mensagem de erro anunciada por leitores de tela, com o código para suporte quando houver. */
@Component({
  selector: 'of-error-alert',
  template: `
    @if (error(); as error) {
      <div class="alert" role="alert">
        <p>{{ error.message }}</p>
        @if (error.correlationId && error.status >= 500) {
          <p class="support">Código para suporte: {{ error.correlationId }}</p>
        }
      </div>
    }
  `,
  styles: `
    .alert {
      margin: 1rem 0;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--of-status-cancelled-fg);
      background: var(--of-status-cancelled-bg);
      color: var(--of-status-cancelled-fg);
      border-radius: 0.25rem;
    }
    p {
      margin: 0;
    }
    .support {
      margin-top: 0.25rem;
      font-size: 0.875rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorAlert {
  readonly error = input<ApiError | null>(null);
}
