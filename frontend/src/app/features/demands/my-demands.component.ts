import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { DemandApi } from '../../core/api/demand-api.service';
import { DemandSummary } from '../../core/models';
import { AuthService } from '../../core/auth/auth.service';
import { StageChipComponent, ProjectBadgeComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';

type Filter = 'all' | 'draft' | 'open' | 'closed';

@Component({
  selector: 'dh-my-demands',
  standalone: true,
  imports: [RouterLink, DatePipe, MatTableModule, MatButtonModule, MatIconModule, MatButtonToggleModule, StageChipComponent,
    ProjectBadgeComponent, StateViewComponent],
  template: `
    <div class="page">
      <div class="row">
        <h1>Minhas demandas</h1>
        <span class="spacer"></span>
        @if (auth.has('DEMAND_CREATE')) {
          <a mat-flat-button color="primary" routerLink="/demands/new"><mat-icon>add</mat-icon> Nova demanda</a>
        }
      </div>
      <mat-button-toggle-group [value]="filter()" (change)="filter.set($event.value)" aria-label="Filtrar demandas">
        <mat-button-toggle value="all">Todas ({{ items().length }})</mat-button-toggle>
        <mat-button-toggle value="draft">Rascunhos</mat-button-toggle>
        <mat-button-toggle value="open">Em andamento</mat-button-toggle>
        <mat-button-toggle value="closed">Encerradas</mat-button-toggle>
      </mat-button-toggle-group>

      <section class="card" style="margin-top:16px">
        <dh-state [loading]="loading()" [error]="error()" (retry)="load()" [empty]="!filtered().length" emptyText="Nenhuma demanda neste filtro.">
          <table mat-table [dataSource]="filtered()" class="full">
            <ng-container matColumnDef="protocol">
              <th mat-header-cell *matHeaderCellDef>Protocolo</th>
              <td mat-cell *matCellDef="let d" class="mono">{{ d.protocol || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Título</th>
              <td mat-cell *matCellDef="let d"><strong>{{ d.title || '(sem título)' }}</strong></td>
            </ng-container>
            <ng-container matColumnDef="project">
              <th mat-header-cell *matHeaderCellDef>Projeto</th>
              <td mat-cell *matCellDef="let d"><dh-project-badge [project]="d.project" [compact]="true"></dh-project-badge></td>
            </ng-container>
            <ng-container matColumnDef="stage">
              <th mat-header-cell *matHeaderCellDef>Etapa</th>
              <td mat-cell *matCellDef="let d"><dh-stage-chip [stage]="d.stage" [lifecycle]="d.lifecycleState"></dh-stage-chip></td>
            </ng-container>
            <ng-container matColumnDef="updated">
              <th mat-header-cell *matHeaderCellDef>Atualização</th>
              <td mat-cell *matCellDef="let d" class="small muted">{{ d.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let d; columns: columns" class="clickable" (click)="open(d)" (keydown.enter)="open(d)" tabindex="0"></tr>
          </table>
        </dh-state>
      </section>
    </div>
  `
})
export class MyDemandsComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(DemandApi);
  private router = inject(Router);

  columns = ['protocol', 'title', 'project', 'stage', 'updated'];
  items = signal<DemandSummary[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  filter = signal<Filter>('all');

  filtered = computed(() => {
    const f = this.filter();
    return this.items().filter(d =>
      f === 'all' ? true
        : f === 'draft' ? d.lifecycleState === 'DRAFT'
          : f === 'open' ? d.lifecycleState === 'ACTIVE' || d.lifecycleState === 'ON_HOLD'
            : ['COMPLETED', 'REJECTED', 'CANCELLED'].includes(d.lifecycleState));
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.mine().subscribe({
      next: items => { this.items.set(items); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar suas demandas.'); this.loading.set(false); }
    });
  }

  open(d: DemandSummary): void {
    this.router.navigate(d.lifecycleState === 'DRAFT' ? ['/demands', d.id, 'edit'] : ['/demands', d.id]);
  }
}
