import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Catalog, DemandDetail, UserRef } from '../../../core/models';

export interface ClassificationData { detail: DemandDetail; catalog: Catalog; users: UserRef[]; }
export interface ClassificationResult { fields: Record<string, string | null>; reason: string; ownerId?: string | null; ownerChanged: boolean; }

/** Classificação pelo PMO: projeto, tipo, prioridade e responsável — com motivo obrigatório para auditoria. */
@Component({
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>Classificar demanda</h2>
    <mat-dialog-content [formGroup]="form">
      <p class="small muted">Alterações ficam registradas na auditoria com valor anterior, novo valor e motivo. A troca de tipo pode mudar o workflow.</p>
      <mat-form-field><mat-label>Projeto</mat-label>
        <mat-select formControlName="project">
          <mat-option [value]="null">—</mat-option>
          @for (p of data.catalog.projects; track p.code) { <mat-option [value]="p.code">{{ p.code }} — {{ p.name }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field><mat-label>Tipo de demanda</mat-label>
        <mat-select formControlName="demandType">
          @for (t of data.catalog.demandTypes; track t.code) { <mat-option [value]="t.code" [disabled]="!t.active">{{ t.name }} · {{ t.workflowName }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field><mat-label>Prioridade</mat-label>
        <mat-select formControlName="priority">
          <mat-option [value]="null">—</mat-option>
          @for (p of data.catalog.priorities; track p.code) { <mat-option [value]="p.code">{{ p.code }} — {{ p.name }}</mat-option> }
        </mat-select>
        <mat-hint>{{ policy() }}</mat-hint>
      </mat-form-field>
      <mat-form-field><mat-label>Responsável</mat-label>
        <mat-select formControlName="ownerId">
          <mat-option [value]="null">—</mat-option>
          @for (u of data.users; track u.id) { <mat-option [value]="u.id">{{ u.fullName }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <mat-form-field><mat-label>Motivo *</mat-label>
        <textarea matInput formControlName="reason" rows="3"></textarea>
        @if (form.controls.reason.touched && form.controls.reason.invalid) { <mat-error>Informe o motivo.</mat-error> }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancelar</button>
      <button mat-flat-button color="primary" (click)="save()">Salvar classificação</button>
    </mat-dialog-actions>
  `,
  styles: [`mat-form-field { width: 100%; margin-bottom: 8px; }`]
})
export class ClassificationDialogComponent {
  data = inject<ClassificationData>(MAT_DIALOG_DATA);
  private ref = inject(MatDialogRef<ClassificationDialogComponent, ClassificationResult>);
  private fb = inject(FormBuilder);

  private initial = {
    project: this.data.detail.fields['project'] ?? null,
    demandType: this.data.detail.fields['demandType'] ?? null,
    priority: this.data.detail.fields['priority'] ?? null,
    ownerId: this.data.detail.summary.owner?.id ?? null
  };

  form = this.fb.group({
    project: [this.initial.project],
    demandType: [this.initial.demandType],
    priority: [this.initial.priority],
    ownerId: [this.initial.ownerId],
    reason: ['', [Validators.required, Validators.maxLength(2000)]]
  });

  policy(): string {
    return this.data.catalog.priorities.find(p => p.code === this.form.value.priority)?.policy ?? '';
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const fields: Record<string, string | null> = {};
    (['project', 'demandType', 'priority'] as const).forEach(k => {
      if ((v[k] ?? null) !== (this.initial[k] ?? null)) fields[k] = v[k] ?? null;
    });
    this.ref.close({ fields, reason: v.reason ?? '', ownerId: v.ownerId, ownerChanged: (v.ownerId ?? null) !== this.initial.ownerId });
  }
}
