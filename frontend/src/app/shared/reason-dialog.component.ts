import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ReasonDialogData {
  title: string;
  message?: string;
  label?: string;
  required?: boolean;
  confirmText?: string;
  danger?: boolean;
  initial?: string;
}

/** Confirmação com texto (motivo, pergunta, comentário). Retorna o texto ou undefined se cancelado. */
@Component({
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.message) { <p class="muted">{{ data.message }}</p> }
      <mat-form-field appearance="outline" class="full">
        <mat-label>{{ data.label || 'Motivo' }}</mat-label>
        <textarea matInput [formControl]="text" rows="4" cdkFocusInitial></textarea>
        @if (text.hasError('required')) { <mat-error>Campo obrigatório.</mat-error> }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button [color]="data.danger ? 'warn' : 'primary'" (click)="confirm()">{{ data.confirmText || 'Confirmar' }}</button>
    </mat-dialog-actions>
  `
})
export class ReasonDialogComponent {
  data = inject<ReasonDialogData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ReasonDialogComponent, string>);
  text = new FormControl(this.data.initial ?? '', this.data.required ? [Validators.required, Validators.maxLength(4000)] : [Validators.maxLength(4000)]);

  confirm(): void {
    this.text.markAsTouched();
    if (this.text.invalid) return;
    this.ref.close((this.text.value ?? '').trim());
  }
}
