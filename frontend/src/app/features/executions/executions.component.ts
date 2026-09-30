import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { forkJoin } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { ExecutionRow, ExecutionStatus } from '../../core/models';
import { ModeBadgeComponent, PriorityBadgeComponent, ProjectBadgeComponent, StageChipComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';

/** Execuções técnicas (lifecycle do repositório GitLab/GitHub) lado a lado com a etapa da demanda (lifecycle Jira PMO). */
@Component({
  selector: 'dh-executions',
  standalone: true,
  imports: [DatePipe, MatTableModule, MatButtonToggleModule, StateViewComponent, ProjectBadgeComponent, PriorityBadgeComponent, StageChipComponent, ModeBadgeComponent],
  template: `
    <div class="page">
      <div class="row"><h1>Execução técnica</h1><span class="spacer"></span>
        <span class="small muted">{{ scmSystem() }}:</span><dh-mode-badge [mode]="scmMode()"></dh-mode-badge></div>
      <p class="muted">O status técnico vem do repositório de código ({{ scmSystem() }}) e não altera a etapa da demanda; avanços são comentados no Jira PMO.</p>
      <mat-button-toggle-group [value]="filter()" (change)="filter.set($event.value)" aria-label="Filtrar por status técnico">
        <mat-button-toggle value="">Todas ({{ rows().length }})</mat-button-toggle>
        @for (s of statuses(); track s.code) { <mat-button-toggle [value]="s.code">{{ s.name }} ({{ count(s.code) }})</mat-button-toggle> }
      </mat-button-toggle-group>
      <section class="card" style="margin-top:16px">
        <dh-state [loading]="loading()" [error]="error()" (retry)="load()" [empty]="!filtered().length" emptyText="Nenhuma execução técnica." emptyIcon="engineering">
          <table mat-table [dataSource]="filtered()" class="full">
            <ng-container matColumnDef="demand"><th mat-header-cell *matHeaderCellDef>Demanda</th>
              <td mat-cell *matCellDef="let r"><div class="mono small muted">{{ r.demand?.protocol }}</div><strong>{{ r.demand?.title }}</strong></td></ng-container>
            <ng-container matColumnDef="project"><th mat-header-cell *matHeaderCellDef>Projeto</th>
              <td mat-cell *matCellDef="let r"><dh-project-badge [project]="r.demand?.project" [compact]="true"></dh-project-badge></td></ng-container>
            <ng-container matColumnDef="priority"><th mat-header-cell *matHeaderCellDef>Prioridade</th>
              <td mat-cell *matCellDef="let r"><dh-priority-badge [priority]="r.demand?.priority"></dh-priority-badge></td></ng-container>
            <ng-container matColumnDef="lifecycle"><th mat-header-cell *matHeaderCellDef>Ciclo da demanda (Jira)</th>
              <td mat-cell *matCellDef="let r"><dh-stage-chip [stage]="r.demand?.stage"></dh-stage-chip></td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Execução técnica ({{ scmSystem() }})</th>
              <td mat-cell *matCellDef="let r"><span class="chip info">{{ statusName(r.status) }}</span></td></ng-container>
            <ng-container matColumnDef="strategy"><th mat-header-cell *matHeaderCellDef>Estratégia</th>
              <td mat-cell *matCellDef="let r" class="small">{{ r.strategy === 'AGENT_SQUAD' ? 'Squad de agentes' : 'Prompt técnico' }}</td></ng-container>
            <ng-container matColumnDef="last"><th mat-header-cell *matHeaderCellDef>Último evento</th>
              <td mat-cell *matCellDef="let r" class="small">{{ r.lastEventAt | date: 'dd/MM/yyyy HH:mm' }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let r; columns: columns" class="clickable" tabindex="0" (click)="open(r)" (keydown.enter)="open(r)"></tr>
          </table>
        </dh-state>
      </section>
    </div>
  `
})
export class ExecutionsComponent implements OnInit {
  private platform = inject(PlatformApi);
  private router = inject(Router);

  columns = ['demand', 'project', 'priority', 'lifecycle', 'status', 'strategy', 'last'];
  rows = signal<ExecutionRow[]>([]);
  statuses = signal<ExecutionStatus[]>([]);
  scmMode = signal<string | null>(null);
  scmSystem = signal('Repositório');
  filter = signal('');
  loading = signal(true);
  error = signal<string | null>(null);
  filtered = computed(() => this.rows().filter(r => !this.filter() || r.status === this.filter()));

  ngOnInit(): void { this.load(); }

  load(): void {
    forkJoin({ rows: this.platform.executions(), statuses: this.platform.executionStatuses(), integrations: this.platform.integrationStatus() }).subscribe({
      next: r => { this.rows.set(r.rows); this.statuses.set(r.statuses); this.scmMode.set(r.integrations.scm); this.scmSystem.set(r.integrations.scmSystem); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar as execuções.'); this.loading.set(false); }
    });
  }

  count(code: string): number { return this.rows().filter(r => r.status === code).length; }
  statusName(code: string): string { return this.statuses().find(s => s.code === code)?.name ?? code; }
  open(r: ExecutionRow): void { if (r.demand) this.router.navigate(['/demands', r.demand.id]); }
}
