import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { map, startWith } from 'rxjs';
import { IDEMPOTENCY_KEY_FACTORY } from '../../../core/api/idempotency';
import { InventoryApi } from '../../../core/api/inventory-api';
import { CatalogItem } from '../../../core/api/models';
import { OrdersApi } from '../../../core/api/orders-api';
import { ApiError, toApiError } from '../../../core/http/api-error';
import { ErrorAlert } from '../../../shared/error-alert/error-alert';

/** Limites da API (PlaceOrderRequest). */
export const MAX_LINES = 50;
export const MAX_QUANTITY = 1000;

type Line = FormGroup<{ sku: FormControl<string>; quantity: FormControl<number> }>;

interface PricedLine {
  readonly product: CatalogItem | undefined;
  readonly quantity: number;
  readonly subtotal: number;
}

@Component({
  selector: 'of-order-create',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSnackBarModule,
    ErrorAlert,
  ],
  templateUrl: './order-create.html',
  styleUrl: './order-create.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderCreate {
  private readonly orders = inject(OrdersApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly newKey = inject(IDEMPOTENCY_KEY_FACTORY);

  protected readonly maxLines = MAX_LINES;
  protected readonly maxQuantity = MAX_QUANTITY;

  protected readonly catalog = signal<readonly CatalogItem[]>([]);
  protected readonly catalogLoading = signal(true);
  protected readonly catalogError = signal<ApiError | null>(null);
  private readonly catalogBySku = computed(
    () => new Map(this.catalog().map((product) => [product.sku, product])),
  );

  protected readonly form = new FormGroup({
    lines: new FormArray<Line>([this.newLine()], {
      validators: (lines) => this.validateLines(lines),
    }),
  });

  protected readonly submitting = signal(false);
  protected readonly submitError = signal<ApiError | null>(null);
  protected readonly submitted = signal(false);

  /** Mantida entre tentativas do mesmo conteúdo: um reenvio após falha não duplica o pedido. */
  private idempotencyKey: string | null = null;

  private readonly lineValues = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => this.form.controls.lines.getRawValue()),
    ),
    { requireSync: true },
  );

  protected readonly pricedLines = computed<PricedLine[]>(() =>
    this.lineValues().map(({ sku, quantity }) => {
      const product = this.catalogBySku().get(sku);
      const validQuantity = Number.isInteger(quantity) && quantity > 0 ? quantity : 0;
      return {
        product,
        quantity: validQuantity,
        subtotal: (product?.unitPrice ?? 0) * validQuantity,
      };
    }),
  );

  protected readonly currency = computed(
    () => this.pricedLines().find((line) => line.product)?.product?.currency ?? 'BRL',
  );

  protected readonly total = computed(() =>
    this.pricedLines().reduce((sum, line) => sum + line.subtotal, 0),
  );

  constructor() {
    inject(InventoryApi)
      .catalog()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (items) => {
          this.catalog.set(items);
          this.catalogLoading.set(false);
          this.form.controls.lines.updateValueAndValidity();
        },
        error: (error: unknown) => {
          this.catalogError.set(toApiError(error));
          this.catalogLoading.set(false);
        },
      });
    // Conteúdo novo, chave nova: reutilizar a chave com outro corpo seria recusado (422).
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => (this.idempotencyKey = null));
  }

  protected get lines(): FormArray<Line> {
    return this.form.controls.lines;
  }

  protected addLine(): void {
    if (this.lines.length < MAX_LINES) {
      this.lines.push(this.newLine());
      queueMicrotask(() => this.focus(`#sku-${this.lines.length - 1}`));
    }
  }

  protected removeLine(index: number): void {
    if (this.lines.length > 1) {
      this.lines.removeAt(index);
      queueMicrotask(() => this.focus(`#sku-${Math.min(index, this.lines.length - 1)}`));
    }
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      queueMicrotask(() => this.focus('select.ng-invalid, input.ng-invalid, .form-error'));
      return;
    }
    const lines = this.pricedLines();
    this.idempotencyKey ??= this.newKey();
    this.submitting.set(true);
    this.submitError.set(null);
    this.orders
      .place(
        {
          currency: this.currency(),
          items: lines.map((line) => ({
            sku: line.product?.sku ?? '',
            quantity: line.quantity,
            unitPrice: line.product?.unitPrice ?? 0,
          })),
        },
        this.idempotencyKey,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.snackBar.open('Pedido criado. Acompanhe a reserva de estoque.', 'OK', {
            duration: 6000,
          });
          void this.router.navigate(['/pedidos', order.id], { state: { order } });
        },
        error: (error: unknown) => {
          const apiError = toApiError(error);
          if (apiError.problemType === 'idempotency-key-reused') {
            this.idempotencyKey = null;
          }
          this.submitError.set(apiError);
          this.submitting.set(false);
        },
      });
  }

  private newLine(): Line {
    return new FormGroup({
      sku: new FormControl('', { nonNullable: true, validators: Validators.required }),
      quantity: new FormControl(1, {
        nonNullable: true,
        validators: [
          Validators.required,
          Validators.min(1),
          Validators.max(MAX_QUANTITY),
          Validators.pattern(/^\d+$/),
        ],
      }),
    });
  }

  /** Regras da API que dependem de mais de um item: SKU repetido e moedas diferentes. */
  private validateLines(control: AbstractControl): ValidationErrors | null {
    const skus = (control as FormArray<Line>)
      .getRawValue()
      .map((line) => line.sku)
      .filter(Boolean);
    const errors: ValidationErrors = {};
    if (new Set(skus).size !== skus.length) {
      errors['duplicateProduct'] = true;
    }
    const currencies = new Set(skus.map((sku) => this.catalogBySku().get(sku)?.currency));
    if (currencies.size > 1) {
      errors['mixedCurrencies'] = true;
    }
    return Object.keys(errors).length ? errors : null;
  }

  private focus(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }
}
