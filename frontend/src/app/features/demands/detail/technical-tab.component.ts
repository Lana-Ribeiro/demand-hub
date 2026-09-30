import { Component, Input, OnChanges, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { forkJoin, of, catchError } from 'rxjs';
import { AiApi, ArtifactKind } from '../../../core/api/ai-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AnalysisView, DemandDetail, SquadAgent } from '../../../core/models';
import { NotifyService } from '../../../core/ui/notify.service';
import { ModeBadgeComponent } from '../../../shared/badges';
import { MarkdownComponent } from '../../../shared/markdown.component';
import { StateViewComponent } from '../../../shared/state-view.component';
import { DocumentsPanelComponent } from '../documents-panel.component';

interface ArtifactDef { kind: ArtifactKind; label: string; icon: string; permissions: string[]; description: string; }

const TECHNICAL: ArtifactDef[] = [
  { kind: 'ARCHITECTURE', label: 'Arquitetura', icon: 'account_tree', permissions: ['ARCHITECTURE_MANAGE', 'REFINEMENT_MANAGE'],
    description: 'Architecture Agent: componentes, integrações, riscos e proposta. O arquiteto humano aprova na aba Aprovações.' },
  { kind: 'TECH_SPEC', label: 'Especificação técnica', icon: 'description', permissions: ['ARCHITECTURE_MANAGE', 'REFINEMENT_MANAGE', 'EXECUTION_MANAGE'],
    description: 'Technical Specification Agent: transforma refinamento e arquitetura em especificação implementável.' },
  { kind: 'TECH_PROMPT', label: 'Prompt técnico (Estratégia A)', icon: 'terminal', permissions: ['ARCHITECTURE_MANAGE', 'REFINEMENT_MANAGE', 'EXECUTION_MANAGE'],
    description: 'Prompt estruturado para Claude, Claude Code, GitHub Copilot e similares. Anexado à issue do repositório (GitLab/GitHub).' },
  { kind: 'SQUAD_PLAN', label: 'Squad de agentes (Estratégia B)', icon: 'groups', permissions: ['ARCHITECTURE_MANAGE', 'EXECUTION_MANAGE'],
    description: 'Plano/manifesto do squad (Orchestrator → Analyst → Architect → Tech Lead → Developer → Code Review → QA → Documentation).' },
];
const DOCUMENTATION: ArtifactDef[] = [
  { kind: 'TECHNICAL_DOC', label: 'Documentação técnica', icon: 'engineering', permissions: ['DEMAND_TRANSITION', 'EXECUTION_MANAGE'],
    description: 'Technical Documentation Agent: arquitetura, componentes, integrações e operação do que foi entregue.' },
  { kind: 'CLIENT_DOC', label: 'Documentação para o cliente', icon: 'menu_book', permissions: ['DEMAND_TRANSITION', 'EXECUTION_MANAGE'],
    description: 'Client Documentation Agent: o que foi entregue, como usar e benefícios, em linguagem de negócio.' },
];

/** Artefatos gerados com apoio de agentes — sempre revisáveis e editáveis por humanos. */
@Component({
  selector: 'dh-technical-tab',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatButtonToggleModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, ModeBadgeComponent, MarkdownComponent, StateViewComponent, DocumentsPanelComponent],
  template: `
    <mat-button-toggle-group [value]="selected()" (change)="select($event.value)" class="kinds" aria-label="Artefato">
      @for (d of defs(); track d.kind) {
        <mat-button-toggle [value]="d.kind"><mat-icon>{{ d.icon }}</mat-icon> {{ d.label }}</mat-button-toggle>
      }
    </mat-button-toggle-group>

    @if (current(); as def) {
      <section class="card" style="margin-top:12px">
        <div class="row">
          <h3>{{ def.label }}</h3>
          @if (artifact(); as a) {
            <dh-mode-badge [mode]="a.mode"></dh-mode-badge>
            @if (a.edited) { <span class="chip info">Editado</span> } @else { <span class="chip neutral">Gerado por agente</span> }
            <span class="small muted">{{ a.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
          }
          <span class="spacer"></span>
          @if (artifact() && markdown() && !editing()) {
            <button mat-button (click)="copy()"><mat-icon>content_copy</mat-icon> Copiar</button>
          }
          @if (canGenerate(def) && artifact() && markdown() && !editing()) {
            <button mat-stroked-button (click)="startEdit()"><mat-icon>edit</mat-icon> Editar</button>
          }
          @if (canGenerate(def)) {
            <button mat-flat-button color="primary" (click)="generate(def)" [disabled]="generating()"><mat-icon>auto_awesome</mat-icon> {{ artifact() ? 'Gerar novamente' : 'Gerar' }}</button>
          }
        </div>
        <p class="small muted">{{ def.description }}</p>
        @if (generating()) { <mat-progress-bar mode="indeterminate"></mat-progress-bar> }
        <dh-state [loading]="loading()" [empty]="!artifact()" emptyIcon="auto_awesome" [emptyText]="canGenerate(def) ? 'Ainda não gerado. Use “Gerar”.' : 'Ainda não disponível.'">
          @if (editing()) {
            <mat-form-field class="full"><mat-label>Conteúdo (Markdown)</mat-label><textarea matInput rows="22" [formControl]="editText" class="mono"></textarea></mat-form-field>
            <mat-form-field class="full"><mat-label>Motivo da edição *</mat-label><input matInput [formControl]="editReason"></mat-form-field>
            <div class="row"><span class="spacer"></span><button mat-button (click)="editing.set(false)">Cancelar</button><button mat-flat-button color="primary" (click)="saveEdit()">Salvar</button></div>
          } @else if (markdown()) {
            <dh-markdown [text]="markdown()"></dh-markdown>
          } @else if (architecture()) { @if (architecture(); as arch) {
            <dh-markdown [text]="arch.proposal"></dh-markdown>
            <div class="grid grid-2">
              @for (sec of archSections; track sec.key) {
                <div><div class="field-label">{{ sec.label }}</div><ul>@for (x of arch[sec.key] ?? []; track x) { <li>{{ x }}</li> } @empty { <li class="muted">—</li> }</ul></div>
              }
            </div>
          } } @else if (squad()) { @if (squad(); as plan) {
            <div class="alert warning small"><mat-icon>info</mat-icon><div>{{ plan.runnerNote }}</div></div>
            <p><strong>Sequência:</strong> {{ plan.sequence }}</p>
            <div class="grid grid-2">
              @for (ag of plan.agents; track ag.name) {
                <div class="agent card">
                  <h4>{{ ag.name }}</h4><p class="small">{{ ag.responsibility }}</p>
                  <div class="small"><strong>Ferramentas:</strong> {{ ag.allowedTools.join(', ') }}</div>
                  <div class="small"><strong>Entradas:</strong> {{ ag.inputs.join(', ') }} · <strong>Saídas:</strong> {{ ag.outputs.join(', ') }}</div>
                  <div class="small"><strong>Conclusão:</strong> {{ ag.doneCriteria.join('; ') }}</div>
                </div>
              }
            </div>
          } }
        </dh-state>
      </section>
    }

    @if (mode === 'documentation') {
      <section class="card">
        <h3>Arquivos de documentação</h3>
        <dh-documents-panel [demandId]="detail.summary.id"></dh-documents-panel>
      </section>
    }
  `,
  styles: [`.kinds { flex-wrap: wrap; } .agent h4 { margin: 0 0 4px; } ul { padding-left: 18px; margin: 4px 0; }`]
})
export class TechnicalTabComponent implements OnChanges {
  private ai = inject(AiApi);
  private auth = inject(AuthService);
  private notify = inject(NotifyService);
  private clipboard = inject(Clipboard);

