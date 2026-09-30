import { Component, Input, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, Location, NgTemplateOutlet } from '@angular/common';
import { TextFieldModule } from '@angular/cdk/text-field';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Subscription, debounceTime, firstValueFrom } from 'rxjs';
import { DemandApi } from '../../core/api/demand-api.service';
import { AiApi } from '../../core/api/ai-api.service';
import { PlatformApi } from '../../core/api/platform-api.service';
import { AiSuggestion, Catalog, DemandDetail, FieldMeta } from '../../core/models';
import { IMPACT_LABELS, SECTION_LABELS, URGENCY_LABELS } from '../../core/labels';
import { NotifyService } from '../../core/ui/notify.service';
import { StateViewComponent } from '../../shared/state-view.component';
import { SuggestionDecision, SuggestionListComponent } from '../../shared/suggestion-list.component';
import { ReasonDialogComponent, ReasonDialogData } from '../../shared/reason-dialog.component';
import { IntakeChatComponent } from './intake-chat.component';
import { DocumentsPanelComponent } from './documents-panel.component';

const SECTIONS = ['REQUESTER', 'INITIATIVE', 'IMPACT', 'FINANCIAL', 'STAKEHOLDERS', 'COMPLEMENTS'];
const BUDGET_DEPENDENT = ['estimatedBudget', 'costCenter', 'budgetApproved', 'expectedReturn'];
const LONG_SPAN = new Set(['LONG_TEXT']);

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';

