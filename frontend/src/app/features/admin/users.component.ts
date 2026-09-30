import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatExpansionModule } from '@angular/material/expansion';
import { forkJoin } from 'rxjs';
import { PlatformApi } from '../../core/api/platform-api.service';
import { RoleView, User } from '../../core/models';
import { ROLE_LABELS } from '../../core/labels';
import { NotifyService } from '../../core/ui/notify.service';
import { StateViewComponent } from '../../shared/state-view.component';

@Component({
  selector: 'dh-users',
  standalone: true,
  imports: [ReactiveFormsModule, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatExpansionModule, StateViewComponent],
  template: `
    <div class="page">
      <h1>Usuários e papéis</h1>
      <mat-expansion-panel class="card-like">
        <mat-expansion-panel-header><mat-panel-title><mat-icon>person_add</mat-icon>&nbsp;Novo usuário</mat-panel-title></mat-expansion-panel-header>
        <form [formGroup]="form" (ngSubmit)="create()" class="form-grid">
          <mat-form-field><mat-label>Nome completo</mat-label><input matInput formControlName="fullName"></mat-form-field>
          <mat-form-field><mat-label>E-mail</mat-label><input matInput type="email" formControlName="email"></mat-form-field>
          <mat-form-field><mat-label>Área</mat-label><input matInput formControlName="area"></mat-form-field>
          <mat-form-field><mat-label>Senha inicial (mín. 10)</mat-label><input matInput type="password" formControlName="password" autocomplete="new-password"></mat-form-field>
          <mat-form-field class="span-2"><mat-label>Papéis</mat-label>
            <mat-select formControlName="roles" multiple>@for (r of roles(); track r.code) { <mat-option [value]="r.code">{{ roleLabel(r.code) }} — {{ r.description }}</mat-option> }</mat-select></mat-form-field>
          <div class="span-2 row"><span class="spacer"></span><button mat-flat-button color="primary">Criar usuário</button></div>
        </form>
      </mat-expansion-panel>

      <section class="card" style="margin-top:16px">
        <dh-state [loading]="loading()" (retry)="load()">
          <table mat-table [dataSource]="users()" class="full">
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Usuário</th>
              <td mat-cell *matCellDef="let u"><strong>{{ u.fullName }}</strong><div class="small muted">{{ u.email }} · {{ u.area }}</div></td></ng-container>
            <ng-container matColumnDef="roles"><th mat-header-cell *matHeaderCellDef>Papéis</th>
              <td mat-cell *matCellDef="let u">
                <mat-select [value]="u.roles" multiple (selectionChange)="update(u, { roles: $event.value })" [attr.aria-label]="'Papéis de ' + u.fullName" class="roles">
                  @for (r of roles(); track r.code) { <mat-option [value]="r.code">{{ roleLabel(r.code) }}</mat-option> }
                </mat-select></td></ng-container>
            <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Ativo</th>
              <td mat-cell *matCellDef="let u"><mat-slide-toggle [checked]="u.active" (change)="update(u, { active: $event.checked })" [attr.aria-label]="'Ativo: ' + u.fullName"></mat-slide-toggle></td></ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let u; columns: columns"></tr>
          </table>
        </dh-state>
      </section>
    </div>
  `,
  styles: [`.card-like { border: 1px solid var(--dh-border); box-shadow: none !important; } .roles { min-width: 220px; }`]
})
export class UsersComponent implements OnInit {
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);
  private fb = inject(FormBuilder);

  columns = ['name', 'roles', 'active'];
  users = signal<User[]>([]);
  roles = signal<RoleView[]>([]);
  loading = signal(true);
  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required], email: ['', [Validators.required, Validators.email]], area: [''],
    password: ['', [Validators.required, Validators.minLength(10)]], roles: [[] as string[], Validators.required]
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    forkJoin({ users: this.platform.users(), roles: this.platform.roles() }).subscribe({
      next: r => { this.users.set(r.users); this.roles.set(r.roles); this.loading.set(false); }, error: () => this.loading.set(false)
    });
  }

  roleLabel(code: string): string { return ROLE_LABELS[code] ?? code; }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.platform.createUser(this.form.getRawValue()).subscribe({
      next: () => { this.notify.success('Usuário criado.'); this.form.reset(); this.load(); }, error: err => this.notify.error(err)
    });
  }

  update(u: User, body: { roles?: string[]; active?: boolean }): void {
    if (body.roles && !body.roles.length) { this.notify.error(null, 'O usuário precisa de ao menos um papel.'); this.load(); return; }
    this.platform.updateUser(u.id, { fullName: u.fullName, area: u.area, ...body }).subscribe({
      next: () => { this.notify.success('Usuário atualizado.'); this.load(); }, error: err => { this.notify.error(err); this.load(); }
    });
  }
}
