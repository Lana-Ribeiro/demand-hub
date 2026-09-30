import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { debounceTime } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { DemandFilters } from '../../core/api/demand-api.service';
import { Catalog, UserRef } from '../../core/models';
import { CATEGORY_LABELS, IMPACT_LABELS } from '../../core/labels';

/** Filtros do PMO: projeto, prioridade, tipo, responsável, etapa, impacto, período e aprovação pendente. */
@Component({
  selector: 'dh-pmo-filters',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatCheckboxModule],
  template: `
    <form [formGroup]="form" class="filters">
      <mat-form-field class="q"><mat-label>Buscar (título, protocolo, ID legado)</mat-label>
        <mat-icon matPrefix>search</mat-icon><input matInput formControlName="q"></mat-form-field>
      <mat-form-field><mat-label>Projeto</mat-label>
        <mat-select formControlName="projectId"><mat-option [value]="null">Todos</mat-option>
          @for (p of catalog()?.projects ?? []; track p.id) { <mat-option [value]="p.id">{{ p.code }} — {{ p.name }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field><mat-label>Tipo</mat-label>
        <mat-select formControlName="type"><mat-option [value]="null">Todos</mat-option>
          @for (t of catalog()?.demandTypes ?? []; track t.code) { <mat-option [value]="t.code">{{ t.name }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field><mat-label>Prioridade</mat-label>
        <mat-select formControlName="priority"><mat-option [value]="null">Todas</mat-option>
          @for (p of catalog()?.priorities ?? []; track p.code) { <mat-option [value]="p.code">{{ p.code }} — {{ p.name }}</mat-option> }</mat-select></mat-form-field>
      <mat-form-field><mat-label>Responsável</mat-label>
        <mat-select formControlName="ownerId"><mat-option [value]="null">Todos</mat-option>
          @for (u of users(); track u.id) { <mat-option [value]="u.id">{{ u.fullName }}</mat-option> }</mat-select></mat-form-field>
      @if (extended) {
        <mat-form-field><mat-label>Etapa</mat-label>
          <mat-select formControlName="category"><mat-option [value]="null">Todas</mat-option>
            @for (c of categories; track c.value) { <mat-option [value]="c.value">{{ c.label }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Impacto</mat-label>
          <mat-select formControlName="impact"><mat-option [value]="null">Todos</mat-option>
            @for (c of impacts; track c.value) { <mat-option [value]="c.value">{{ c.label }}</mat-option> }</mat-select></mat-form-field>
        <mat-form-field><mat-label>Enviada a partir de</mat-label><input matInput type="date" formControlName="from"></mat-form-field>
        <mat-form-field><mat-label>Enviada até</mat-label><input matInput type="date" formControlName="to"></mat-form-field>
        <mat-checkbox formControlName="pendingApproval">Com aprovação pendente</mat-checkbox>
        <mat-checkbox formControlName="includeLegacy">Incluir legado</mat-checkbox>
      }
      <button mat-button type="button" (click)="clear()"><mat-icon>filter_alt_off</mat-icon> Limpar</button>
    </form>
  `,
  styles: [`.filters { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; }
            .filters mat-form-field { width: 190px; } .filters .q { width: 300px; }`]
})
export class PmoFiltersComponent implements OnInit {
  private platform = inject(PlatformApi);
  private fb = inject(FormBuilder);

  /** Filtros de etapa, impacto, período, aprovação e legado (somente na tabela). */
  @Input() extended = true;
  @Output() changed = new EventEmitter<DemandFilters>();

  catalog = signal<Catalog | null>(null);
  users = signal<UserRef[]>([]);
  categories = ['INTAKE', 'TRIAGE', 'ON_HOLD', 'APPROVAL', 'ANALYSIS', 'REFINEMENT', 'ARCHITECTURE', 'READY', 'EXECUTION', 'DOCUMENTATION', 'DONE', 'REJECTED', 'CANCELLED']
    .map(value => ({ value, label: CATEGORY_LABELS[value] }));
  impacts = Object.entries(IMPACT_LABELS).map(([value, label]) => ({ value, label }));

  form = this.fb.group({
    q: [''], projectId: [null as number | null], type: [null as string | null], priority: [null as string | null],
    ownerId: [null as string | null], category: [null as string | null], impact: [null as string | null],
    from: [null as string | null], to: [null as string | null], pendingApproval: [false], includeLegacy: [false]
  });

  ngOnInit(): void {
    this.platform.catalog().subscribe(c => this.catalog.set(c));
    this.platform.assignableUsers().subscribe({ next: u => this.users.set(u), error: () => {} });
    this.form.valueChanges.pipe(debounceTime(300)).subscribe(() => this.emit());
  }

  emit(): void {
    const v = this.form.getRawValue();
    this.changed.emit({
      q: v.q ?? '', projectId: v.projectId, type: v.type, priority: v.priority, ownerId: v.ownerId, category: v.category,
      impact: v.impact, from: v.from ? new Date(v.from + 'T00:00:00').toISOString() : null,
      to: v.to ? new Date(v.to + 'T23:59:59').toISOString() : null, pendingApproval: v.pendingApproval || null,
      includeLegacy: !!v.includeLegacy
    });
  }

  clear(): void { this.form.reset({ q: '', pendingApproval: false, includeLegacy: false }); }
}
