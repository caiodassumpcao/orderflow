import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { distinctUntilChanged, map, of, switchMap, timer } from 'rxjs';

/** Esperas mais curtas que isso não mostram indicador (evita o "pisca"). */
export const SHOW_DELAY_MS = 200;
/** Depois de aparecer, o indicador fica pelo menos esse tempo (evita sumir num piscar). */
export const MIN_VISIBLE_MS = 400;

/**
 * Indicador de carregamento estável:
 * - ocupa sempre o mesmo espaço, então o conteúdo não pula quando ele aparece ou some;
 * - só aparece se a espera passar de {@link SHOW_DELAY_MS} e fica no mínimo {@link MIN_VISIBLE_MS};
 * - com movimento reduzido (`prefers-reduced-motion`), troca a barra animada por um texto estático.
 */
@Component({
  selector: 'of-loading-indicator',
  imports: [MatProgressBarModule],
  template: `
    <div class="slot">
      @if (visible()) {
        <mat-progress-bar class="bar" mode="indeterminate" [attr.aria-label]="label()" />
        <p class="text">{{ label() }}…</p>
      }
    </div>
  `,
  styles: `
    .slot {
      display: flex;
      align-items: center;
      height: 1.25rem;
      margin: 0.25rem 0;
    }
    .bar {
      width: 100%;
    }
    .text {
      display: none;
      margin: 0;
      font-size: 0.875rem;
      color: var(--of-muted);
    }
    @media (prefers-reduced-motion: reduce) {
      .bar {
        display: none;
      }
      .text {
        display: block;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingIndicator {
  readonly active = input.required<boolean>();
  /** Descreve o que está carregando, ex.: "Carregando pedidos". */
  readonly label = input.required<string>();

  private shownAt = 0;

  protected readonly visible = toSignal(
    toObservable(this.active).pipe(
      distinctUntilChanged(),
      switchMap((active) => {
        if (active) {
          return timer(SHOW_DELAY_MS).pipe(
            map(() => {
              this.shownAt ||= Date.now();
              return true;
            }),
          );
        }
        if (!this.shownAt) {
          return of(false);
        }
        const remaining = Math.max(0, MIN_VISIBLE_MS - (Date.now() - this.shownAt));
        return timer(remaining).pipe(
          map(() => {
            this.shownAt = 0;
            return false;
          }),
        );
      }),
    ),
    { initialValue: false },
  );
}
