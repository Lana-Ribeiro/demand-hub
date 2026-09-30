import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { NotifyService } from '../../core/ui/notify.service';

@Component({
  selector: 'dh-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="wrap">
      <section class="panel" aria-labelledby="login-title">
        <div class="brand"><mat-icon>hub</mat-icon><span>Demand Hub</span></div>
        <h1 id="login-title">Entrar</h1>
        <p class="muted">Plataforma de demandas da Diretoria Transformação de Redes</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field>
            <mat-label>E-mail corporativo</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="username" required>
            @if (form.controls.email.touched && form.controls.email.invalid) { <mat-error>Informe um e-mail válido.</mat-error> }
          </mat-form-field>
          <mat-form-field>
            <mat-label>Senha</mat-label>
            <input matInput [type]="hide() ? 'password' : 'text'" formControlName="password" autocomplete="current-password" required>
            <button mat-icon-button matSuffix type="button" (click)="hide.set(!hide())" [attr.aria-label]="hide() ? 'Mostrar senha' : 'Ocultar senha'">
              <mat-icon>{{ hide() ? 'visibility' : 'visibility_off' }}</mat-icon>
            </button>
          </mat-form-field>
          @if (error()) { <div class="alert danger small" role="alert"><mat-icon>error_outline</mat-icon>{{ error() }}</div> }
          <button mat-flat-button color="primary" class="full submit" type="submit" [disabled]="loading()">
            @if (loading()) { <mat-spinner diameter="18"></mat-spinner> } @else { Entrar }
          </button>
        </form>
        <p class="small muted note">Acesso autenticado. SSO corporativo (Entra ID) previsto na evolução da plataforma.</p>
      </section>
      <aside class="hero" aria-hidden="true">
        <h2>Da ideia à entrega, em um só lugar.</h2>
        <ul>
          <li><mat-icon>edit_note</mat-icon> Abertura guiada com apoio de IA</li>
          <li><mat-icon>fact_check</mat-icon> Triagem e aprovações rastreáveis</li>
          <li><mat-icon>sync_alt</mat-icon> Jira PMO e GitLab/GitHub integrados, cada um no seu papel</li>
        </ul>
      </aside>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: grid; grid-template-columns: minmax(360px, 480px) 1fr; }
    .panel { background: #fff; padding: 48px 40px; display: flex; flex-direction: column; justify-content: center; }
    .brand { display: flex; align-items: center; gap: 8px; font-weight: 700; color: var(--dh-primary); font-size: 18px; margin-bottom: 24px; }
    h1 { margin: 0 0 4px; font-size: 26px; }
    form { margin-top: 24px; display: flex; flex-direction: column; gap: 12px; }
    .submit { height: 44px; }
    .note { margin-top: 24px; }
    .hero { background: linear-gradient(135deg, #111a33, #303f9f); color: #fff; padding: 64px; display: flex; flex-direction: column; justify-content: center; }
    .hero h2 { font-size: 30px; max-width: 460px; }
    .hero ul { list-style: none; padding: 0; } .hero li { display: flex; gap: 10px; align-items: center; margin: 14px 0; font-size: 16px; }
    @media (max-width: 860px) { .wrap { grid-template-columns: 1fr; } .hero { display: none; } .panel { padding: 32px 16px; } }
  `]
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  hide = signal(true);
  loading = signal(false);
  error = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => this.router.navigate(['/']),
      error: err => {
        this.loading.set(false);
        this.error.set(NotifyService.message(err, 'Não foi possível entrar.'));
      }
    });
  }
}
