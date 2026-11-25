import { CurrencyPipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  FormGroupDirective,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { InventoryApi } from '../../../core/api/inventory-api';
import { StockItem } from '../../../core/api/models';
import { ApiError, toApiError } from '../../../core/http/api-error';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';
import { LoadingIndicator } from '../../../shared/loading-indicator/loading-indicator';
import { PagedList } from '../../../shared/paged-list';
import { RestockDialog } from './restock-dialog';

/** Moedas oferecidas no cadastro (a API aceita qualquer código ISO 4217). */
export const CURRENCIES = ['BRL', 'USD', 'EUR'] as const;

@Component({
  selector: 'of-inventory',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
    ErrorAlert,
    LoadingIndicator,
  ],
  templateUrl: './inventory.html',
  styleUrl: './inventory.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Inventory {
  private readonly api = inject(InventoryApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly currencies = CURRENCIES;
  protected readonly stock = new PagedList<StockItem>((cursor) => this.api.list(cursor));

  protected readonly form = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z0-9][A-Z0-9-]{2,31}$/)],
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120), Validators.pattern(/\S/)],
    }),
    currency: new FormControl<string>('BRL', {
      nonNullable: true,
      validators: Validators.required,
    }),
    unitPrice: new FormControl<number | null>(null, {
      validators: [
        Validators.required,
        Validators.min(0.01),
        Validators.max(1_000_000),
        Validators.pattern(/^\d+(\.\d{1,2})?$/),
      ],
    }),
    initialQuantity: new FormControl<number>(0, {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.min(0),
        Validators.max(1_000_000),
        Validators.pattern(/^\d+$/),
      ],
    }),
  });
  /**
   * Para limpar o formulário depois do cadastro: resetForm() também zera o estado "submetido",
   * que reset() mantém (os campos vazios voltariam a aparecer como inválidos).
   */
  private readonly formDirective = viewChild.required(FormGroupDirective);
  protected readonly registering = signal(false);
  protected readonly registerError = signal<ApiError | null>(null);

  constructor() {
    this.stock.reload();
  }

  protected restock(item: StockItem): void {
    this.dialog
      .open<RestockDialog, StockItem, StockItem>(RestockDialog, { data: item })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((updated) => {
        if (updated) {
          this.replace(updated);
          this.snackBar.open(
            `Estoque de ${updated.name} atualizado: ${updated.available} disponíveis.`,
            'OK',
            { duration: 5000 },
          );
        }
      });
  }

  protected register(): void {
    if (this.form.invalid || this.registering()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.registering.set(true);
    this.registerError.set(null);
    this.api
      .register({
        sku: value.sku,
        name: value.name.trim(),
        currency: value.currency,
        unitPrice: Number(value.unitPrice),
        initialQuantity: Number(value.initialQuantity),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.registering.set(false);
          this.formDirective().resetForm();
          this.snackBar.open(`Produto ${created.name} cadastrado.`, 'OK', { duration: 5000 });
          this.stock.reload();
        },
        error: (error: unknown) => {
          this.registerError.set(toApiError(error));
          this.registering.set(false);
        },
      });
  }

  private replace(updated: StockItem): void {
    this.stock.items.update((items) =>
      items.map((item) => (item.sku === updated.sku ? updated : item)),
    );
  }
}
