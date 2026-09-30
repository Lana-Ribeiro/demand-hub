import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { ApprovalRule, WorkflowMetadata, WorkflowStage, WorkflowView } from '../../core/models';
import { CATEGORY_LABELS, ROLE_LABELS } from '../../core/labels';
import { NotifyService } from '../../core/ui/notify.service';
import { StateViewComponent } from '../../shared/state-view.component';

/** Workflows, gates de saída, ações de entrada e regras de aprovação — configuráveis sem deploy, com auditoria. */
@Component({
  selector: 'dh-workflows',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatTabsModule, StateViewComponent],
  template: `
    <div class="page">
      <h1>Workflows e approval gates</h1>
      <p class="muted">Cada tipo de demanda aponta para um workflow. Transições são validadas pelo motor; gates bloqueiam o avanço até serem cumpridos.</p>
      <dh-state [loading]="loading()" [error]="error()" (retry)="load()">
        <mat-tab-group animationDuration="0ms">
          @for (wf of workflows(); track wf.id) {
            <mat-tab [label]="wf.name">
              <div class="tabbody">
                <p class="small muted">{{ wf.description }}</p>
                <section class="card">
                  <h3>Etapas</h3>
                  <div class="stages">
                    @for (s of wf.stages; track s.id) {
                      <div class="stage">
                        <div class="row"><strong>{{ s.position + 1 }}. {{ s.name }}</strong><span class="chip neutral small">{{ category(s.category) }}</span>
                          @if (s.autoAdvance) { <span class="chip info small">auto-avanço</span> }<span class="spacer"></span>
                          <button mat-icon-button (click)="editStage(s)" aria-label="Editar etapa"><mat-icon>edit</mat-icon></button></div>
                        <div class="small muted mono">{{ s.code }} · Jira: {{ s.jiraStatus || '—' }}</div>
                        @if (s.exitRequirements.length) { <div class="small">Gates: {{ s.exitRequirements.join(', ') }}</div> }
                        @if (s.onEnterActions.length) { <div class="small">Ao entrar: {{ s.onEnterActions.join(', ') }}</div> }
                        @if (stageEditing()?.id === s.id) {
                          <div class="edit">
                            <mat-form-field class="full"><mat-label>Nome</mat-label><input matInput [(ngModel)]="stageDraft.name"></mat-form-field>
                            <mat-form-field class="full"><mat-label>Status no Jira</mat-label><input matInput [(ngModel)]="stageDraft.jiraStatus"></mat-form-field>
                            <mat-form-field class="full"><mat-label>Gates de saída</mat-label>
                              <mat-select multiple [(ngModel)]="stageDraft.exitRequirements">@for (r of meta()?.exitRequirements ?? []; track r) { <mat-option [value]="r">{{ r }}</mat-option> }</mat-select></mat-form-field>
                            <mat-form-field class="full"><mat-label>Ações ao entrar</mat-label>
                              <mat-select multiple [(ngModel)]="stageDraft.onEnterActions">@for (a of meta()?.stageActions ?? []; track a) { <mat-option [value]="a">{{ a }}</mat-option> }</mat-select></mat-form-field>
                            <mat-slide-toggle [(ngModel)]="stageDraft.autoAdvance">Avançar automaticamente quando os gates forem cumpridos</mat-slide-toggle>
                            <div class="row"><span class="spacer"></span><button mat-button (click)="stageEditing.set(null)">Cancelar</button><button mat-flat-button color="primary" (click)="saveStage()">Salvar</button></div>
                          </div>
                        }
                      </div>
                    }
                  </div>
                </section>

                <section class="card">
                  <h3>Transições</h3>
                  <div class="scroll"><table class="simple">
                    <thead><tr><th>De</th><th>Ação</th><th>Para</th><th>Permissão</th><th>Regras</th></tr></thead>
                    <tbody>@for (t of wf.transitions; track t.id) {
                      <tr><td class="mono small">{{ t.from }}</td><td>{{ t.label }} <span class="mono small muted">({{ t.action }})</span></td><td class="mono small">{{ t.to }}</td>
                        <td class="small">{{ t.systemOnly ? 'Somente sistema' : t.requiredPermission }}</td>
                        <td class="small">@if (t.forward) { <span class="chip small">exige gates</span> } @if (t.requiresReason) { <span class="chip warning small">motivo</span> }</td></tr>
                    }</tbody></table></div>
                </section>

                <section class="card">
                  <div class="row"><h3>Regras de aprovação</h3><span class="spacer"></span><button mat-stroked-button (click)="newRule(wf)"><mat-icon>add</mat-icon> Nova regra</button></div>
                  @for (r of wf.approvalRules; track r.id) {
                    <div class="rule" [class.inactive]="!r.active">
                      <div class="row"><strong>{{ r.name }}</strong><span class="chip warning small">{{ role(r.approverRole) }}</span>
                        <span class="small muted">na etapa {{ r.stageCode }}</span>@if (!r.active) { <span class="chip neutral small">inativa</span> }
                        <span class="spacer"></span>
                        <button mat-icon-button (click)="editRule(wf, r)" aria-label="Editar regra"><mat-icon>edit</mat-icon></button>
                        @if (r.active) { <button mat-icon-button (click)="deactivate(r)" aria-label="Desativar regra"><mat-icon>block</mat-icon></button> }</div>
                      <div class="small">{{ r.conditionField ? ('Quando ' + r.conditionField + ' ' + r.conditionOperator + ' ' + r.conditionValue) : 'Sempre' }}</div>
                    </div>
                  }
                  @if (ruleFormFor() === wf.id) {
                    <form [formGroup]="ruleForm" (ngSubmit)="saveRule(wf)" class="form-grid rule-form">
                      <mat-form-field class="span-2"><mat-label>Nome da regra</mat-label><input matInput formControlName="name"></mat-form-field>
                      <mat-form-field><mat-label>Etapa</mat-label><mat-select formControlName="stageCode">@for (s of wf.stages; track s.code) { <mat-option [value]="s.code">{{ s.name }}</mat-option> }</mat-select></mat-form-field>
                      <mat-form-field><mat-label>Papel aprovador</mat-label><mat-select formControlName="approverRole">@for (r of meta()?.roles ?? []; track r) { <mat-option [value]="r">{{ role(r) }}</mat-option> }</mat-select></mat-form-field>
                      <mat-form-field><mat-label>Campo da condição (vazio = sempre)</mat-label><mat-select formControlName="conditionField"><mat-option [value]="null">— sempre —</mat-option>@for (f of meta()?.conditionFields ?? []; track f) { <mat-option [value]="f">{{ f }}</mat-option> }</mat-select></mat-form-field>
                      <mat-form-field><mat-label>Operador</mat-label><mat-select formControlName="conditionOperator"><mat-option [value]="null">—</mat-option>@for (o of meta()?.operators ?? []; track o) { <mat-option [value]="o">{{ o }}</mat-option> }</mat-select></mat-form-field>
                      <mat-form-field class="span-2"><mat-label>Valor (ex.: P1, 500000, true, P1,P2)</mat-label><input matInput formControlName="conditionValue"></mat-form-field>
                      <div class="span-2 row"><span class="spacer"></span><button mat-button type="button" (click)="ruleFormFor.set(null)">Cancelar</button><button mat-flat-button color="primary">Salvar regra</button></div>
                    </form>
                  }
                </section>
              </div>
            </mat-tab>
          }
        </mat-tab-group>
      </dh-state>
    </div>
  `,
  styles: [`.tabbody { padding: 16px 2px; } .stages { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 10px; }
            .stage { border: 1px solid var(--dh-border); border-radius: 8px; padding: 10px; } .edit { margin-top: 8px; }
            .scroll { overflow-x: auto; } table.simple { width: 100%; border-collapse: collapse; } table.simple th, table.simple td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--dh-border); }
            .rule { padding: 8px 0; border-bottom: 1px solid var(--dh-border); } .rule.inactive { opacity: .55; } .rule-form { margin-top: 12px; }`]
})
export class WorkflowsComponent implements OnInit {
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);
  private fb = inject(FormBuilder);

  workflows = signal<WorkflowView[]>([]);
  meta = signal<WorkflowMetadata | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  stageEditing = signal<WorkflowStage | null>(null);
  stageDraft: Partial<WorkflowStage> = {};
  ruleFormFor = signal<number | null>(null);
  editingRuleId: number | null = null;
  ruleForm = this.fb.group({
    name: ['', Validators.required], stageCode: ['', Validators.required], approverRole: ['', Validators.required],
    conditionField: [null as string | null], conditionOperator: [null as string | null], conditionValue: ['']
  });
  hasData = computed(() => this.workflows().length > 0);

  ngOnInit(): void { this.load(); }

  load(): void {
    forkJoin({ wf: this.platform.workflows(), meta: this.platform.workflowMetadata() }).subscribe({
      next: r => { this.workflows.set(r.wf); this.meta.set(r.meta); this.loading.set(false); this.error.set(null); },
      error: () => { this.error.set('Não foi possível carregar os workflows.'); this.loading.set(false); }
    });
  }

  category(c: string): string { return CATEGORY_LABELS[c] ?? c; }
  role(r: string): string { return ROLE_LABELS[r] ?? r; }

  editStage(s: WorkflowStage): void {
    this.stageDraft = { name: s.name, jiraStatus: s.jiraStatus, autoAdvance: s.autoAdvance, exitRequirements: [...s.exitRequirements], onEnterActions: [...s.onEnterActions] };
    this.stageEditing.set(s);
  }

  saveStage(): void {
    const s = this.stageEditing();
    if (!s) return;
    this.platform.updateStage(s.id, this.stageDraft).subscribe({
      next: () => { this.notify.success('Etapa atualizada.'); this.stageEditing.set(null); this.load(); }, error: err => this.notify.error(err)
    });
  }

  newRule(wf: WorkflowView): void {
    this.editingRuleId = null;
    this.ruleForm.reset({ name: '', stageCode: '', approverRole: '', conditionField: null, conditionOperator: null, conditionValue: '' });
    this.ruleFormFor.set(wf.id);
  }

  editRule(wf: WorkflowView, r: ApprovalRule): void {
    this.editingRuleId = r.id;
    this.ruleForm.reset({ name: r.name, stageCode: r.stageCode, approverRole: r.approverRole, conditionField: r.conditionField ?? null,
      conditionOperator: r.conditionOperator ?? null, conditionValue: r.conditionValue ?? '' });
    this.ruleFormFor.set(wf.id);
  }

  saveRule(wf: WorkflowView): void {
    this.ruleForm.markAllAsTouched();
    if (this.ruleForm.invalid) return;
    const v = this.ruleForm.getRawValue();
    this.platform.saveRule({ ...v, workflowId: wf.id, active: true } as Partial<ApprovalRule>, this.editingRuleId ?? undefined).subscribe({
      next: () => { this.notify.success('Regra salva.'); this.ruleFormFor.set(null); this.load(); }, error: err => this.notify.error(err)
    });
  }

  deactivate(r: ApprovalRule): void {
    this.platform.deactivateRule(r.id).subscribe({ next: () => { this.notify.success('Regra desativada.'); this.load(); }, error: err => this.notify.error(err) });
  }
}
