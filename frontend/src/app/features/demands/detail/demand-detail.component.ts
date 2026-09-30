import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { DemandApi } from '../../../core/api/demand-api.service';
import { PlatformApi } from '../../../core/api/platform-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AvailableTransition, Catalog, DemandDetail } from '../../../core/models';
import { LIFECYCLE_LABELS } from '../../../core/labels';
import { NotifyService } from '../../../core/ui/notify.service';
import { ModeBadgeComponent, PriorityBadgeComponent, ProjectBadgeComponent, StageChipComponent } from '../../../shared/badges';
import { StateViewComponent } from '../../../shared/state-view.component';
import { ReasonDialogComponent, ReasonDialogData } from '../../../shared/reason-dialog.component';
import { ClassificationDialogComponent, ClassificationData, ClassificationResult } from './classification-dialog.component';
import { SummaryTabComponent } from './summary-tab.component';
import { FullTabComponent } from './full-tab.component';
import { AnalysisTabComponent } from './analysis-tab.component';
import { InteractionsTabComponent } from './interactions-tab.component';
import { ApprovalsTabComponent } from './approvals-tab.component';
import { RefinementTabComponent } from './refinement-tab.component';
import { TechnicalTabComponent } from './technical-tab.component';
import { ExecutionTabComponent } from './execution-tab.component';
import { HistoryTabComponent } from './history-tab.component';

