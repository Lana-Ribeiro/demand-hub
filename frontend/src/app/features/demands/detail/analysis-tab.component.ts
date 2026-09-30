import { Component, EventEmitter, Input, OnChanges, Output, computed, inject, signal } from '@angular/core';
import { DatePipe, PercentPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { forkJoin, of, catchError } from 'rxjs';
import { AiApi } from '../../../core/api/ai-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AiSuggestion, AnalysisView, Catalog, DemandDetail } from '../../../core/models';
import { IMPACT_LABELS, URGENCY_LABELS, confidenceLabel } from '../../../core/labels';
import { NotifyService } from '../../../core/ui/notify.service';
import { ModeBadgeComponent } from '../../../shared/badges';
import { StateViewComponent } from '../../../shared/state-view.component';
import { SuggestionDecision, SuggestionListComponent } from '../../../shared/suggestion-list.component';

/** Triagem por IA: análise estruturada + sugestões para decisão do PMO. A IA nunca aprova. */
@Component({
  selector: 'dh-analysis-tab',
  standalone: true,
  imports: [DatePipe, PercentPipe, MatButtonModule, MatIconModule, MatProgressBarModule, ModeBadgeComponent, StateViewComponent, SuggestionListComponent],
  template: `
    <div class="alert info small" style="margin-bottom:12px"><mat-icon>info</mat-icon>
      <div>A IA sugere; o PMO decide. Aceitar, editar ou rejeitar cada sugestão fica registrado na auditoria com a referência da sugestão.</div></div>
    <dh-state [loading]="loading()" [empty]="!analysis()" emptyIcon="psychology" emptyText="Análise ainda não gerada.">
      @if (content(); as c) {
        <section class="card">
          <div class="row">
            <h3>Triagem da IA</h3><dh-mode-badge [mode]="analysis()?.mode"></dh-mode-badge>
            <span class="small muted">Gerada em {{ analysis()?.createdAt | date: 'dd/MM/yyyy HH:mm' }} · {{ c.provider }}</span>
            <span class="spacer"></span>
            @if (auth.has('DEMAND_TRIAGE') && !detail.summary.readOnly) {
              <button mat-stroked-button (click)="rerun()" [disabled]="running()"><mat-icon>refresh</mat-icon> Reexecutar análise</button>
            }
          </div>
          @if (running()) { <mat-progress-bar mode="indeterminate"></mat-progress-bar> }
          @if (c.failedAgents?.length) {
            <div class="alert warning small"><mat-icon>warning</mat-icon><div>Agentes que falharam (sem impacto no processo): {{ c.failedAgents.join(', ') }}</div></div>
          }
          <div class="alert info recommendation"><mat-icon>assistant_direction</mat-icon><div><strong>Recomendação de encaminhamento:</strong> {{ c.recommendation }}</div></div>

          <div class="grid grid-3">
            <div class="block">
              <div class="field-label">Tipo sugerido</div>
              <div><strong>{{ typeName(c.classification?.typeCode) }}</strong> <span class="muted small">({{ conf(c.classification?.typeConfidence) }})</span></div>
              <div class="small muted">{{ c.classification?.typeRationale }}</div>
            </div>
            <div class="block">
              <div class="field-label">Projeto sugerido</div>
              <div><strong>{{ c.classification?.projectCode || 'Nenhum com segurança' }}</strong> @if (c.classification?.projectCode) { <span class="muted small">({{ conf(c.classification?.projectConfidence) }})</span> }</div>
              <div class="small muted">{{ c.classification?.projectRationale }}</div>
            </div>
            <div class="block">
              <div class="field-label">Prioridade sugerida</div>
              <div><strong>{{ c.priority?.priority || '—' }}</strong> <span class="muted small">({{ conf(c.priority?.confidence) }})</span></div>
              <div class="small muted">{{ c.priority?.rationale }}</div>
            </div>
            <div class="block">
              <div class="field-label">Impacto</div>
              <div><strong>{{ impact(c.impact?.impactLevel) }}</strong> · Urgência declarada: {{ urgency(c.urgency) }}</div>
              <div class="small muted">{{ c.impact?.analysis }}</div>
            </div>
            <div class="block">
              <div class="field-label">Complexidade / viabilidade</div>
              <div><strong>{{ complexity(c.feasibility?.complexity) }}</strong></div>
              <div class="small muted">{{ c.feasibility?.feasibility }}</div>
            </div>
            <div class="block">
              <div class="field-label">Riscos e dependências</div>
              <ul class="small">
                @for (r of c.feasibility?.risks ?? []; track r) { <li>Risco: {{ r }}</li> }
                @for (r of c.feasibility?.dependencies ?? []; track r) { <li>Dependência: {{ r }}</li> }
                @if (!(c.feasibility?.risks?.length || c.feasibility?.dependencies?.length)) { <li class="muted">Nenhum identificado</li> }
              </ul>
            </div>
          </div>
        </section>

        <div class="grid grid-2" style="margin-top:16px">
          <section class="card">
            <h3>Informações faltantes</h3>
            @if (!c.missingInformation?.length) { <p class="muted small">Nenhuma lacuna identificada.</p> }
            <ul>@for (g of c.missingInformation ?? []; track $index) {
              <li>{{ g.question }} <span class="chip neutral small">{{ g.source === 'SYSTEM' ? 'Regra do sistema' : 'IA' }}</span></li>
            }</ul>
          </section>
          <section class="card">
            <h3>Possíveis duplicidades</h3>
            @if (!c.duplicates?.length) { <p class="muted small">Nenhuma demanda semelhante encontrada (inclui legado).</p> }
            @for (dup of c.duplicates ?? []; track dup.ref) {
              <div class="dup">
                <div class="row"><strong>{{ dup.ref }}</strong><span class="chip" [class.warning]="dup.likelyDuplicate" [class.neutral]="!dup.likelyDuplicate">{{ dup.likelyDuplicate ? 'Provável duplicidade' : 'Semelhante' }}</span>
                  <span class="spacer"></span><span class="small muted">similaridade {{ dup.score | percent }}</span></div>
                <div>{{ dup.title }}</div><div class="small muted">{{ dup.rationale }}</div>
              </div>
            }
          </section>
        </div>
      }
    </dh-state>

    <section class="card" style="margin-top:16px">
      <h3>Sugestões pendentes ({{ pending().length }})</h3>
      <dh-state [empty]="!pending().length" emptyText="Nenhuma sugestão pendente." emptyIcon="task_alt">
        <dh-suggestion-list [suggestions]="pending()" [fieldLabels]="fieldLabels()" [staff]="true"
                            [readOnly]="detail.summary.readOnly || !auth.has('DEMAND_TRIAGE')" (decide)="decide($event)"></dh-suggestion-list>
      </dh-state>
    </section>
  `,
  styles: [`.block { border: 1px solid var(--dh-border); border-radius: 8px; padding: 12px; } .recommendation { margin: 12px 0; }
            .dup { padding: 8px 0; border-bottom: 1px solid var(--dh-border); } .dup:last-child { border-bottom: 0; } ul { padding-left: 18px; margin: 4px 0; }`]
})
export class AnalysisTabComponent implements OnChanges {
  auth = inject(AuthService);
  private ai = inject(AiApi);
  private notify = inject(NotifyService);
  private catalogSignal = signal<Catalog | null>(null);

  @Input({ required: true }) detail!: DemandDetail;
  @Input() set catalog(c: Catalog | null) { this.catalogSignal.set(c); }
  @Output() changed = new EventEmitter<void>();

  loading = signal(true);
  running = signal(false);
  analysis = signal<AnalysisView | null>(null);
  suggestions = signal<AiSuggestion[]>([]);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  content = computed<any>(() => { const a = this.analysis(); return a ? JSON.parse(a.content) : null; });
  pending = computed(() => this.suggestions().filter(s => s.status === 'PENDING' && s.kind !== 'REFINEMENT_ITEM'));
  fieldLabels = computed(() => Object.fromEntries((this.catalogSignal()?.fields ?? []).map(f => [f.key, f.label])));

  ngOnChanges(): void { this.load(); }

  load(): void {
    forkJoin({
      analysis: this.ai.triage(this.detail.summary.id).pipe(catchError(() => of(null))),
      suggestions: this.ai.suggestions(this.detail.summary.id).pipe(catchError(() => of([])))
    }).subscribe(r => { this.analysis.set(r.analysis); this.suggestions.set(r.suggestions); this.loading.set(false); });
  }

  rerun(): void {
    this.running.set(true);
    this.ai.rerunTriage(this.detail.summary.id).subscribe({
      next: () => { this.running.set(false); this.notify.success('Análise atualizada.'); this.load(); },
      error: err => { this.running.set(false); this.notify.error(err); }
    });
  }

  decide(d: SuggestionDecision): void {
    this.ai.decide(this.detail.summary.id, d.suggestion.id, d.action, d.value, d.reason).subscribe({
      next: () => { this.notify.success('Decisão registrada.'); this.load(); this.changed.emit(); },
      error: err => this.notify.error(err)
    });
  }

  typeName(code?: string): string { return this.catalogSignal()?.demandTypes.find(t => t.code === code)?.name ?? code ?? '—'; }
  conf(c?: number): string { return confidenceLabel(c); }
  impact(v?: string): string { return IMPACT_LABELS[v ?? ''] ?? '—'; }
  urgency(v?: string): string { return URGENCY_LABELS[v ?? ''] ?? '—'; }
  complexity(v?: string): string { return ({ LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta' } as Record<string, string>)[v ?? ''] ?? '—'; }
}
