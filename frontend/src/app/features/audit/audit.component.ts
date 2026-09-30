import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { debounceTime } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { AuditLog } from '../../core/models';
import { StateViewComponent } from '../../shared/state-view.component';

/** Trilha de auditoria global: quem, quando, o quê, antes, depois e motivo (inclui decisões sobre sugestões da IA). */
@Component({
  selector: 'dh-audit',
  standalone: true,
  imports: [DatePipe, RouterLink, ReactiveFormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule, StateViewComponent],
  template: `
    <div class="page">
      <h1>Auditoria</h1>
      <section class="card">
        <form [formGroup]="form" class="row">
          <mat-form-field style="width:260px"><mat-label>Ação (ex.: CHANGE_PRIORITY)</mat-label><input matInput formControlName="action"></mat-form-field>
          <mat-form-field style="width:260px"><mat-label>Ator</mat-label><input matInput formControlName="actor"></mat-form-field>
          <mat-form-field style="width:320px"><mat-label>ID da demanda</mat-label><input matInput formControlName="demandId"></mat-form-field>
        </form>
      </section>
      <section class="card">
        <dh-state [loading]="loading()" [error]="error()" (retry)="load()" [empty]="!rows().length" emptyText="Nenhum registro encontrado." emptyIcon="history">
          <div style="overflow-x:auto">
            <table mat-table [dataSource]="rows()" class="full">
              <ng-container matColumnDef="when"><th mat-header-cell *matHeaderCellDef>Quando</th><td mat-cell *matCellDef="let l" class="small">{{ l.createdAt | date: 'dd/MM/yyyy HH:mm:ss' }}</td></ng-container>
              <ng-container matColumnDef="actor"><th mat-header-cell *matHeaderCellDef>Ator</th><td mat-cell *matCellDef="let l"><strong>{{ l.actorName }}</strong><div class="small muted">{{ l.actorRoles }}</div></td></ng-container>
              <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef>Ação</th><td mat-cell *matCellDef="let l"><span class="chip neutral mono small">{{ l.action }}</span>
                @if (l.aiSuggestionId) { <span class="chip info small">IA</span> }</td></ng-container>
              <ng-container matColumnDef="change"><th mat-header-cell *matHeaderCellDef>Alteração</th>
                <td mat-cell *matCellDef="let l" class="small">@if (l.field) { {{ l.field }}: <span class="muted">{{ l.beforeValue || '∅' }}</span> → <strong>{{ l.afterValue || '∅' }}</strong> }
                  @if (l.metadata) { <div class="muted">{{ l.metadata }}</div> }</td></ng-container>
              <ng-container matColumnDef="reason"><th mat-header-cell *matHeaderCellDef>Motivo</th><td mat-cell *matCellDef="let l" class="small">{{ l.reason }}</td></ng-container>
              <ng-container matColumnDef="demand"><th mat-header-cell *matHeaderCellDef>Demanda</th>
                <td mat-cell *matCellDef="let l">@if (l.demandId) { <a [routerLink]="['/demands', l.demandId]">abrir</a> }</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let l; columns: columns"></tr>
            </table>
          </div>
          <mat-paginator [length]="total()" [pageIndex]="page" [pageSize]="50" (page)="onPage($event)"></mat-paginator>
        </dh-state>
      </section>
    </div>
  `
})
export class AuditComponent implements OnInit {
  private platform = inject(PlatformApi);
  private fb = inject(FormBuilder);

  columns = ['when', 'actor', 'action', 'change', 'reason', 'demand'];
  rows = signal<AuditLog[]>([]);
  total = signal(0);
  loading = signal(true);
  error = signal<string | null>(null);
  page = 0;
  form = this.fb.nonNullable.group({ action: [''], actor: [''], demandId: [''] });

  ngOnInit(): void {
    this.load();
    this.form.valueChanges.pipe(debounceTime(400)).subscribe(() => { this.page = 0; this.load(); });
  }

  load(): void {
    const v = this.form.getRawValue();
    const params: Record<string, string | number> = { page: this.page, size: 50 };
    if (v.action) params['action'] = v.action.trim().toUpperCase();
    if (v.actor) params['actor'] = v.actor.trim();
    if (/^[0-9a-f-]{36}$/i.test(v.demandId.trim())) params['demandId'] = v.demandId.trim();
    this.platform.auditSearch(params).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.page.totalElements); this.loading.set(false); this.error.set(null); },
      error: () => { this.error.set('Não foi possível carregar a auditoria.'); this.loading.set(false); }
    });
  }

  onPage(e: PageEvent): void { this.page = e.pageIndex; this.load(); }
}
