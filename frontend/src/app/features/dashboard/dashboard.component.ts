import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { PlatformApi } from '../../core/api/platform-api.service';
import { CountItem, Dashboard, ProjectRef } from '../../core/models';
import { StateViewComponent } from '../../shared/state-view.component';

/**
 * Lista de barras horizontais (série única): uma cor por padrão, cor da entidade quando ela tem identidade própria
 * (projeto/prioridade), valor em texto neutro ao lado, tooltip por barra. Cada lista também é legível como tabela.
 */
@Component({
  selector: 'dh-bar-list',
  standalone: true,
  imports: [MatTooltipModule],
  template: `
    @if (!items.length) { <p class="muted small">Dados insuficientes.</p> }
    <ul class="bars" role="table" [attr.aria-label]="label">
      @for (i of items; track i.key) {
        <li role="row" [matTooltip]="i.label + ': ' + i.count" matTooltipPosition="above">
          <span role="cell" class="name">{{ i.label }}</span>
          <span class="track" aria-hidden="true"><span class="fill" [style.width.%]="pct(i)" [style.background]="useEntityColor && i.color ? i.color : null"></span></span>
          <span role="cell" class="value">{{ i.count }}</span>
        </li>
      }
    </ul>
  `,
  styles: [`.bars { list-style: none; margin: 0; padding: 0; }
            .bars li { display: grid; grid-template-columns: minmax(120px, 42%) 1fr 36px; gap: 10px; align-items: center; padding: 5px 0; }
            .name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .track { height: 10px; background: #eef1f6; border-radius: 4px; overflow: hidden; }
            .fill { display: block; height: 100%; background: var(--dh-primary); border-radius: 0 4px 4px 0; min-width: 2px; }
            .value { text-align: right; font-variant-numeric: tabular-nums; font-weight: 600; color: var(--dh-text); }`]
})
export class BarListComponent {
  @Input() items: CountItem[] = [];
  @Input() label = '';
  @Input() useEntityColor = false;
  pct(i: CountItem): number {
    const max = Math.max(...this.items.map(x => x.count), 1);
    return (i.count / max) * 100;
  }
}

