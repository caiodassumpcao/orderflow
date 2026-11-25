import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmDialogData {
  readonly title: string;
  readonly message: string;
  readonly confirmLabel: string;
  /** Se presente, pede um texto opcional (ex.: motivo do cancelamento). */
  readonly noteLabel?: string;
  readonly noteMaxLength?: number;
}

/** Resultado: `undefined` se o usuário desistiu; senão o texto digitado (pode ser vazio). */
export type ConfirmDialogResult = string | undefined;

/**
 * Confirmação de ações que não podem ser desfeitas. O diálogo do Material prende o foco, fecha com
 * Esc e devolve o foco ao botão que o abriu.
 */
@Component({
  selector: 'of-confirm-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      @if (data.noteLabel) {
        <mat-form-field class="note">
          <mat-label>{{ data.noteLabel }}</mat-label>
          <textarea matInput [formControl]="note" rows="3" [maxlength]="maxLength"></textarea>
          <mat-hint align="end">{{ note.value.length }} / {{ maxLength }}</mat-hint>
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="back()">Voltar</button>
      <button mat-flat-button type="button" [disabled]="note.invalid" (click)="confirm()">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .note {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<ConfirmDialog, ConfirmDialogResult>>(MatDialogRef);

  protected readonly maxLength = this.data.noteMaxLength ?? 200;
  protected readonly note = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(this.maxLength)],
  });

  /** Fecha sem resultado: quem abriu entende como desistência. */
  protected back(): void {
    this.dialogRef.close();
  }

  protected confirm(): void {
    this.dialogRef.close(this.note.value.trim());
  }
}
