import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { forkJoin, of, catchError } from 'rxjs';
import { AiApi } from '../../../core/api/ai-api.service';
import { DemandApi } from '../../../core/api/demand-api.service';
import { AnalysisView, ApprovalView, DemandDetail } from '../../../core/models';
import { IMPACT_LABELS, ROLE_LABELS, URGENCY_LABELS } from '../../../core/labels';
import { NotifyService } from '../../../core/ui/notify.service';
import { ModeBadgeComponent, PriorityBadgeComponent } from '../../../shared/badges';
import { StateViewComponent } from '../../../shared/state-view.component';

interface Summary {
  executiveSummary?: string; objective?: string; problem?: string; expectedBenefit?: string; scope?: string;
  impact?: string; deadline?: string; budget?: string; risks?: string[]; dependencies?: string[];
}

/** Visão resumida (gerada pela IA, editável pelo PMO) com os dados-chave da decisão. */
@Component({
  selector: 'dh-summary-tab',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, CurrencyPipe, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    PriorityBadgeComponent, ModeBadgeComponent, StateViewComponent],
  template: `
    <div class="grid layout">
      <section class="card">
        <div class="row">
          <h3>Resumo executivo</h3>
          @if (analysis(); as a) {
            <dh-mode-badge [mode]="a.mode"></dh-mode-badge>
            @if (a.edited) { <span class="chip info">Editado pelo PMO</span> } @else { <span class="chip neutral">Gerado pela IA</span> }
          }
          <span class="spacer"></span>
          @if (canEdit && analysis() && !editing()) { <button mat-stroked-button (click)="startEdit()"><mat-icon>edit</mat-icon> Editar resumo</button> }
        </div>
        <dh-state [loading]="loading()" [empty]="!summary()" emptyIcon="summarize"
                  [emptyText]="detail.summary.lifecycleState === 'DRAFT' ? 'O resumo é gerado pela IA após o envio.' : 'Resumo ainda não disponível.'">
          @if (editing()) {
            <form [formGroup]="form" (ngSubmit)="saveEdit()">
              @for (f of editFields; track f.key) {
                <mat-form-field class="full"><mat-label>{{ f.label }}</mat-label><textarea matInput [formControlName]="f.key" rows="2"></textarea></mat-form-field>
              }
              <mat-form-field class="full"><mat-label>Riscos (um por linha)</mat-label><textarea matInput formControlName="risks" rows="3"></textarea></mat-form-field>
              <mat-form-field class="full"><mat-label>Dependências (uma por linha)</mat-label><textarea matInput formControlName="dependencies" rows="3"></textarea></mat-form-field>
              <mat-form-field class="full"><mat-label>Motivo da edição *</mat-label><input matInput formControlName="reason"></mat-form-field>
              <div class="row"><span class="spacer"></span><button mat-button type="button" (click)="editing.set(false)">Cancelar</button><button mat-flat-button color="primary">Salvar</button></div>
            </form>
          } @else { @if (summary(); as s) {
            <p class="lead">{{ s.executiveSummary }}</p>
            <div class="grid grid-2">
              <div><div class="field-label">Objetivo</div><div class="field-value">{{ s.objective || '—' }}</div></div>
              <div><div class="field-label">Problema</div><div class="field-value">{{ s.problem || '—' }}</div></div>
              <div><div class="field-label">Benefício esperado</div><div class="field-value">{{ s.expectedBenefit || '—' }}</div></div>
              <div><div class="field-label">Impacto</div><div class="field-value">{{ s.impact || '—' }}</div></div>
              <div><div class="field-label">Prazo</div><div class="field-value">{{ s.deadline || '—' }}</div></div>
              <div><div class="field-label">Orçamento</div><div class="field-value">{{ s.budget || '—' }}</div></div>
              <div><div class="field-label">Riscos</div>@if (s.risks?.length) { <ul>@for (r of s.risks; track r) { <li>{{ r }}</li> }</ul> } @else { <div class="field-value empty">Nenhum registrado</div> }</div>
              <div><div class="field-label">Dependências</div>@if (s.dependencies?.length) { <ul>@for (r of s.dependencies; track r) { <li>{{ r }}</li> }</ul> } @else { <div class="field-value empty">Nenhuma registrada</div> }</div>
            </div>
          } }
        </dh-state>
      </section>

      <aside>
        <section class="card">
          <h3>Dados-chave</h3>
          <div class="kv"><span class="field-label">Prioridade</span><dh-priority-badge [priority]="detail.summary.priority"></dh-priority-badge></div>
          <div class="kv"><span class="field-label">Impacto declarado</span><span>{{ impact() }}</span></div>
          <div class="kv"><span class="field-label">Urgência</span><span>{{ urgency() }}</span></div>
          <div class="kv"><span class="field-label">Prazo desejado</span><span>{{ detail.fields['desiredDate'] ? (detail.fields['desiredDate'] | date: 'dd/MM/yyyy') : '—' }}</span></div>
          <div class="kv"><span class="field-label">Orçamento estimado</span>
            <span>{{ detail.fields['hasBudgetImpact'] === 'true' && detail.fields['estimatedBudget'] ? (+detail.fields['estimatedBudget']! | currency: 'BRL') : 'Sem impacto informado' }}</span></div>
          <div class="kv"><span class="field-label">Exigência regulatória</span><span>{{ detail.fields['regulatoryRequirement'] === 'true' ? 'Sim' : 'Não' }}</span></div>
        </section>
        <section class="card">
          <h3>Aprovações</h3>
          @if (!approvals().length) { <p class="muted small">Nenhuma aprovação exigida até o momento.</p> }
          @for (a of approvals(); track a.id) {
            <div class="kv">
              <span>{{ role(a.approverRole) }}</span>
              <span class="chip" [class.success]="a.status === 'APPROVED'" [class.warning]="a.status === 'PENDING'" [class.danger]="a.status === 'REJECTED'" [class.neutral]="a.status === 'CANCELLED'">
                {{ statusLabel(a.status) }}
              </span>
            </div>
          }
        </section>
      </aside>
    </div>
  `,
  styles: [`
    .layout { grid-template-columns: minmax(0, 1fr) 340px; } @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
    .lead { font-size: 15px; line-height: 1.55; }
    .kv { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--dh-border); }
    .kv:last-child { border-bottom: 0; }
    ul { margin: 4px 0; padding-left: 18px; }
  `]
})
export class SummaryTabComponent implements OnChanges {
  private ai = inject(AiApi);
  private demands = inject(DemandApi);
  private notify = inject(NotifyService);
  private fb = inject(FormBuilder);

