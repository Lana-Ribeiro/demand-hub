import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { DemandApi, DemandFilters } from '../../core/api/demand-api.service';
import { BoardColumn } from '../../core/models';
import { ModeBadgeComponent, PriorityBadgeComponent, ProjectBadgeComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';
import { PmoFiltersComponent } from './pmo-filters.component';

/** Kanban por categoria de etapa. Visual: as decisões acontecem no detalhe da demanda, sempre pelo workflow. */
@Component({
  selector: 'dh-board',
  standalone: true,
  imports: [RouterLink, DatePipe, MatIconModule, MatButtonModule, MatSlideToggleModule, ProjectBadgeComponent, PriorityBadgeComponent,
    ModeBadgeComponent, StateViewComponent, PmoFiltersComponent],
  template: `
    <div class="page wide">
      <div class="row"><h1>Kanban PMO</h1><span class="spacer"></span>
        <mat-slide-toggle [checked]="showClosed()" (change)="showClosed.set($event.checked)">Mostrar concluídas/rejeitadas</mat-slide-toggle>
        <a mat-stroked-button routerLink="/pmo/table"><mat-icon>table_rows</mat-icon> Ver em tabela</a></div>
      <section class="card"><dh-pmo-filters [extended]="false" (changed)="load($event)"></dh-pmo-filters></section>
      <dh-state [loading]="loading()" [error]="error()" (retry)="load(filters)">
        <div class="board" role="list">
          @for (col of visibleColumns(); track col.category) {
            <section class="column" role="listitem" [attr.aria-label]="col.label">
              <header><strong>{{ col.label }}</strong><span class="count">{{ col.items.length }}</span></header>
              <div class="cards">
                @for (d of col.items; track d.id) {
                  <a class="kcard" [routerLink]="['/demands', d.id]" [style.border-left-color]="d.project?.color || '#c7ccd9'">
                    <div class="row small"><span class="mono muted">{{ d.protocol }}</span><span class="spacer"></span><dh-priority-badge [priority]="d.priority"></dh-priority-badge></div>
                    <div class="title">{{ d.title }}</div>
                    <dh-project-badge [project]="d.project" [compact]="true"></dh-project-badge>
                    <div class="small muted">{{ d.stage?.name }} · desde {{ d.stageEnteredAt | date: 'dd/MM' }}</div>
                    <div class="row small tags">
                      @if (d.pendingApprovals) { <span class="chip warning">{{ d.pendingApprovals }} aprovação(ões)</span> }
                      @if (d.jira?.key) { <span class="chip neutral">{{ d.jira?.key }}</span><dh-mode-badge [mode]="d.jira?.mode"></dh-mode-badge> }
                      @if (d.executionStatus) { <span class="chip info">Técnico: {{ d.executionStatus }}</span> }
                    </div>
                    <div class="small muted">{{ d.owner?.fullName || 'Sem responsável' }}</div>
                  </a>
                } @empty { <div class="empty small muted">Vazio</div> }
              </div>
            </section>
          }
        </div>
      </dh-state>
    </div>
  `,
  styles: [`.wide { max-width: none; } .board { display: flex; gap: 12px; overflow-x: auto; padding: 16px 0 8px; align-items: flex-start; }
            .column { background: #eaedf4; border-radius: 10px; width: 280px; flex: none; padding: 10px; }
            .column header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
            .count { background: #fff; border-radius: 999px; padding: 0 8px; font-size: 12px; }
            .cards { display: flex; flex-direction: column; gap: 8px; min-height: 40px; }
            .kcard { background: #fff; border-radius: 8px; padding: 10px; border-left: 4px solid; text-decoration: none; color: inherit; display: flex; flex-direction: column; gap: 4px; box-shadow: 0 1px 2px rgba(16,24,40,.06); }
            .kcard:hover, .kcard:focus { box-shadow: 0 4px 10px rgba(16,24,40,.12); }
            .title { font-weight: 600; } .tags { gap: 4px; } .empty { text-align: center; padding: 12px; }`]
})
export class BoardComponent implements OnInit {
  private api = inject(DemandApi);
  columns = signal<BoardColumn[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  showClosed = signal(false);
  filters: DemandFilters = {};

  visibleColumns(): BoardColumn[] {
    return this.columns().filter(c => this.showClosed() || (c.category !== 'DONE' && c.category !== 'REJECTED'));
  }

  ngOnInit(): void { this.load({}); }

  load(f: DemandFilters): void {
    this.filters = f;
    this.api.board({ q: f.q, projectId: f.projectId, type: f.type, priority: f.priority, ownerId: f.ownerId }).subscribe({
      next: c => { this.columns.set(c); this.loading.set(false); this.error.set(null); },
      error: () => { this.error.set('Não foi possível carregar o Kanban.'); this.loading.set(false); }
    });
  }
}
