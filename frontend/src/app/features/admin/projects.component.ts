import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { forkJoin } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { Project, UserRef } from '../../core/models';
import { NotifyService } from '../../core/ui/notify.service';
import { ProjectBadgeComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';

/** Projetos: identidade (código, nome, cor, ícone), responsáveis e chaves de integração Jira e repositório (GitLab/GitHub). */
@Component({
  selector: 'dh-projects',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule,
    ProjectBadgeComponent, StateViewComponent],
  template: `
    <div class="page">
      <div class="row"><h1>Projetos</h1><span class="spacer"></span><button mat-flat-button color="primary" (click)="edit(null)"><mat-icon>add</mat-icon> Novo projeto</button></div>
      <div class="grid layout">
        <section class="card">
          <dh-state [loading]="loading()" [empty]="!projects().length" emptyText="Nenhum projeto cadastrado.">
            @for (p of projects(); track p.id) {
              <div class="proj" [class.selected]="editingId() === p.id" (click)="edit(p)" (keydown.enter)="edit(p)" tabindex="0">
                <dh-project-badge [project]="p"></dh-project-badge>
                @if (!p.active) { <span class="chip neutral">Inativo</span> }
                <span class="spacer"></span>
                <span class="small muted">Jira: {{ p.jiraProjectKey || 'padrão' }} · Repositório: {{ p.scmProjectRef || 'padrão' }}</span>
              </div>
            }
          </dh-state>
        </section>
        @if (showForm()) {
          <section class="card">
            <h3>{{ editingId() ? 'Editar projeto' : 'Novo projeto' }}</h3>
            <form [formGroup]="form" (ngSubmit)="save()">
              <div class="form-grid">
                <mat-form-field><mat-label>Código</mat-label><input matInput formControlName="code" placeholder="SMARTDESK"><mat-hint>Maiúsculas, números, _ ou -</mat-hint></mat-form-field>
                <mat-form-field><mat-label>Nome</mat-label><input matInput formControlName="name"></mat-form-field>
                <mat-form-field class="span-2"><mat-label>Descrição</mat-label><textarea matInput rows="2" formControlName="description"></textarea></mat-form-field>
                <mat-form-field><mat-label>Cor (#RRGGBB)</mat-label><input matInput formControlName="color"><input matSuffix type="color" formControlName="color" aria-label="Seletor de cor" class="picker"></mat-form-field>
                <mat-form-field><mat-label>Ícone (Material)</mat-label><input matInput formControlName="icon"></mat-form-field>
                <mat-form-field><mat-label>Responsável</mat-label>
                  <mat-select formControlName="ownerId"><mat-option [value]="null">—</mat-option>@for (u of users(); track u.id) { <mat-option [value]="u.id">{{ u.fullName }}</mat-option> }</mat-select></mat-form-field>
                <mat-form-field><mat-label>PMO</mat-label>
                  <mat-select formControlName="pmoId"><mat-option [value]="null">—</mat-option>@for (u of users(); track u.id) { <mat-option [value]="u.id">{{ u.fullName }}</mat-option> }</mat-select></mat-form-field>
                <mat-form-field><mat-label>Chave do projeto Jira</mat-label><input matInput formControlName="jiraProjectKey"></mat-form-field>
                <mat-form-field><mat-label>Repositório (GitLab: id · GitHub: owner/repo)</mat-label><input matInput formControlName="scmProjectRef"></mat-form-field>
                <mat-slide-toggle formControlName="active">Ativo</mat-slide-toggle>
              </div>
              <div class="row"><span class="spacer"></span><button mat-button type="button" (click)="showForm.set(false)">Cancelar</button><button mat-flat-button color="primary">Salvar</button></div>
            </form>
          </section>
        }
      </div>
    </div>
  `,
  styles: [`.layout { grid-template-columns: 1fr 1fr; } @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
            .proj { display: flex; align-items: center; gap: 10px; padding: 12px 8px; border-bottom: 1px solid var(--dh-border); cursor: pointer; flex-wrap: wrap; border-radius: 6px; }
            .proj.selected, .proj:hover { background: #f5f7fc; } .picker { width: 32px; height: 24px; border: 0; background: none; }`]
})
export class ProjectsComponent implements OnInit {
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);
  private fb = inject(FormBuilder);

  projects = signal<Project[]>([]);
  users = signal<UserRef[]>([]);
  loading = signal(true);
  showForm = signal(false);
  editingId = signal<number | null>(null);
  form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z0-9_-]+$/)]], name: ['', Validators.required], description: [''],
    color: ['#303F9F', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]], icon: [''], ownerId: [null as string | null],
    pmoId: [null as string | null], jiraProjectKey: [''], scmProjectRef: [''], active: [true]
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    forkJoin({ projects: this.platform.projects(false), users: this.platform.assignableUsers() }).subscribe({
      next: r => { this.projects.set(r.projects); this.users.set(r.users); this.loading.set(false); }, error: () => this.loading.set(false)
    });
  }

  edit(p: Project | null): void {
    this.editingId.set(p?.id ?? null);
    this.form.reset({
      code: p?.code ?? '', name: p?.name ?? '', description: p?.description ?? '', color: p?.color ?? '#303F9F', icon: p?.icon ?? '',
      ownerId: p?.owner?.id ?? null, pmoId: p?.pmo?.id ?? null, jiraProjectKey: p?.jiraProjectKey ?? '', scmProjectRef: p?.scmProjectRef ?? '', active: p?.active ?? true
    });
    this.showForm.set(true);
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.platform.saveProject({ ...v, code: (v.code ?? '').toUpperCase() } as never, this.editingId() ?? undefined).subscribe({
      next: () => { this.notify.success('Projeto salvo.'); this.showForm.set(false); this.platform.catalog(true); this.load(); },
      error: err => this.notify.error(err)
    });
  }
}