  @Input({ required: true }) detail!: DemandDetail;
  @Input() canEdit = false;
  @Output() changed = new EventEmitter<void>();

  loading = signal(true);
  analysis = signal<AnalysisView | null>(null);
  summary = signal<Summary | null>(null);
  approvals = signal<ApprovalView[]>([]);
  editing = signal(false);

  editFields = [
    { key: 'executiveSummary', label: 'Resumo executivo' }, { key: 'objective', label: 'Objetivo' }, { key: 'problem', label: 'Problema' },
    { key: 'expectedBenefit', label: 'Benefício esperado' }, { key: 'impact', label: 'Impacto' }, { key: 'deadline', label: 'Prazo' },
    { key: 'budget', label: 'Orçamento' }
  ];
  form = this.fb.group({
    executiveSummary: [''], objective: [''], problem: [''], expectedBenefit: [''], impact: [''], deadline: [''], budget: [''],
    risks: [''], dependencies: [''], reason: ['', Validators.required]
  });

  ngOnChanges(): void {
    this.loading.set(true);
    forkJoin({
      analysis: this.ai.triage(this.detail.summary.id).pipe(catchError(() => of(null))),
      approvals: this.demands.approvals(this.detail.summary.id).pipe(catchError(() => of([])))
    }).subscribe(r => {
      this.analysis.set(r.analysis);
      this.summary.set(r.analysis ? (JSON.parse(r.analysis.content).summary ?? null) : null);
      this.approvals.set(r.approvals);
      this.loading.set(false);
    });
  }

  impact(): string { return IMPACT_LABELS[this.detail.fields['impactLevel'] ?? ''] ?? '—'; }
  urgency(): string { return URGENCY_LABELS[this.detail.fields['urgency'] ?? ''] ?? '—'; }
  role(r: string): string { return ROLE_LABELS[r] ?? r; }
  statusLabel(s: string): string { return ({ PENDING: 'Pendente', APPROVED: 'Aprovado', REJECTED: 'Rejeitado', CANCELLED: 'Cancelado' } as Record<string, string>)[s] ?? s; }

  startEdit(): void {
    const s = this.summary() ?? {};
    this.form.reset({
      executiveSummary: s.executiveSummary ?? '', objective: s.objective ?? '', problem: s.problem ?? '', expectedBenefit: s.expectedBenefit ?? '',
      impact: s.impact ?? '', deadline: s.deadline ?? '', budget: s.budget ?? '',
      risks: (s.risks ?? []).join('\n'), dependencies: (s.dependencies ?? []).join('\n'), reason: ''
    });
    this.editing.set(true);
  }

  saveEdit(): void {
    this.form.markAllAsTouched();
    const a = this.analysis();
    if (this.form.invalid || !a) return;
    const v = this.form.getRawValue();
    const content = JSON.parse(a.content);
    content.summary = {
      ...content.summary, executiveSummary: v.executiveSummary, objective: v.objective, problem: v.problem, expectedBenefit: v.expectedBenefit,
      impact: v.impact, deadline: v.deadline, budget: v.budget,
      risks: (v.risks ?? '').split('\n').map(x => x.trim()).filter(Boolean),
      dependencies: (v.dependencies ?? '').split('\n').map(x => x.trim()).filter(Boolean)
    };
    this.ai.editAnalysis(this.detail.summary.id, a.id, JSON.stringify(content), v.reason ?? '').subscribe({
      next: updated => {
        this.analysis.set(updated);
        this.summary.set(JSON.parse(updated.content).summary);
        this.editing.set(false);
        this.notify.success('Resumo atualizado.');
      },
      error: err => this.notify.error(err)
    });
  }
}