@Component({
  selector: 'dh-demand-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, NgTemplateOutlet, TextFieldModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatIconModule, MatTabsModule, MatTooltipModule, MatProgressBarModule, StateViewComponent,
    SuggestionListComponent, IntakeChatComponent, DocumentsPanelComponent],
  template: `
    <div class="page">
      <dh-state [loading]="loading()" [error]="loadError()" (retry)="init()">
        <div class="row header">
          <div>
            <a routerLink="/demands/mine" class="small">← Minhas demandas</a>
            <h1>{{ demandId() ? (form.value['title'] || 'Rascunho de demanda') : 'Nova demanda' }}</h1>
            <div class="small muted" aria-live="polite">
              @switch (saveState()) {
                @case ('saving') { Salvando rascunho… }
                @case ('saved') { Rascunho salvo às {{ savedAt() | date: 'HH:mm:ss' }} }
                @case ('dirty') { Alterações não salvas }
                @case ('error') { <span style="color:var(--dh-danger)">Falha ao salvar — verifique os campos destacados</span> }
                @default { Preencha o formulário ou converse com o assistente. O rascunho é salvo automaticamente. }
              }
            </div>
          </div>
          <span class="spacer"></span>
          @if (demandId()) { <button mat-button color="warn" (click)="discard()"><mat-icon>delete_outline</mat-icon> Descartar</button> }
          <button mat-stroked-button (click)="save(true)" [disabled]="saveState() === 'saving'"><mat-icon>save</mat-icon> Salvar rascunho</button>
          <button mat-flat-button color="primary" (click)="goToReview()"><mat-icon>fact_check</mat-icon> Revisar e enviar</button>
        </div>

        <div class="layout">
          <form [formGroup]="form" class="form-col" (ngSubmit)="save(true)">
            <nav class="section-nav" aria-label="Seções do formulário">
              @for (s of sections; track s) {
                <a [href]="'#sec-' + s" (click)="scrollTo($event, s)" class="chip" [class.warning]="sectionHasErrors(s)">{{ sectionLabel(s) }}</a>
              }
              <a href="#sec-review" (click)="scrollTo($event, 'review')" class="chip">Revisão</a>
            </nav>

            @for (s of sections; track s) {
              <section class="card" [id]="'sec-' + s">
                <h3>{{ sectionLabel(s) }}</h3>
                @if (s === 'FINANCIAL') { <p class="small muted">Preencha somente se a iniciativa envolver custo, investimento ou retorno financeiro.</p> }
                <div class="form-grid">
                  @for (f of fieldsOf(s); track f.key) {
                    @if (visible(f.key)) {
                      <div [class.span-2]="isWide(f)">
                        @switch (f.type) {
                          @case ('BOOLEAN') {
                            <mat-slide-toggle [formControlName]="f.key" class="toggle">{{ f.label }}</mat-slide-toggle>
                            <div class="small muted hint">{{ f.help }}</div>
                          }
                          @case ('LONG_TEXT') {
                            <mat-form-field>
                              <mat-label>{{ f.label }}{{ isRequired(f) ? ' *' : '' }}</mat-label>
                              <textarea matInput [formControlName]="f.key" rows="3" cdkTextareaAutosize [maxlength]="f.maxLength"></textarea>
                              <mat-hint>{{ f.help }}</mat-hint>
                              @if (form.controls[f.key].errors?.['server']) { <mat-error>{{ form.controls[f.key].errors?.['server'] }}</mat-error> }
                            </mat-form-field>
                          }
                          @case ('IMPACT_LEVEL') { <ng-container *ngTemplateOutlet="selectTpl; context: { f: f, options: impactOptions }"></ng-container> }
                          @case ('URGENCY') { <ng-container *ngTemplateOutlet="selectTpl; context: { f: f, options: urgencyOptions }"></ng-container> }
                          @case ('DEMAND_TYPE') {
                            <mat-form-field>
                              <mat-label>{{ f.label }} *</mat-label>
                              <mat-select [formControlName]="f.key">
                                @for (t of activeTypes(); track t.code) {
                                  <mat-option [value]="t.code">{{ t.name }}</mat-option>
                                }
                              </mat-select>
                              <mat-hint>{{ typeHint() }}</mat-hint>
                              @if (form.controls[f.key].errors?.['server']) { <mat-error>{{ form.controls[f.key].errors?.['server'] }}</mat-error> }
                            </mat-form-field>
                          }
                          @case ('PROJECT') {
                            <mat-form-field>
                              <mat-label>{{ f.label }}</mat-label>
                              <mat-select [formControlName]="f.key">
                                <mat-option [value]="null">Não sei / definir na triagem</mat-option>
                                @for (p of catalog()?.projects ?? []; track p.code) {
                                  <mat-option [value]="p.code">{{ p.code }} — {{ p.name }}</mat-option>
                                }
                              </mat-select>
                              <mat-hint>{{ f.help }}</mat-hint>
                            </mat-form-field>
                          }
                          @default {
                            <mat-form-field>
                              <mat-label>{{ f.label }}{{ isRequired(f) ? ' *' : '' }}</mat-label>
                              <input matInput [formControlName]="f.key" [type]="inputType(f)" [maxlength]="f.maxLength"
                                     [attr.inputmode]="f.type === 'DECIMAL' || f.type === 'INTEGER' ? 'decimal' : null">
                              <mat-hint>{{ f.help }}</mat-hint>
                              @if (form.controls[f.key].errors?.['server']) { <mat-error>{{ form.controls[f.key].errors?.['server'] }}</mat-error> }
                            </mat-form-field>
                          }
                        }
                      </div>
                    }
                  }
                </div>
              </section>
            }

            <section class="card" id="sec-review">
              <h3>Revisão e envio</h3>
              @if (validating()) { <mat-progress-bar mode="indeterminate"></mat-progress-bar> }
              @if (validation(); as v) {
                @if (v.ready) {
                  <div class="alert info"><mat-icon>check_circle</mat-icon><div>Tudo pronto. Ao enviar, a demanda recebe um protocolo, passa pela análise da IA e segue para a triagem do PMO. Após o envio, alterações só pelo PMO.</div></div>
                } @else {
                  <div class="alert warning"><mat-icon>rule</mat-icon>
                    <div><strong>Complete antes de enviar:</strong>
                      <ul>@for (e of errorList(); track e.key) { <li><a href="#" (click)="focusField($event, e.key)">{{ e.message }}</a></li> }</ul>
                    </div>
                  </div>
                }
              }
              @if (pendingSuggestionCount()) {
                <p class="small muted">Há {{ pendingSuggestionCount() }} sugestão(ões) da IA não decidida(s). Elas não impedem o envio, mas recomendamos revisá-las.</p>
              }
              <div class="row">
                <button mat-stroked-button type="button" (click)="validate()"><mat-icon>fact_check</mat-icon> Verificar pendências</button>
                <span class="spacer"></span>
                <button mat-flat-button color="primary" type="button" (click)="submit()" [disabled]="submitting()"><mat-icon>send</mat-icon> Enviar demanda</button>
              </div>
            </section>
          </form>

          <aside class="side-col">
            <div class="card side-card">
              <mat-tab-group [selectedIndex]="sideTab()" (selectedIndexChange)="sideTab.set($event)" animationDuration="0ms">
                <mat-tab label="Assistente">
                  <div class="tab-body">
                    <dh-intake-chat [demandId]="demandId()" [ensureDraft]="ensureDraftFn" (suggestionsChanged)="onSuggestionsChanged()"></dh-intake-chat>
                  </div>
                </mat-tab>
                <mat-tab>
                  <ng-template mat-tab-label>Sugestões @if (pendingSuggestionCount()) { <span class="count">{{ pendingSuggestionCount() }}</span> }</ng-template>
                  <div class="tab-body">
                    <div class="row"><span class="small muted">Sugestões nunca são aplicadas sem sua decisão.</span><span class="spacer"></span>
                      @if (demandId()) { <button mat-icon-button (click)="checkConsistency()" matTooltip="Verificar divergências entre formulário e documentos" aria-label="Verificar divergências"><mat-icon>compare_arrows</mat-icon></button> }
                    </div>
                    <dh-state [empty]="!suggestions().length" emptyText="Nenhuma sugestão pendente. Converse com o assistente ou anexe documentos." emptyIcon="auto_awesome">
                      <dh-suggestion-list [suggestions]="suggestions()" [fieldLabels]="fieldLabels()" (decide)="decide($event)"></dh-suggestion-list>
                    </dh-state>
                  </div>
                </mat-tab>
                <mat-tab label="Documentos">
                  <div class="tab-body">
                    <dh-documents-panel [demandId]="demandId()" [canUpload]="true" [canAnalyze]="true" [ensureDraft]="ensureDraftFn" (uploaded)="onUploaded()"></dh-documents-panel>
                  </div>
                </mat-tab>
              </mat-tab-group>
            </div>
          </aside>
        </div>
      </dh-state>
    </div>

    <ng-template #selectTpl let-f="f" let-options="options">
      <mat-form-field [formGroup]="form">
        <mat-label>{{ f.label }}{{ isRequired(f) ? ' *' : '' }}</mat-label>
        <mat-select [formControlName]="f.key">
          <mat-option [value]="null">—</mat-option>
          @for (o of options; track o.value) { <mat-option [value]="o.value">{{ o.label }}</mat-option> }
        </mat-select>
        <mat-hint>{{ f.help }}</mat-hint>
        @if (form.controls[f.key].errors?.['server']) { <mat-error>{{ form.controls[f.key].errors?.['server'] }}</mat-error> }
      </mat-form-field>
    </ng-template>
  `,
  styles: [`
    .header { margin-bottom: 16px; align-items: flex-end; } .header h1 { margin: 4px 0; font-size: 22px; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 400px; gap: 16px; align-items: start; }
    @media (max-width: 1200px) { .layout { grid-template-columns: 1fr; } }
    .side-col { position: sticky; top: 72px; } @media (max-width: 1200px) { .side-col { position: static; } }
    .side-card { padding: 8px 12px 12px; }
    .tab-body { padding-top: 12px; }
    .section-nav { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; } .section-nav a { text-decoration: none; }
    .toggle { margin: 12px 0 2px; } .hint { margin-bottom: 12px; }
    mat-form-field { margin-bottom: 8px; }
    .count { margin-left: 6px; background: var(--dh-warning); color: #fff; border-radius: 999px; padding: 0 7px; font-size: 11px; }
  `]
})
export class DemandFormComponent implements OnInit, OnDestroy {
  private demands = inject(DemandApi);
  private ai = inject(AiApi);
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);
  private router = inject(Router);
  private location = inject(Location);
  private dialog = inject(MatDialog);
  private subs = new Subscription();

  @Input() id?: string;

  sections = SECTIONS;
  impactOptions = Object.entries(IMPACT_LABELS).map(([value, label]) => ({ value, label }));
  urgencyOptions = Object.entries(URGENCY_LABELS).map(([value, label]) => ({ value, label }));

  catalog = signal<Catalog | null>(null);
  demandId = signal<string | null>(null);
  loading = signal(true);
  loadError = signal<string | null>(null);
  saveState = signal<SaveState>('idle');
  savedAt = signal<Date | null>(null);
  suggestions = signal<AiSuggestion[]>([]);
  validation = signal<{ ready: boolean; fieldErrors: Record<string, string> } | null>(null);
  validating = signal(false);
  submitting = signal(false);
  sideTab = signal(0);
  form = new FormGroup<Record<string, FormControl>>({});

  private creating: Promise<string> | null = null;

  fields = computed(() => (this.catalog()?.fields ?? []).filter(f => f.clientEditable));
  fieldLabels = computed(() => Object.fromEntries((this.catalog()?.fields ?? []).map(f => [f.key, f.label])));
  activeTypes = computed(() => (this.catalog()?.demandTypes ?? []).filter(t => t.active));
  pendingSuggestionCount = computed(() => this.suggestions().length);
  errorList = computed(() => Object.entries(this.validation()?.fieldErrors ?? {}).map(([key, message]) => ({ key, message })));
  private typeValue = signal<string | null>(null);
  typeHint = computed(() => {
    const t = this.activeTypes().find(x => x.code === this.typeValue());
    return t ? `${t.description ?? ''} Fluxo: ${t.workflowName}.` : 'Não sabe? Descreva no assistente: a IA sugere o tipo.';
  });

  ensureDraftFn = () => this.ensureDraft();

  ngOnInit(): void { this.init(); }
  ngOnDestroy(): void { this.subs.unsubscribe(); }

  init(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.platform.catalog().subscribe({
      next: catalog => {
        this.catalog.set(catalog);
        this.buildForm(catalog.fields.filter(f => f.clientEditable));
        if (this.id) {
          this.demands.get(this.id).subscribe({
            next: d => this.applyDetail(d),
            error: err => { this.loadError.set(NotifyService.message(err, 'Demanda não encontrada.')); this.loading.set(false); }
          });
        } else {
          this.loading.set(false);
        }
      },
      error: () => { this.loadError.set('Não foi possível carregar o formulário.'); this.loading.set(false); }
    });
  }

  private buildForm(fields: FieldMeta[]): void {
    const group: Record<string, FormControl> = {};
    fields.forEach(f => group[f.key] = new FormControl(f.type === 'BOOLEAN' ? false : null));
    this.form = new FormGroup(group);
    this.subs.add(this.form.controls['demandType'].valueChanges.subscribe(v => this.typeValue.set(v)));
    this.subs.add(this.form.valueChanges.subscribe(() => { if (this.saveState() !== 'saving') this.saveState.set('dirty'); }));
    this.subs.add(this.form.valueChanges.pipe(debounceTime(2500)).subscribe(() => {
      if (this.demandId() && this.form.dirty) this.save(false);
    }));
  }

  private applyDetail(d: DemandDetail): void {
    if (d.summary.lifecycleState !== 'DRAFT') {
      this.router.navigate(['/demands', d.summary.id], { replaceUrl: true });
      return;
    }
    this.demandId.set(d.summary.id);
    this.patchFromServer(d.fields);
    this.loading.set(false);
    this.loadSuggestions();
  }

  private patchFromServer(fields: Record<string, string | null>): void {
    const patch: Record<string, unknown> = {};
    this.fields().forEach(f => {
      const raw = fields[f.key] ?? null;
      patch[f.key] = f.type === 'BOOLEAN' ? raw === 'true' : raw;
    });
    this.form.patchValue(patch, { emitEvent: false });
    this.typeValue.set((patch['demandType'] as string) ?? null);
    this.form.markAsPristine();
    this.saveState.set('saved');
    this.savedAt.set(new Date());
  }

  private payload(): Record<string, string | null> {
    const out: Record<string, string | null> = {};
    this.fields().forEach(f => {
      const v = this.form.controls[f.key].value;
      if (!this.visible(f.key)) { out[f.key] = f.type === 'BOOLEAN' ? 'false' : null; return; }
      out[f.key] = v === null || v === undefined || v === '' ? null : String(v);
    });
    return out;
  }

  visible(key: string): boolean {
    const v = this.form.controls;
    if (BUDGET_DEPENDENT.includes(key)) return !!v['hasBudgetImpact']?.value;
    if (key === 'regulatoryDescription') return !!v['regulatoryRequirement']?.value;
    if (key === 'deadlineJustification') return !!v['desiredDate']?.value;
    return true;
  }

  isRequired(f: FieldMeta): boolean {
    if (f.requiredAtSubmit) return true;
    return ['estimatedBudget', 'costCenter', 'regulatoryDescription', 'deadlineJustification'].includes(f.key) && this.visible(f.key);
  }

  isWide(f: FieldMeta): boolean { return LONG_SPAN.has(f.type) || f.key === 'title'; }
  inputType(f: FieldMeta): string { return f.type === 'DATE' ? 'date' : 'text'; }
  fieldsOf(section: string): FieldMeta[] { return this.fields().filter(f => f.section === section); }
  sectionLabel(s: string): string { return SECTION_LABELS[s] ?? s; }
  sectionHasErrors(s: string): boolean { return this.fieldsOf(s).some(f => !!this.validation()?.fieldErrors[f.key]); }

  /** Garante que exista rascunho no servidor (criado na primeira interação que precisa dele). */
  ensureDraft(): Promise<string> {
    const existing = this.demandId();
    if (existing) return this.flush().then(() => existing);
    if (!this.creating) {
      this.saveState.set('saving');
      this.creating = firstValueFrom(this.demands.create(this.payload())).then(d => {
        this.demandId.set(d.summary.id);
        this.location.replaceState(`/demands/${d.summary.id}/edit`);
        this.form.markAsPristine();
        this.saveState.set('saved');
        this.savedAt.set(new Date());
        return d.summary.id;
      }).catch(err => {
        this.saveState.set('error');
        this.applyServerErrors(err);
        this.creating = null;
        throw err;
      });
    }
    return this.creating;
  }

  private async flush(): Promise<void> {
    if (this.form.dirty) await this.saveAsync();
  }

  save(explicit: boolean): void {
    if (!this.demandId()) {
      this.ensureDraft().then(() => explicit && this.notify.success('Rascunho criado.')).catch(err => this.notify.error(err));
      return;
    }
    this.saveAsync().then(() => { if (explicit) this.notify.success('Rascunho salvo.'); }).catch(err => { if (explicit) this.notify.error(err); });
  }

  private saveAsync(): Promise<void> {
    const id = this.demandId();
    if (!id) return Promise.resolve();
    this.saveState.set('saving');
    this.clearServerErrors();
    return firstValueFrom(this.demands.updateDraft(id, this.payload())).then(() => {
      this.form.markAsPristine();
      this.saveState.set('saved');
      this.savedAt.set(new Date());
    }).catch(err => {
      this.saveState.set('error');
      this.applyServerErrors(err);
      throw err;
    });
  }

  private applyServerErrors(err: unknown): void {
    Object.entries(NotifyService.fieldErrors(err)).forEach(([key, message]) => {
      const c = this.form.controls[key];
      if (c) { c.setErrors({ server: message }); c.markAsTouched(); }
    });
  }

  private clearServerErrors(): void {
    Object.values(this.form.controls).forEach(c => {
      if (c.errors?.['server']) c.setErrors(null);
    });
  }

  loadSuggestions(): void {
    const id = this.demandId();
    if (!id) return;
    this.ai.suggestions(id, true).subscribe({
      next: s => this.suggestions.set(s.filter(x => ['FIELD_VALUE', 'INCONSISTENCY', 'MISSING_INFO'].includes(x.kind))),
      error: () => {}
    });
  }

  onSuggestionsChanged(): void {
    this.loadSuggestions();
  }

  onUploaded(): void {
    // A extração roda de forma assíncrona após o upload: atualiza agora e novamente em instantes.
    this.loadSuggestions();
    setTimeout(() => this.loadSuggestions(), 2000);
    setTimeout(() => { this.loadSuggestions(); if (this.suggestions().length) this.sideTab.set(1); }, 5000);
  }

  async decide(d: SuggestionDecision): Promise<void> {
    const id = this.demandId();
    if (!id) return;
    try {
      await this.flush();
      await firstValueFrom(this.ai.decide(id, d.suggestion.id, d.action, d.value, d.reason));
      const detail = await firstValueFrom(this.demands.get(id));
      this.patchFromServer(detail.fields);
      this.loadSuggestions();
      if (d.action !== 'REJECT' && d.suggestion.field) this.notify.success('Sugestão aplicada ao formulário.');
    } catch (err) {
      this.notify.error(err);
    }
  }

  checkConsistency(): void {
    const id = this.demandId();
    if (!id) return;
    this.flush().then(() => this.ai.checkConsistency(id).subscribe({
      next: r => { this.notify.success(r.divergences ? `${r.divergences} divergência(s) encontrada(s).` : 'Nenhuma divergência encontrada.'); this.loadSuggestions(); },
      error: err => this.notify.error(err)
    }));
  }

  goToReview(): void {
    this.validate();
    document.getElementById('sec-review')?.scrollIntoView({ behavior: 'smooth' });
  }

  async validate(): Promise<void> {
    try {
      this.validating.set(true);
      const id = await this.ensureDraft();
      await this.flush();
      const v = await firstValueFrom(this.demands.validation(id));
      this.validation.set(v);
      Object.entries(v.fieldErrors).forEach(([key, message]) => {
        const c = this.form.controls[key];
        if (c) { c.setErrors({ server: message }); c.markAsTouched(); }
      });
    } catch (err) {
      this.notify.error(err);
    } finally {
      this.validating.set(false);
    }
  }

  async submit(): Promise<void> {
    this.submitting.set(true);
    try {
      const id = await this.ensureDraft();
      await this.flush();
      const detail = await firstValueFrom(this.demands.submit(id));
      this.notify.success(`Demanda enviada! Protocolo ${detail.summary.protocol}.`);
      this.router.navigate(['/demands', id]);
    } catch (err) {
      this.notify.error(err);
      const errors = NotifyService.fieldErrors(err);
      if (Object.keys(errors).length) {
        this.validation.set({ ready: false, fieldErrors: errors });
        this.applyServerErrors(err);
      }
    } finally {
      this.submitting.set(false);
    }
  }

  discard(): void {
    const id = this.demandId();
    if (!id) return;
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '480px', data: { title: 'Descartar rascunho?', message: 'O rascunho deixará de aparecer na sua lista. Esta ação é registrada.', label: 'Observação (opcional)', confirmText: 'Descartar', danger: true }
    }).afterClosed().subscribe(result => {
      if (result === undefined) return;
      this.demands.discard(id).subscribe({
        next: () => { this.notify.success('Rascunho descartado.'); this.router.navigate(['/demands/mine']); },
        error: err => this.notify.error(err)
      });
    });
  }

  scrollTo(e: Event, s: string): void {
    e.preventDefault();
    document.getElementById('sec-' + s)?.scrollIntoView({ behavior: 'smooth' });
  }

  focusField(e: Event, key: string): void {
    e.preventDefault();
    const el = document.querySelector<HTMLElement>(`[formcontrolname="${key}"]`);
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el?.focus();
  }
}
