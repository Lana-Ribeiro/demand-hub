import { Component, EventEmitter, Input, OnChanges, Output, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PlatformApi } from '../../../core/api/platform-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { DemandDetail, ExecutionPanel } from '../../../core/models';
import { NotifyService } from '../../../core/ui/notify.service';
import { ModeBadgeComponent } from '../../../shared/badges';
import { StateViewComponent } from '../../../shared/state-view.component';

/**
 * Painel "Ciclo da Demanda × Execução Técnica": deixa explícito que o Jira PMO é o lifecycle da demanda e
 * o repositório de código (GitLab ou GitHub) é a fonte da execução técnica. Status técnico não altera o estágio da demanda.
 */
@Component({
  selector: 'dh-execution-tab',
  standalone: true,
  imports: [DatePipe, FormsModule, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule, MatTooltipModule, ModeBadgeComponent, StateViewComponent],
  template: `
    <dh-state [loading]="loading()" [error]="error()" (retry)="load()">
      @if (panel(); as p) {
        <div class="grid grid-2">
          <section class="card lifecycle">
            <div class="row"><mat-icon>flag</mat-icon><h3>Ciclo da Demanda</h3><span class="spacer"></span><span class="chip">Jira PMO</span><dh-mode-badge [mode]="p.jiraMode"></dh-mode-badge></div>
            <div class="big">{{ p.demandLifecycle.stage || '—' }}</div>
            <div class="small muted">Status correspondente no Jira: {{ p.demandLifecycle.jiraStatus || '—' }}</div>
            @if (p.demandLifecycle.jira; as j) {
              <div class="link">Issue: @if (j.url) { <a [href]="j.url" target="_blank" rel="noopener">{{ j.key }}</a> } @else { {{ j.key || 'não criada' }} }
                · Sincronização: <span class="chip" [class.success]="j.syncStatus === 'OK'" [class.danger]="j.syncStatus === 'FAILED'">{{ j.syncStatus }}</span>
                @if (j.lastError) { <div class="small" style="color:var(--dh-danger)">{{ j.lastError }}</div> }
              </div>
            } @else { <div class="small muted">Issue Jira ainda não criada (criada após a aprovação do PMO).</div> }
            @if (auth.hasAny('INTEGRATION_MANAGE', 'DEMAND_TRIAGE') && p.jiraMode !== 'DISABLED') {
              <button mat-stroked-button (click)="jiraSync()"><mat-icon>sync</mat-icon> Sincronizar/reprocessar Jira</button>
            }
          </section>

          <section class="card technical">
            <div class="row"><mat-icon>code</mat-icon><h3>Execução Técnica</h3><span class="spacer"></span><span class="chip">{{ p.scmSystem }}</span><dh-mode-badge [mode]="p.scmMode"></dh-mode-badge></div>
            @if (!p.technicalDemand) {
              <p class="muted">Demanda não técnica: não há execução no repositório de código. A execução é operacional e acompanhada pelo workflow da demanda.</p>
            } @else if (!p.technicalExecution) {
              <p class="muted">A execução técnica é criada automaticamente quando a demanda fica “Pronta para desenvolvimento”.</p>
            } @else {
              @if (p.technicalExecution; as e) {
                <div class="big">{{ statusName(e.status) }}</div>
                <div class="steps" role="list" aria-label="Etapas técnicas">
                  @for (s of p.statuses; track s.code) {
                    <span role="listitem" class="step" [class.done]="position(s.code) < position(e.status)" [class.current]="s.code === e.status">{{ s.name }}</span>
                  }
                </div>
                <div class="small muted">Estratégia: {{ e.strategy === 'AGENT_SQUAD' ? 'Squad de agentes' : 'Prompt técnico' }} · iniciada {{ e.startedAt | date: 'dd/MM/yyyy HH:mm' }}</div>
                @if (e.repository; as g) {
                  <div class="link">Issue: @if (g.url) { <a [href]="g.url" target="_blank" rel="noopener">{{ g.projectRef }}#{{ g.key }}</a> } @else { {{ g.projectRef }}#{{ g.key || '—' }} }
                    · <span class="chip" [class.success]="g.syncStatus === 'OK'" [class.danger]="g.syncStatus === 'FAILED'">{{ g.syncStatus }}</span>
                    @if (g.lastError) { <div class="small" style="color:var(--dh-danger)">{{ g.lastError }}</div> }
                  </div>
                  @if (g.syncStatus === 'FAILED' && auth.hasAny('INTEGRATION_MANAGE', 'EXECUTION_MANAGE')) {
                    <button mat-stroked-button (click)="scmRetry()"><mat-icon>sync</mat-icon> Reprocessar {{ p.scmSystem }}</button>
                  }
                }
                @if (auth.hasAny('EXECUTION_MANAGE', 'ARCHITECTURE_MANAGE')) {
                  <div class="row controls">
                    <mat-form-field class="w"><mat-label>Estratégia</mat-label>
                      <mat-select [ngModel]="e.strategy" (ngModelChange)="setStrategy($event)">
                        <mat-option value="TECHNICAL_PROMPT">Prompt técnico (A)</mat-option>
                        <mat-option value="AGENT_SQUAD">Squad de agentes (B)</mat-option>
                      </mat-select></mat-form-field>
                  </div>
                }
                @if (p.scmMode === 'MOCK' && auth.has('EXECUTION_MANAGE')) {
                  <div class="alert mock small"><mat-icon>science</mat-icon>
                    <div>Simulação de evento do {{ p.scmSystem }} (modo MOCK) — passa pelo mesmo processador dos webhooks reais.
                      <div class="row"><mat-form-field class="w"><mat-label>Novo status</mat-label>
                        <mat-select [(ngModel)]="simStatus">@for (s of p.statuses; track s.code) { <mat-option [value]="s.code">{{ s.name }}</mat-option> }</mat-select></mat-form-field>
                        <button mat-flat-button (click)="simulate()">Simular evento</button></div></div></div>
                }
                @if (p.scmMode === 'DISABLED' && auth.hasAny('EXECUTION_MANAGE', 'QA_RECORD')) {
                  <div class="alert info small"><mat-icon>edit</mat-icon>
                    <div>Integração {{ p.scmSystem }} desabilitada: registre o status técnico manualmente.
                      <div class="row"><mat-form-field class="w"><mat-label>Status</mat-label>
                        <mat-select [(ngModel)]="simStatus">@for (s of p.statuses; track s.code) { <mat-option [value]="s.code">{{ s.name }}</mat-option> }</mat-select></mat-form-field>
                        <button mat-flat-button (click)="manual()">Registrar</button></div></div></div>
                }
              }
            }
          </section>
        </div>

        @if (p.technicalExecution?.events?.length) {
          <section class="card">
            <h3>Eventos da execução técnica</h3>
            @for (ev of p.technicalExecution!.events; track ev.id) {
              <div class="event">
                <span class="small muted">{{ ev.receivedAt | date: 'dd/MM/yyyy HH:mm' }}</span>
                <span><strong>{{ statusName(ev.fromStatus) }} → {{ statusName(ev.toStatus) }}</strong></span>
                <span class="chip neutral small">{{ ev.source }}</span>
                <div class="small">{{ ev.summary }} <span class="muted">(comentado no Jira PMO)</span></div>
              </div>
            }
          </section>
        }
      }
    </dh-state>
  `,
  styles: [`.big { font-size: 22px; font-weight: 700; margin: 8px 0; } .lifecycle { border-top: 3px solid var(--dh-primary); } .technical { border-top: 3px solid var(--dh-accent, #00897b); }
            .link { margin: 8px 0; } .controls { margin-top: 8px; } .w { width: 240px; }
            .steps { display: flex; flex-wrap: wrap; gap: 4px; margin: 8px 0; }
            .step { font-size: 12px; padding: 3px 8px; border-radius: 999px; background: #eef1f6; color: var(--dh-muted); }
            .step.done { background: var(--dh-success-soft); color: var(--dh-success); } .step.current { background: var(--dh-primary); color: #fff; }
            .event { padding: 8px 0; border-bottom: 1px solid var(--dh-border); display: flex; gap: 10px; flex-wrap: wrap; align-items: center; } .event:last-child { border-bottom: 0; }
            .alert { margin-top: 12px; }`]
})
export class ExecutionTabComponent implements OnChanges {
  auth = inject(AuthService);
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);

  @Input({ required: true }) detail!: DemandDetail;
  @Output() changed = new EventEmitter<void>();

  loading = signal(true);
  error = signal<string | null>(null);
  panel = signal<ExecutionPanel | null>(null);
  simStatus = 'DEVELOPMENT';
  private order = computed(() => new Map((this.panel()?.statuses ?? []).map(s => [s.code, s.position])));

  ngOnChanges(): void { this.load(); }

  load(): void {
    this.platform.execution(this.detail.summary.id).subscribe({
      next: p => { this.panel.set(p); this.loading.set(false); },
      error: err => { this.error.set(NotifyService.message(err)); this.loading.set(false); }
    });
  }

  position(code?: string): number { return code ? this.order().get(code) ?? 0 : 0; }
  statusName(code?: string): string { return this.panel()?.statuses.find(s => s.code === code)?.name ?? code ?? '—'; }

  setStrategy(strategy: string): void {
    this.platform.setStrategy(this.detail.summary.id, strategy).subscribe({ next: () => { this.notify.success('Estratégia atualizada.'); this.load(); }, error: err => this.notify.error(err) });
  }

  simulate(): void {
    this.platform.simulateScm(this.detail.summary.id, this.simStatus).subscribe({
      next: () => { this.notify.success('Evento simulado processado.'); this.load(); this.changed.emit(); }, error: err => this.notify.error(err)
    });
  }

  manual(): void {
    this.platform.manualStatus(this.detail.summary.id, this.simStatus).subscribe({
      next: () => { this.notify.success('Status registrado.'); this.load(); this.changed.emit(); }, error: err => this.notify.error(err)
    });
  }

  jiraSync(): void {
    this.platform.jiraSync(this.detail.summary.id).subscribe({ next: () => { this.notify.success('Jira sincronizado.'); this.load(); this.changed.emit(); }, error: err => this.notify.error(err) });
  }

  scmRetry(): void {
    this.platform.scmRetry(this.detail.summary.id).subscribe({ next: () => { this.notify.success('Reprocessado.'); this.load(); }, error: err => this.notify.error(err) });
  }
}
