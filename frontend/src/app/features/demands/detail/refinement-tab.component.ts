import { Component, EventEmitter, Input, OnChanges, Output, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { forkJoin } from 'rxjs';
import { PlatformApi } from '../../../core/api/platform-api.service';
import { AiApi } from '../../../core/api/ai-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AiSuggestion, DemandDetail, RefinementItem, RefinementSnapshot } from '../../../core/models';
import { DECISION_TYPE_LABELS, ITEM_TYPE_LABELS } from '../../../core/labels';
import { NotifyService } from '../../../core/ui/notify.service';
import { StateViewComponent } from '../../../shared/state-view.component';
import { SuggestionDecision, SuggestionListComponent } from '../../../shared/suggestion-list.component';

/** Refinamento: reuniões, decisões, requisitos, critérios, riscos, dúvidas. Refinement Agent organiza notas de reunião. */
@Component({
  selector: 'dh-refinement-tab',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatExpansionModule, MatCheckboxModule, StateViewComponent, SuggestionListComponent],
  template: `
    @if (gate(); as g) { <div class="alert warning small" style="margin-bottom:12px"><mat-icon>rule</mat-icon><div>{{ g }}</div></div> }
    <dh-state [loading]="loading()">
      <div class="grid layout">
        <div>
          <section class="card">
            <h3>Requisitos, critérios e pendências</h3>
            @for (type of itemTypes; track type) {
              @if (itemsOf(type).length) {
                <div class="group-title">{{ itemLabel(type) }} ({{ itemsOf(type).length }})</div>
                @for (i of itemsOf(type); track i.id) {
                  <div class="item">
                    <mat-checkbox [checked]="i.status === 'RESOLVED'" [disabled]="!canManage()" (change)="toggle(i)" [attr.aria-label]="'Marcar como resolvido: ' + i.description"></mat-checkbox>
                    <span [class.resolved]="i.status === 'RESOLVED'">{{ i.description }}</span>
                    @if (i.origin === 'AI_ACCEPTED') { <span class="chip neutral small">IA (aceito)</span> }
                  </div>
                }
              }
            }
            @if (!snapshot()?.items?.length) { <p class="muted small">Nenhum item registrado. O avanço exige ao menos um requisito funcional e um critério de aceite.</p> }
            @if (canManage()) {
              <form [formGroup]="itemForm" (ngSubmit)="addItem()" class="row add">
                <mat-form-field class="type"><mat-label>Tipo</mat-label>
                  <mat-select formControlName="type">@for (t of itemTypes; track t) { <mat-option [value]="t">{{ itemLabel(t) }}</mat-option> }</mat-select></mat-form-field>
                <mat-form-field class="desc"><mat-label>Descrição</mat-label><input matInput formControlName="description"></mat-form-field>
                <button mat-flat-button color="primary">Adicionar</button>
              </form>
            }
          </section>

          <section class="card">
            <h3>Decisões</h3>
            @for (d of snapshot()?.decisions ?? []; track d.id) {
              <div class="item"><span class="chip">{{ decisionLabel(d.type) }}</span><div><div>{{ d.description }}</div>
                <div class="small muted">{{ d.decidedBy }} · {{ d.createdAt | date: 'dd/MM/yyyy' }}@if (d.rationale) { · {{ d.rationale }} }</div></div></div>
            }
            @if (!snapshot()?.decisions?.length) { <p class="muted small">Nenhuma decisão registrada.</p> }
            @if (canManage()) {
              <form [formGroup]="decisionForm" (ngSubmit)="addDecision()" class="row add">
                <mat-form-field class="type"><mat-label>Tipo</mat-label>
                  <mat-select formControlName="type">@for (t of decisionTypes; track t) { <mat-option [value]="t">{{ decisionLabel(t) }}</mat-option> }</mat-select></mat-form-field>
                <mat-form-field class="desc"><mat-label>Decisão</mat-label><input matInput formControlName="description"></mat-form-field>
                <button mat-flat-button color="primary">Registrar</button>
              </form>
            }
          </section>
        </div>

        <div>
          <section class="card">
            <h3>Reuniões de refinamento</h3>
            @if (canManage()) {
              <mat-expansion-panel class="new-meeting">
                <mat-expansion-panel-header><mat-panel-title>Registrar reunião</mat-panel-title></mat-expansion-panel-header>
                <form [formGroup]="meetingForm" (ngSubmit)="addMeeting()">
                  <mat-form-field class="full"><mat-label>Título</mat-label><input matInput formControlName="title"></mat-form-field>
                  <mat-form-field class="full"><mat-label>Participantes</mat-label><input matInput formControlName="participants"></mat-form-field>
                  <mat-form-field class="full"><mat-label>Notas / ata</mat-label><textarea matInput rows="6" formControlName="notes"
                    placeholder="- O sistema deve...&#10;- Critério de aceite: ...&#10;- Ficou decidido...&#10;- Dúvida: ...?"></textarea></mat-form-field>
                  <div class="row"><span class="spacer"></span><button mat-flat-button color="primary">Salvar reunião</button></div>
                </form>
              </mat-expansion-panel>
            }
            @for (m of snapshot()?.meetings ?? []; track m.id) {
              <div class="meeting">
                <div class="row"><strong>{{ m.title }}</strong><span class="spacer"></span><span class="small muted">{{ m.heldAt | date: 'dd/MM/yyyy HH:mm' }}</span></div>
                @if (m.participants) { <div class="small muted">Participantes: {{ m.participants }}</div> }
                @if (m.notes) { <div class="pre notes">{{ m.notes }}</div> }
                @if (canManage() && m.notes) {
                  <button mat-stroked-button (click)="organize(m.id)" [disabled]="organizing()"><mat-icon>auto_awesome</mat-icon> Organizar com IA (Refinement Agent)</button>
                }
              </div>
            }
            @if (!snapshot()?.meetings?.length) { <p class="muted small">Nenhuma reunião registrada.</p> }
          </section>

          @if (pendingProposals().length) {
            <section class="card">
              <h3>Propostas do Refinement Agent</h3>
              <dh-suggestion-list [suggestions]="pendingProposals()" [staff]="true" [readOnly]="!canManage()" (decide)="decide($event)"></dh-suggestion-list>
            </section>
          }
        </div>
      </div>
    </dh-state>
  `,
  styles: [`.layout { grid-template-columns: 1fr 1fr; } @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
            .group-title { font-weight: 600; margin: 12px 0 4px; font-size: 13px; }
            .item { display: flex; gap: 8px; align-items: center; padding: 4px 0; } .resolved { text-decoration: line-through; color: var(--dh-muted); }
            .add { margin-top: 12px; align-items: flex-start; } .add .type { width: 200px; } .add .desc { flex: 1; min-width: 200px; }
            .meeting { border-top: 1px solid var(--dh-border); padding: 12px 0; } .notes { background: #f7f8fc; padding: 8px; border-radius: 6px; margin: 8px 0; }
            .new-meeting { margin-bottom: 12px; box-shadow: none !important; border: 1px solid var(--dh-border); }`]
})
export class RefinementTabComponent implements OnChanges {
  private platform = inject(PlatformApi);
  private ai = inject(AiApi);
  private auth = inject(AuthService);
  private notify = inject(NotifyService);
  private fb = inject(FormBuilder);