  @Input({ required: true }) detail!: DemandDetail;
  @Input() mode: 'technical' | 'documentation' = 'technical';

  archSections = [
    { key: 'components', label: 'Componentes' }, { key: 'integrations', label: 'Integrações' },
    { key: 'risks', label: 'Riscos' }, { key: 'impacts', label: 'Impactos' }, { key: 'suggestedDecisions', label: 'Decisões sugeridas' }
  ] as const;

  selected = signal<ArtifactKind>('ARCHITECTURE');
  artifact = signal<AnalysisView | null>(null);
  loading = signal(false);
  generating = signal(false);
  editing = signal(false);
  editText = new FormControl('', Validators.required);
  editReason = new FormControl('', Validators.required);

  defs = computed(() => this.mode === 'technical' ? TECHNICAL : DOCUMENTATION);
  current = computed(() => this.defs().find(d => d.kind === this.selected()) ?? null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private parsed = computed<any>(() => { const a = this.artifact(); try { return a ? JSON.parse(a.content) : null; } catch { return null; } });
  markdown = computed<string | null>(() => this.parsed()?.markdown ?? null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  architecture = computed<any>(() => this.selected() === 'ARCHITECTURE' ? this.parsed() : null);
  squad = computed<{ runnerNote: string; sequence: string; agents: SquadAgent[] } | null>(() => this.selected() === 'SQUAD_PLAN' ? this.parsed() : null);

  ngOnChanges(): void {
    if (!this.defs().some(d => d.kind === this.selected())) this.selected.set(this.defs()[0].kind);
    this.load();
  }

  select(kind: ArtifactKind): void {
    this.selected.set(kind);
    this.editing.set(false);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.ai.artifact(this.detail.summary.id, this.selected()).pipe(catchError(() => of(null))).subscribe(a => { this.artifact.set(a); this.loading.set(false); });
  }

  canGenerate(def: ArtifactDef): boolean {
    return this.auth.hasAny(...def.permissions) && !this.detail.summary.readOnly && this.detail.summary.lifecycleState !== 'DRAFT';
  }

  generate(def: ArtifactDef): void {
    this.generating.set(true);
    this.ai.generate(this.detail.summary.id, def.kind).subscribe({
      next: a => { this.artifact.set(a); this.generating.set(false); this.notify.success(`${def.label} gerado(a) para revisão.`); },
      error: err => { this.generating.set(false); this.notify.error(err); }
    });
  }

  startEdit(): void {
    this.editText.setValue(this.markdown() ?? '');
    this.editReason.setValue('');
    this.editing.set(true);
  }

  saveEdit(): void {
    const a = this.artifact();
    if (!a || this.editText.invalid || this.editReason.invalid) { this.editReason.markAsTouched(); return; }
    const content = JSON.stringify({ ...this.parsed(), markdown: this.editText.value });
    this.ai.editAnalysis(this.detail.summary.id, a.id, content, this.editReason.value ?? '').subscribe({
      next: updated => { this.artifact.set(updated); this.editing.set(false); this.notify.success('Conteúdo atualizado.'); },
      error: err => this.notify.error(err)
    });
  }

  copy(): void {
    this.clipboard.copy(this.markdown() ?? '');
    this.notify.success('Copiado para a área de transferência.');
  }
}