@Component({
  selector: 'dh-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, MatSelectModule, MatFormFieldModule, MatIconModule, MatTooltipModule, MatButtonModule,
    StateViewComponent, BarListComponent],
  template: `
    <div class="page">
      <div class="row">
        <div><h1>Dashboard gerencial</h1><p class="muted small">Indicadores calculados a partir dos registros reais da plataforma.
          @if (data()) { Atualizado {{ data()!.generatedAt | date: 'dd/MM/yyyy HH:mm' }}. }</p></div>
        <span class="spacer"></span>
        <mat-form-field style="width:280px"><mat-label>Projeto</mat-label>
          <mat-select [value]="projectId()" (selectionChange)="projectId.set($event.value); load()">
            <mat-option [value]="null">Todos os projetos</mat-option>
            @for (p of projects(); track p.id) { <mat-option [value]="p.id">{{ p.code }} — {{ p.name }}</mat-option> }
          </mat-select></mat-form-field>
      </div>

      <dh-state [loading]="loading()" [error]="error()" (retry)="load()">
        @if (data(); as d) {
          <div class="grid grid-4 kpis">
            @for (k of kpis(); track k.label) {
              <a class="card kpi" [routerLink]="k.link" [queryParams]="k.query">
                <div class="muted small">{{ k.label }}</div><div class="kpi-value">{{ k.value }}</div>
              </a>
            }
          </div>

          <div class="grid grid-3" style="margin-top:16px">
            @for (t of d.averageTimes; track t.label) {
              <section class="card">
                <div class="muted small">{{ t.label }}</div>
                @if (t.sufficient) {
                  <div class="kpi-value">{{ formatHours(t.averageHours!) }}</div>
                  <div class="small muted">base: {{ t.sampleSize }} passagem(ns) concluída(s)</div>
                } @else {
                  <div class="insufficient"><mat-icon>hourglass_empty</mat-icon> Dados insuficientes</div>
                  <div class="small muted">Nenhuma passagem concluída por esta etapa ainda.</div>
                }
              </section>
            }
          </div>

          <div class="grid grid-2" style="margin-top:16px">
            <section class="card"><h3>Demandas abertas por etapa</h3><dh-bar-list [items]="d.byStage" label="Demandas abertas por etapa"></dh-bar-list></section>
            <section class="card"><h3>Demandas abertas por projeto</h3><dh-bar-list [items]="d.byProject" [useEntityColor]="true" label="Por projeto"></dh-bar-list></section>
            <section class="card"><h3>Demandas abertas por tipo</h3><dh-bar-list [items]="d.byType" label="Por tipo"></dh-bar-list></section>
            <section class="card"><h3>Demandas abertas por prioridade</h3><dh-bar-list [items]="d.byPriority" [useEntityColor]="true" label="Por prioridade"></dh-bar-list></section>
          </div>

          <section class="card" style="margin-top:16px">
            <h3>Volume de demandas enviadas por mês (últimos 12 meses)</h3>
            @if (totalVolume() === 0) { <p class="muted small">Dados insuficientes.</p> } @else {
              <div class="columns" role="table" aria-label="Demandas enviadas por mês">
                @for (m of d.volumeByMonth; track m.key) {
                  <div class="col" role="row" [matTooltip]="monthLabel(m.key) + ': ' + m.count + ' demanda(s)'">
                    <span class="col-value" role="cell">{{ m.count || '' }}</span>
                    <span class="col-bar" [style.height.%]="colPct(m.count)" aria-hidden="true"></span>
                    <span class="col-label" role="cell">{{ monthLabel(m.key) }}</span>
                  </div>
                }
              </div>
            }
          </section>

          <section class="card" style="margin-top:16px">
            <h3>Demandas paradas (≥ {{ d.stalledThresholdDays }} dias na mesma etapa)</h3>
            @if (!d.stalled.length) { <p class="muted small">Nenhuma demanda parada acima do limite.</p> }
            @for (s of d.stalled; track s.id) {
              <a class="stalled" [routerLink]="['/demands', s.id]">
                <span class="mono muted">{{ s.protocol }}</span><strong>{{ s.title }}</strong><span class="spacer"></span>
                <span class="small muted">{{ s.stage }}</span><span class="chip warning">{{ s.daysInStage }} dias</span>
              </a>
            }
          </section>
        }
      </dh-state>
    </div>
  `,
  styles: [`.kpi { text-decoration: none; color: inherit; } .kpi-value { font-size: 28px; font-weight: 700; font-variant-numeric: tabular-nums; }
            .insufficient { display: flex; align-items: center; gap: 6px; font-size: 16px; font-weight: 600; color: var(--dh-muted); margin: 6px 0; }
            .columns { display: grid; grid-template-columns: repeat(12, 1fr); gap: 6px; height: 200px; align-items: end; padding-top: 8px; }
            .col { height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; gap: 4px; }
            .col-bar { width: 60%; max-width: 36px; background: var(--dh-primary); border-radius: 4px 4px 0 0; min-height: 1px; }
            .col-value { font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
            .col-label { font-size: 11px; color: var(--dh-muted); white-space: nowrap; }
            .stalled { display: flex; gap: 10px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--dh-border); text-decoration: none; color: inherit; flex-wrap: wrap; }
            .stalled:last-child { border-bottom: 0; }`]
})
export class DashboardComponent implements OnInit {
  private platform = inject(PlatformApi);

  data = signal<Dashboard | null>(null);
  projects = signal<ProjectRef[]>([]);
  projectId = signal<number | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  kpis = computed(() => {
    const d = this.data();
    if (!d) return [];
    return [
      { label: 'Demandas abertas', value: d.openDemands, link: '/pmo/table', query: {} },
      { label: 'Aguardando aprovação', value: d.pendingApprovals, link: '/pmo/table', query: {} },
      { label: 'Em refinamento', value: d.inRefinement, link: '/pmo/board', query: {} },
      { label: 'Em desenvolvimento / execução', value: d.inDevelopment, link: '/pmo/board', query: {} },
      { label: 'Execução técnica em QA', value: d.inQa, link: '/executions', query: {} },
      { label: 'Concluídas', value: d.completedDemands, link: '/pmo/table', query: {} },
      { label: 'Rejeitadas', value: d.rejectedDemands, link: '/pmo/table', query: {} },
      { label: 'Paradas', value: d.stalled.length, link: '/pmo/table', query: {} },
    ];
  });
  totalVolume = computed(() => (this.data()?.volumeByMonth ?? []).reduce((s, m) => s + m.count, 0));

  ngOnInit(): void {
    this.platform.catalog().subscribe(c => this.projects.set(c.projects));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.platform.dashboard(this.projectId()).subscribe({
      next: d => { this.data.set(d); this.loading.set(false); this.error.set(null); },
      error: () => { this.error.set('Não foi possível carregar o dashboard.'); this.loading.set(false); }
    });
  }

  colPct(count: number): number {
    const max = Math.max(...(this.data()?.volumeByMonth ?? []).map(m => m.count), 1);
    return (count / max) * 85;
  }

  monthLabel(key: string): string {
    const [y, m] = key.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' });
  }

  formatHours(h: number): string {
    if (h < 1) return h * 60 < 1 ? '< 1 min' : `${Math.round(h * 60)} min`;
    return h >= 48 ? `${(h / 24).toFixed(1).replace('.', ',')} dias` : `${h.toFixed(1).replace('.', ',')} h`;
  }
}