@Component({
  selector: 'dh-demand-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, MatButtonModule, MatIconModule, MatTabsModule, MatTooltipModule, MatMenuModule, StateViewComponent,
    StageChipComponent, PriorityBadgeComponent, ProjectBadgeComponent, ModeBadgeComponent, SummaryTabComponent, FullTabComponent,
    AnalysisTabComponent, InteractionsTabComponent, ApprovalsTabComponent, RefinementTabComponent, TechnicalTabComponent,
    ExecutionTabComponent, HistoryTabComponent],
  template: `
    <div class="page">
      <dh-state [loading]="loading()" [error]="error()" (retry)="load()">
        @if (detail(); as d) {
          <header class="card head">
            <div class="row">
              <span class="mono muted">{{ d.summary.protocol || 'Rascunho' }}</span>
              <dh-stage-chip [stage]="d.summary.stage" [lifecycle]="d.summary.lifecycleState"></dh-stage-chip>
              <span class="chip neutral">{{ lifecycle(d) }}</span>
              @if (d.summary.readOnly) { <span class="chip neutral"><mat-icon style="font-size:14px;width:14px;height:14px">lock</mat-icon> Somente leitura</span> }
              @if (d.summary.legacy) { <span class="chip warning">Legado importado</span> }
            </div>
            <h1>{{ d.summary.title || '(sem título)' }}</h1>
            <div class="row meta">
              <dh-project-badge [project]="d.summary.project"></dh-project-badge>
              <span class="sep">·</span>
              <span>{{ d.summary.type?.name || 'Tipo não definido' }}</span>
              <span class="sep">·</span>
              <dh-priority-badge [priority]="d.summary.priority"></dh-priority-badge>
              <span class="sep">·</span>
              <span class="muted">Solicitante: {{ d.summary.requester?.fullName || '—' }}</span>
              <span class="sep">·</span>
              <span class="muted">Responsável: {{ d.summary.owner?.fullName || '—' }}</span>
              @if (d.summary.jira) {
                <span class="sep">·</span>
                <span class="row">
                  <mat-icon class="small-icon">confirmation_number</mat-icon>
                  @if (d.summary.jira.url) { <a [href]="d.summary.jira.url" target="_blank" rel="noopener">Jira {{ d.summary.jira.key }}</a> }
                  @else { <span>Jira {{ d.summary.jira.key || '(pendente)' }}</span> }
                  <dh-mode-badge [mode]="d.summary.jira.mode"></dh-mode-badge>
                  @if (d.summary.jira.syncStatus === 'FAILED') { <span class="chip danger" [matTooltip]="d.summary.jira.lastError || ''">Falha de sincronização</span> }
                </span>
              }
            </div>

            @if (d.summary.stage?.category === 'INTAKE') {
              <div class="alert info"><mat-icon>hourglass_top</mat-icon><div>A IA está analisando a demanda. Em instantes ela seguirá para a triagem do PMO.
                <button mat-button (click)="load()">Atualizar</button></div></div>
            }
            @if (d.summary.lifecycleState === 'ON_HOLD' && isRequester()) {
              <div class="alert warning"><mat-icon>priority_high</mat-icon><div>O PMO solicitou informações. Responda na aba <a href="#" (click)="goTab($event, 'interactions')">Pendências e comentários</a>.</div></div>
            }

            <div class="row actions">
              @for (t of transitions(); track t.action) {
                <button mat-flat-button [color]="t.action === 'REJECT' || t.action === 'CANCEL' ? 'warn' : 'primary'"
                        [class.blocked]="t.blockedBy.length" [matTooltip]="t.blockedBy.join(' ')"
                        (click)="runTransition(t)">
                  @if (t.blockedBy.length) { <mat-icon>lock</mat-icon> } {{ t.label }}
                </button>
              }
              @if (canClassify()) {
                <button mat-stroked-button (click)="classify()"><mat-icon>category</mat-icon> Classificar</button>
                <button mat-stroked-button (click)="requestInfo()"><mat-icon>contact_support</mat-icon> Solicitar informação</button>
              }
              <span class="spacer"></span>
              <span class="small muted">Enviada {{ d.summary.submittedAt | date: 'dd/MM/yyyy HH:mm' }} · Workflow: {{ d.workflowName || '—' }}</span>
            </div>
            @if (blockedHint(); as hint) {
              <div class="alert warning small"><mat-icon>lock</mat-icon><div><strong>Avanço bloqueado:</strong> {{ hint }}</div></div>
            }
          </header>

          <mat-tab-group [selectedIndex]="tabIndex()" (selectedIndexChange)="tabIndex.set($event)" animationDuration="0ms" class="tabs">
            <mat-tab label="Resumo"><div class="tab"><dh-summary-tab [detail]="d" [canEdit]="auth.has('DEMAND_TRIAGE')" (changed)="load()"></dh-summary-tab></div></mat-tab>
            <mat-tab label="Demanda completa"><div class="tab"><dh-full-tab [detail]="d" [catalog]="catalog()"></dh-full-tab></div></mat-tab>
            @if (isInternal()) {
              <mat-tab label="Análise da IA"><div class="tab"><dh-analysis-tab [detail]="d" [catalog]="catalog()" (changed)="load()"></dh-analysis-tab></div></mat-tab>
            }
            <mat-tab label="Pendências e comentários"><div class="tab"><dh-interactions-tab [detail]="d" (changed)="load()"></dh-interactions-tab></div></mat-tab>
            <mat-tab label="Aprovações"><div class="tab"><dh-approvals-tab [detail]="d" (changed)="load()"></dh-approvals-tab></div></mat-tab>
            @if (isInternal() && showTechnical()) {
              <mat-tab label="Refinamento"><div class="tab"><dh-refinement-tab [detail]="d" (changed)="load()"></dh-refinement-tab></div></mat-tab>
              <mat-tab label="Arquitetura e prompt"><div class="tab"><dh-technical-tab [detail]="d" mode="technical"></dh-technical-tab></div></mat-tab>
            }
            @if (isInternal()) {
              <mat-tab label="Execução"><div class="tab"><dh-execution-tab [detail]="d" (changed)="load()"></dh-execution-tab></div></mat-tab>
            }
            <mat-tab label="Documentação"><div class="tab"><dh-technical-tab [detail]="d" mode="documentation"></dh-technical-tab></div></mat-tab>
            <mat-tab label="Histórico"><div class="tab"><dh-history-tab [detail]="d"></dh-history-tab></div></mat-tab>
          </mat-tab-group>
        }
      </dh-state>
    </div>
  `,
  styles: [`
    .head h1 { margin: 8px 0; font-size: 22px; }
    .meta { font-size: 13px; } .sep { color: var(--dh-border); } .small-icon { font-size: 16px; width: 16px; height: 16px; }
    .actions { margin-top: 14px; } .blocked { opacity: .75; }
    .head .alert { margin-top: 12px; }
    .tabs { margin-top: 16px; } .tab { padding: 16px 2px; }
  `]
})
export class DemandDetailComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(DemandApi);
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);
  private dialog = inject(MatDialog);

  @Input() id!: string;

  detail = signal<DemandDetail | null>(null);
  catalog = signal<Catalog | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  tabIndex = signal(0);
  private intakePolls = 0;

  // REQUEST_INFO é disparada pelo botão dedicado, que registra a pergunta antes da transição.
  transitions = computed(() => (this.detail()?.availableTransitions ?? []).filter(t => t.action !== 'REQUEST_INFO'));
  isInternal = computed(() => this.auth.has('DEMAND_VIEW_ALL'));
  isRequester = computed(() => this.detail()?.summary.requester?.id === this.auth.user()?.id);
  showTechnical = computed(() => !!this.detail()?.summary.type?.technical);
  canClassify = computed(() => this.auth.has('DEMAND_TRIAGE') && !this.detail()?.summary.readOnly && this.detail()?.summary.lifecycleState !== 'DRAFT');
  blockedHint = computed(() => {
    const forward = this.transitions().find(t => t.forward && t.blockedBy.length);
    return forward ? forward.blockedBy.join(' ') : null;
  });

  ngOnInit(): void {
    this.platform.catalog().subscribe(c => this.catalog.set(c));
    this.load();
  }

  load(): void {
    this.error.set(null);
    this.api.get(this.id).subscribe({
      next: d => {
        this.detail.set(d);
        this.loading.set(false);
        // A análise da IA roda de forma assíncrona após o envio: recarrega até a demanda sair da etapa de análise.
        if (d.summary.stage?.category === 'INTAKE' && this.intakePolls++ < 15) setTimeout(() => this.load(), 2000);
      },
      error: err => { this.error.set(NotifyService.message(err, 'Demanda não encontrada.')); this.loading.set(false); }
    });
  }

  lifecycle(d: DemandDetail): string { return LIFECYCLE_LABELS[d.summary.lifecycleState] ?? d.summary.lifecycleState; }

  goTab(e: Event, _name: string): void {
    e.preventDefault();
    this.tabIndex.set(this.isInternal() ? 3 : 2);
  }

  runTransition(t: AvailableTransition): void {
    if (t.blockedBy.length) {
      this.notify.error(null, 'Avanço bloqueado: ' + t.blockedBy.join(' '));
      return;
    }
    const perform = (reason?: string) => this.api.transition(this.id, t.action, reason).subscribe({
      next: d => { this.detail.set(d); this.notify.success(`${t.label}: concluído.`); },
      error: err => this.notify.error(err)
    });
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '520px',
      data: {
        title: t.label, message: `A demanda seguirá para: ${t.toStageName}.`, required: t.requiresReason,
        label: t.requiresReason ? 'Motivo *' : 'Observação (opcional)', confirmText: t.label,
        danger: t.action === 'REJECT' || t.action === 'CANCEL'
      }
    }).afterClosed().subscribe(reason => { if (reason !== undefined) perform(reason || undefined); });
  }

  async classify(): Promise<void> {
    const d = this.detail();
    const catalog = this.catalog();
    if (!d || !catalog) return;
    const users = await firstValueFrom(this.platform.assignableUsers()).catch(() => []);
    this.dialog.open<ClassificationDialogComponent, ClassificationData, ClassificationResult>(ClassificationDialogComponent, {
      width: '560px', data: { detail: d, catalog, users }
    }).afterClosed().subscribe(async result => {
      if (!result) return;
      try {
        if (Object.keys(result.fields).length) await firstValueFrom(this.api.staffUpdate(this.id, result.fields, result.reason));
        if (result.ownerChanged) await firstValueFrom(this.api.assignOwner(this.id, result.ownerId ?? null));
        this.notify.success('Classificação atualizada.');
        this.load();
      } catch (err) {
        this.notify.error(err);
      }
    });
  }

  requestInfo(): void {
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '560px', data: { title: 'Solicitar informação ao solicitante', label: 'Pergunta *', required: true, confirmText: 'Enviar solicitação',
        message: 'Na triagem, a demanda ficará aguardando a resposta e voltará automaticamente para o PMO.' }
    }).afterClosed().subscribe(question => {
      if (!question) return;
      this.api.requestInformation(this.id, question).subscribe({
        next: () => { this.notify.success('Solicitação enviada ao solicitante.'); this.load(); },
        error: err => this.notify.error(err)
      });
    });
  }
}
