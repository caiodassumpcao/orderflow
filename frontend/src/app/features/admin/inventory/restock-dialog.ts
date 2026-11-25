import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { InventoryApi } from '../../../core/api/inventory-api';
import { StockItem } from '../../../core/api/models';
import { ApiError, toApiError } from '../../../core/http/api-error';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';

export const MAX_RESTOCK = 100_000;

/**
 * Reposição com concorrência otimista: envia a versão vista. Se o produto mudou (412), recarrega
 * o produto, mostra as quantidades novas e pede para confirmar de novo.
 */
@Component({
  selector: 'of-restock-dialog',
  imports: [
    CurrencyPipe,
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ErrorAlert,
  ],
  template: `
    <h2 mat-dialog-title>Repor {{ item().name }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content>
        <p>
          {{ item().sku }} · {{ item().unitPrice | currency: item().currency }} · disponível:
          <strong>{{ item().available | number }}</strong> · reservado:
          <strong>{{ item().reserved | number }}</strong>
        </p>
        <mat-form-field class="quantity">
          <mat-label>Unidades a repor</mat-label>
          <input
            matInput
            type="number"
            inputmode="numeric"
            formControlName="quantity"
            min="1"
            [max]="max"
            required
          />
          @if (quantity.invalid) {
            <mat-error>Informe um número inteiro de 1 a {{ max | number }}.</mat-error>
          }
        </mat-form-field>
        <of-error-alert [error]="error()" />
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="cancel()">Cancelar</button>
        <button mat-flat-button type="submit" [disabled]="saving()">
          {{ saving() ? 'Repondo…' : 'Repor' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .quantity {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestockDialog {
  private readonly api = inject(InventoryApi);
  private readonly dialogRef = inject<MatDialogRef<RestockDialog, StockItem>>(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly max = MAX_RESTOCK;
  protected readonly item = signal(inject<StockItem>(MAT_DIALOG_DATA));
  protected readonly saving = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly quantity = new FormControl<number | null>(null, {
    validators: [
      Validators.required,
      Validators.min(1),
      Validators.max(MAX_RESTOCK),
      Validators.pattern(/^\d+$/),
    ],
  });
  protected readonly form = new FormGroup({ quantity: this.quantity });

  protected cancel(): void {
    this.dialogRef.close();
  }

  protected submit(): void {
    if (this.quantity.invalid || this.saving()) {
      this.quantity.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .restock(this.item(), this.quantity.value ?? 0)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => this.dialogRef.close(updated),
        error: (error: unknown) => {
          const apiError = toApiError(error);
          this.error.set(apiError);
          this.saving.set(false);
          if (apiError.problemType === 'stock-item-modified') {
            this.reloadItem();
          }
        },
      });
  }

  private reloadItem(): void {
    this.api
      .get(this.item().sku)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (fresh) => this.item.set(fresh), error: () => undefined });
  }
}
