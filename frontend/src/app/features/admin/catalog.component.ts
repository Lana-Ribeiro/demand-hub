import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { forkJoin } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { DemandTypeView, ExecutionStatus, Priority, WorkflowView } from '../../core/models';
import { NotifyService } from '../../core/ui/notify.service';
import { StateViewComponent } from '../../shared/state-view.component';

/** Catálogos configuráveis: tipos de demanda (→ workflow), política de prioridades e status técnicos (labels do repositório GitLab/GitHub e texto do Jira). */
@Component({
  selector: 'dh-catalog-admin',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule, StateViewComponent],
  template: `
    <div class="page">
      <h1>Catálogos</h1>
      <dh-state [loading]="loading()" (retry)="load()">
        <section class="card">
          <div class="row"><h3>Tipos de demanda</h3><span class="spacer"></span><button mat-stroked-button (click)="addType()"><mat-icon>add</mat-icon> Novo tipo</button></div>
          <p class="small muted">O workflow define o fluxo: tipos não técnicos nunca geram issue no repositório de código.</p>
          @for (t of types(); track t.code) {
            <div class="line">
              <mat-form-field class="code"><mat-label>Código</mat-label><input matInput [(ngModel)]="t.code" [disabled]="!!t.id"></mat-form-field>
              <mat-form-field class="name"><mat-label>Nome</mat-label><input matInput [(ngModel)]="t.name"></mat-form-field>
              <mat-form-field class="desc"><mat-label>Descrição</mat-label><input matInput [(ngModel)]="t.description"></mat-form-field>
              <mat-form-field class="wf"><mat-label>Workflow</mat-label>
                <mat-select [(ngModel)]="t.workflowCode">@for (w of workflows(); track w.code) { <mat-option [value]="w.code">{{ w.name }}</mat-option> }</mat-select></mat-form-field>
              <mat-slide-toggle [(ngModel)]="t.technical">Técnica</mat-slide-toggle>
              <mat-slide-toggle [(ngModel)]="t.active">Ativo</mat-slide-toggle>
              <button mat-icon-button (click)="saveType(t)" aria-label="Salvar tipo"><mat-icon>save</mat-icon></button>
            </div>
          }
        </section>
        <section class="card">
          <h3>Prioridades e política</h3>
          <p class="small muted">A política é o contexto que o Priority Agent usa para sugerir — a decisão final é do PMO.</p>
          @for (p of priorities(); track p.code) {
            <div class="line">
              <strong class="code">{{ p.code }}</strong>
              <mat-form-field class="name"><mat-label>Nome</mat-label><input matInput [(ngModel)]="p.name"></mat-form-field>
              <mat-form-field class="color"><mat-label>Cor</mat-label><input matInput [(ngModel)]="p.color"></mat-form-field>
              <mat-form-field class="desc"><mat-label>Política</mat-label><textarea matInput rows="2" [(ngModel)]="p.policy"></textarea></mat-form-field>
              <button mat-icon-button (click)="savePriority(p)" aria-label="Salvar prioridade"><mat-icon>save</mat-icon></button>
            </div>
          }
        </section>
        <section class="card">
          <h3>Status da execução técnica (GitLab/GitHub)</h3>
          <p class="small muted">Label da issue no repositório (GitLab ou GitHub) → status técnico. O texto é publicado como comentário no Jira PMO, sem alterar a etapa da demanda.</p>
          @for (s of statuses(); track s.code) {
            <div class="line">
              <strong class="code">{{ s.code }}</strong>
              <mat-form-field class="name"><mat-label>Nome</mat-label><input matInput [(ngModel)]="s.name"></mat-form-field>
              <mat-form-field class="wf"><mat-label>Label no repositório</mat-label><input matInput [(ngModel)]="s.scmLabel"></mat-form-field>
              <mat-form-field class="desc"><mat-label>Comentário no Jira</mat-label><input matInput [(ngModel)]="s.jiraCommentTemplate"></mat-form-field>
              <button mat-icon-button (click)="saveStatus(s)" aria-label="Salvar status"><mat-icon>save</mat-icon></button>
            </div>
          }
        </section>
      </dh-state>
    </div>
  `,
  styles: [`.line { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; border-bottom: 1px solid var(--dh-border); padding: 8px 0; }
            .code { width: 150px; } .name { width: 180px; } .desc { flex: 1; min-width: 240px; } .wf { width: 220px; } .color { width: 110px; }`]
})
export class CatalogAdminComponent implements OnInit {
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);

  types = signal<DemandTypeView[]>([]);
  priorities = signal<Priority[]>([]);
  statuses = signal<ExecutionStatus[]>([]);
  workflows = signal<WorkflowView[]>([]);
  loading = signal(true);

  ngOnInit(): void { this.load(); }

  load(): void {
    forkJoin({ catalog: this.platform.catalog(true), statuses: this.platform.executionStatuses(), workflows: this.platform.workflows() }).subscribe({
      next: r => {
        this.types.set(r.catalog.demandTypes.map(t => ({ ...t })));
        this.priorities.set(r.catalog.priorities.map(p => ({ ...p })));
        this.statuses.set(r.statuses);
        this.workflows.set(r.workflows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  addType(): void {
    this.types.update(t => [...t, { id: 0, code: '', name: '', description: '', technical: false, workflowCode: 'SERVICE', workflowName: '', active: true }]);
  }

  saveType(t: DemandTypeView): void {
    this.platform.saveDemandType({ code: t.code.toUpperCase(), name: t.name, description: t.description, technical: t.technical, workflowCode: t.workflowCode, active: t.active }, t.id || undefined)
      .subscribe({ next: () => { this.notify.success('Tipo salvo.'); this.load(); }, error: err => this.notify.error(err) });
  }

  savePriority(p: Priority): void {
    this.platform.updatePriority(p.code, { name: p.name, color: p.color, policy: p.policy }).subscribe({ next: () => this.notify.success('Prioridade salva.'), error: err => this.notify.error(err) });
  }

  saveStatus(s: ExecutionStatus): void {
    this.platform.updateExecutionStatus(s.code, { name: s.name, scmLabel: s.scmLabel, jiraCommentTemplate: s.jiraCommentTemplate })
      .subscribe({ next: () => this.notify.success('Status salvo.'), error: err => this.notify.error(err) });
  }
}
