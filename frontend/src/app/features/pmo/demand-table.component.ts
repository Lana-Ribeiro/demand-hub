import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { DemandApi, DemandFilters } from '../../core/api/demand-api.service';
import { DemandSummary } from '../../core/models';
import { IMPACT_LABELS } from '../../core/labels';
import { PriorityBadgeComponent, ProjectBadgeComponent, StageChipComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';
import { PmoFiltersComponent } from './pmo-filters.component';

/** Visão operacional em tabela com filtros e paginação no servidor. */
@Component({
  selector: 'dh-demand-table',
  standalone: true,
  imports: [RouterLink, DatePipe, MatTableModule, MatPaginatorModule, MatSelectModule, MatFormFieldModule, MatIconModule, MatButtonModule,
    PriorityBadgeComponent, ProjectBadgeComponent, StageChipComponent, StateViewComponent, PmoFiltersComponent],
  template: `
    <div class="page">
      <div class="row"><h1>Todas as demandas</h1><span class="spacer"></span>
        <mat-form-field style="width:220px"><mat-label>Ordenar por</mat-label>
          <mat-select [value]="sort" (selectionChange)="sort = $event.value; load()">
            <mat-option value="updatedAt">Última atualização</mat-option><mat-option value="priority">Prioridade</mat-option>
            <mat-option value="submittedAt">Data de envio</mat-option><mat-option value="stageEnteredAt">Mais tempo na etapa</mat-option>
          </mat-select></mat-form-field>
        <a mat-stroked-button routerLink="/pmo/board"><mat-icon>view_kanban</mat-icon> Kanban</a></div>
      <section class="card"><dh-pmo-filters (changed)="filters = $event; page = 0; load()"></dh-pmo-filters></section>
      <section class="card">
        <dh-state [loading]="loading()" [error]="error()" (retry)="load()" [empty]="!rows().length" emptyText="Nenhuma demanda encontrada com os filtros.">
          <div class="scroll">
            <table mat-table [dataSource]="rows()" class="full">
              <ng-container matColumnDef="protocol"><th mat-header-cell *matHeaderCellDef>Protocolo</th>
                <td mat-cell *matCellDef="let d" class="mono nowrap">{{ d.protocol || d.legacy?.originalId || '—' }}</td></ng-container>
              <ng-container matColumnDef="title"><th mat-header-cell *matHeaderCellDef>Título</th>
                <td mat-cell *matCellDef="let d"><strong>{{ d.title }}</strong><div class="small muted">{{ d.requester?.fullName || 'Legado' }}</div></td></ng-container>
              <ng-container matColumnDef="project"><th mat-header-cell *matHeaderCellDef>Projeto</th>
                <td mat-cell *matCellDef="let d"><dh-project-badge [project]="d.project" [compact]="true"></dh-project-badge></td></ng-container>
              <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Tipo</th><td mat-cell *matCellDef="let d">{{ d.type?.name || '—' }}</td></ng-container>
              <ng-container matColumnDef="priority"><th mat-header-cell *matHeaderCellDef>Prioridade</th>
                <td mat-cell *matCellDef="let d"><dh-priority-badge [priority]="d.priority"></dh-priority-badge></td></ng-container>
              <ng-container matColumnDef="impact"><th mat-header-cell *matHeaderCellDef>Impacto</th><td mat-cell *matCellDef="let d">{{ impact(d.impactLevel) }}</td></ng-container>
              <ng-container matColumnDef="stage"><th mat-header-cell *matHeaderCellDef>Etapa</th>
                <td mat-cell *matCellDef="let d"><dh-stage-chip [stage]="d.stage" [lifecycle]="d.lifecycleState"></dh-stage-chip>
                  @if (d.pendingApprovals) { <span class="chip warning small">{{ d.pendingApprovals }} aprov.</span> }</td></ng-container>
              <ng-container matColumnDef="owner"><th mat-header-cell *matHeaderCellDef>Responsável</th><td mat-cell *matCellDef="let d">{{ d.owner?.fullName || '—' }}</td></ng-container>
              <ng-container matColumnDef="submitted"><th mat-header-cell *matHeaderCellDef>Enviada</th>
                <td mat-cell *matCellDef="let d" class="small">{{ d.submittedAt | date: 'dd/MM/yyyy' }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="columns; sticky: true"></tr>
              <tr mat-row *matRowDef="let d; columns: columns" class="clickable" tabindex="0" (click)="open(d)" (keydown.enter)="open(d)"></tr>
            </table>
          </div>
          <mat-paginator [length]="total()" [pageIndex]="page" [pageSize]="size" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" aria-label="Paginação"></mat-paginator>
        </dh-state>
      </section>
    </div>
  `,
  styles: [`.scroll { overflow-x: auto; }`]
})
export class DemandTableComponent implements OnInit {
  ngOnInit(): void { this.load(); }

  private api = inject(DemandApi);
  private router = inject(Router);

  columns = ['protocol', 'title', 'project', 'type', 'priority', 'impact', 'stage', 'owner', 'submitted'];
  rows = signal<DemandSummary[]>([]);
  total = signal(0);
  loading = signal(true);
  error = signal<string | null>(null);
  filters: DemandFilters = {};
  page = 0;
  size = 25;
  sort = 'updatedAt';

  load(): void {
    this.loading.set(true);
    this.api.search({ ...this.filters, page: this.page, size: this.size, sort: this.sort }).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.page.totalElements); this.loading.set(false); this.error.set(null); },
      error: () => { this.error.set('Não foi possível carregar as demandas.'); this.loading.set(false); }
    });
  }

  onPage(e: PageEvent): void { this.page = e.pageIndex; this.size = e.pageSize; this.load(); }
  impact(v?: string): string { return IMPACT_LABELS[v ?? ''] ?? '—'; }
  open(d: DemandSummary): void { this.router.navigate(['/demands', d.id]); }
}