  @Input({ required: true }) detail!: DemandDetail;
  @Output() changed = new EventEmitter<void>();

  itemTypes = Object.keys(ITEM_TYPE_LABELS);
  decisionTypes = Object.keys(DECISION_TYPE_LABELS);
  loading = signal(true);
  organizing = signal(false);
  snapshot = signal<RefinementSnapshot | null>(null);
  suggestions = signal<AiSuggestion[]>([]);
  pendingProposals = computed(() => this.suggestions().filter(s => s.kind === 'REFINEMENT_ITEM' && s.status === 'PENDING'));
  canManage = computed(() => this.auth.has('REFINEMENT_MANAGE') && !this.detail.summary.readOnly);
  gate = computed(() => {
    const t = this.detail.availableTransitions.find(x => x.forward && x.blockedBy.length && this.detail.summary.stage?.category === 'REFINEMENT');
    return t ? t.blockedBy.join(' ') : null;
  });

  itemForm = this.fb.nonNullable.group({ type: ['FUNCTIONAL_REQUIREMENT'], description: ['', Validators.required] });
  decisionForm = this.fb.nonNullable.group({ type: ['BUSINESS'], description: ['', Validators.required] });
  meetingForm = this.fb.nonNullable.group({ title: ['', Validators.required], participants: [''], notes: [''] });

  ngOnChanges(): void { this.load(); }

  load(): void {
    forkJoin({ snapshot: this.platform.refinement(this.detail.summary.id), suggestions: this.ai.suggestions(this.detail.summary.id, true) })
      .subscribe({ next: r => { this.snapshot.set(r.snapshot); this.suggestions.set(r.suggestions); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  itemsOf(type: string): RefinementItem[] { return (this.snapshot()?.items ?? []).filter(i => i.type === type); }
  itemLabel(t: string): string { return ITEM_TYPE_LABELS[t] ?? t; }
  decisionLabel(t: string): string { return DECISION_TYPE_LABELS[t] ?? t; }

  addItem(): void {
    if (this.itemForm.invalid) return;
    const v = this.itemForm.getRawValue();
    this.platform.addItem(this.detail.summary.id, v.type, v.description).subscribe({
      next: () => { this.itemForm.controls.description.reset(''); this.afterChange(); }, error: err => this.notify.error(err)
    });
  }

  toggle(i: RefinementItem): void {
    this.platform.setItemStatus(this.detail.summary.id, i.id, i.status === 'RESOLVED' ? 'OPEN' : 'RESOLVED').subscribe({
      next: () => this.afterChange(), error: err => this.notify.error(err)
    });
  }

  addDecision(): void {
    if (this.decisionForm.invalid) return;
    this.platform.addDecision(this.detail.summary.id, this.decisionForm.getRawValue()).subscribe({
      next: () => { this.decisionForm.controls.description.reset(''); this.afterChange(); }, error: err => this.notify.error(err)
    });
  }

  addMeeting(): void {
    if (this.meetingForm.invalid) { this.meetingForm.markAllAsTouched(); return; }
    this.platform.addMeeting(this.detail.summary.id, this.meetingForm.getRawValue()).subscribe({
      next: () => { this.meetingForm.reset(); this.notify.success('Reunião registrada.'); this.load(); }, error: err => this.notify.error(err)
    });
  }

  organize(meetingId: string): void {
    this.organizing.set(true);
    this.platform.meetingSuggestions(this.detail.summary.id, meetingId).subscribe({
      next: s => { this.organizing.set(false); this.notify.success(`${s.length} proposta(s) gerada(s) para revisão.`); this.load(); },
      error: err => { this.organizing.set(false); this.notify.error(err); }
    });
  }

  decide(d: SuggestionDecision): void {
    this.ai.decide(this.detail.summary.id, d.suggestion.id, d.action, d.value, d.reason).subscribe({
      next: () => this.afterChange(), error: err => this.notify.error(err)
    });
  }

  private afterChange(): void {
    this.load();
    this.changed.emit();
  }
}
