import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { NotifyService } from '../../core/ui/notify.service';

@Component({
  selector: 'dh-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="signin-page">
      <header class="nav">
        <div class="brand"><span class="mark"><mat-icon>hub</mat-icon></span>Demand Hub</div>
        <span class="org">Diretoria Transformação de Redes</span>
      </header>

      <main class="main">
        <section class="intro" aria-hidden="true">
          <p class="kicker">Gestão de demandas</p>
          <h1>Da ideia<br>à entrega.</h1>
          <p class="lead">Um só lugar para abrir, aprovar e acompanhar cada demanda — com IA que sugere e pessoas que decidem.</p>

          <div class="stack">
            <div class="tile t1">
              <span class="ico blue"><mat-icon>edit_note</mat-icon></span>
              <div><strong>Demanda aberta</strong><span>Formulário guiado com apoio de IA</span></div>
            </div>
            <div class="tile t2">
              <span class="ico green"><mat-icon>task_alt</mat-icon></span>
              <div><strong>Triagem e aprovações</strong><span>Regras claras, tudo auditado</span></div>
            </div>
            <div class="tile t3">
              <span class="ico indigo"><mat-icon>sync_alt</mat-icon></span>
              <div><strong>Jira e GitHub integrados</strong><span>Gestão e execução, cada um no seu papel</span></div>
            </div>
          </div>
        </section>

        <section class="signin" aria-labelledby="login-title">
          <h2 id="login-title">Iniciar sessão</h2>
          <p class="sub">Use seu e-mail corporativo.</p>

          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <div class="fields" [class.invalid]="error()">
              <label class="field">
                <input type="email" formControlName="email" autocomplete="username" placeholder=" " required
                       [attr.aria-invalid]="form.controls.email.touched && form.controls.email.invalid">
                <span>E-mail</span>
              </label>
              <label class="field">
                <input [type]="hide() ? 'password' : 'text'" formControlName="password" autocomplete="current-password" placeholder=" " required>
                <span>Senha</span>
                <button type="button" class="reveal" (click)="hide.set(!hide())" [attr.aria-label]="hide() ? 'Mostrar senha' : 'Ocultar senha'">
                  <mat-icon>{{ hide() ? 'visibility' : 'visibility_off' }}</mat-icon>
                </button>
              </label>
            </div>
            @if (form.controls.email.touched && form.controls.email.invalid) { <p class="hint">Informe um e-mail válido.</p> }
            @if (error()) { <p class="hint" role="alert">{{ error() }}</p> }

            <button class="primary" type="submit" [disabled]="loading()">
              @if (loading()) { <mat-spinner diameter="18"></mat-spinner> } @else { Continuar }
            </button>
          </form>

          <p class="fine">Acesso restrito a colaboradores autorizados. O login corporativo (SSO) está previsto para uma próxima versão.</p>
        </section>
      </main>

      <footer class="foot">Demand Hub · Uso interno</footer>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .signin-page {
      --ink: #1d1d1f; --ink-2: #6e6e73; --ink-3: #86868b; --line: #d2d2d7; --accent: #0071e3; --accent-hover: #0077ed;
      --font: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", Inter, "Segoe UI", Roboto, sans-serif;
      min-height: 100vh; display: flex; flex-direction: column; background: #f5f5f7; color: var(--ink); font-family: var(--font);
      -webkit-font-smoothing: antialiased;
    }

    .nav { height: 52px; display: flex; align-items: center; justify-content: space-between; padding: 0 max(24px, calc((100vw - 1120px) / 2 + 24px));
      background: rgba(251, 251, 253, .8); backdrop-filter: saturate(180%) blur(20px); -webkit-backdrop-filter: saturate(180%) blur(20px);
      border-bottom: 1px solid rgba(0, 0, 0, .06); position: sticky; top: 0; z-index: 2; }
    .brand { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; letter-spacing: -.2px; }
    .mark { width: 24px; height: 24px; border-radius: 7px; background: var(--ink); display: grid; place-items: center; }
    .mark mat-icon { font-size: 15px; width: 15px; height: 15px; color: #fff; }
    .org { font-size: 12px; color: var(--ink-2); }

    .main { flex: 1; width: 100%; max-width: 1120px; margin: 0 auto; padding: 72px 24px 48px; box-sizing: border-box;
      display: grid; grid-template-columns: 1fr 400px; gap: 96px; align-items: center; }

    .kicker { margin: 0 0 12px; font-size: 17px; font-weight: 600; color: var(--accent); letter-spacing: -.2px; }
    h1 { margin: 0; font-size: clamp(44px, 5.6vw, 72px); line-height: 1.04; font-weight: 700; letter-spacing: -.035em; }
    .lead { margin: 20px 0 48px; max-width: 480px; font-size: 21px; line-height: 1.4; color: var(--ink-2); letter-spacing: -.2px; font-weight: 400; }

    .stack { display: flex; flex-direction: column; gap: 12px; max-width: 440px; }
    .tile { display: flex; align-items: center; gap: 14px; padding: 16px 18px; background: #fff; border-radius: 18px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, .04), 0 8px 24px rgba(0, 0, 0, .05); animation: rise .7s cubic-bezier(.2, .8, .2, 1) both; }
    .t2 { margin-left: 28px; animation-delay: .08s; } .t3 { margin-left: 56px; animation-delay: .16s; }
    @keyframes rise { from { opacity: 0; transform: translateY(10px); } }
    .tile strong { display: block; font-size: 15px; font-weight: 600; letter-spacing: -.2px; }
    .tile span:not(.ico) { display: block; font-size: 13px; color: var(--ink-2); margin-top: 2px; }
    .ico { width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center; flex: none; }
    .ico mat-icon { font-size: 20px; width: 20px; height: 20px; color: #fff; }
    .ico.blue { background: #0071e3; } .ico.green { background: #34c759; } .ico.indigo { background: #5856d6; }

    .signin { background: #fff; border-radius: 22px; padding: 44px 40px 36px; box-shadow: 0 1px 2px rgba(0, 0, 0, .04), 0 20px 50px rgba(0, 0, 0, .08); }
    .signin h2 { margin: 0; font-size: 28px; font-weight: 600; letter-spacing: -.5px; }
    .sub { margin: 6px 0 28px; font-size: 15px; color: var(--ink-2); }

    .fields { border: 1px solid var(--line); border-radius: 12px; transition: border-color .2s; }
    .fields.invalid { border-color: #e30000; }
    .field { position: relative; display: block; }
    .field + .field { border-top: 1px solid var(--line); }
    .field input { width: 100%; box-sizing: border-box; height: 56px; padding: 20px 48px 6px 16px; border: 0; outline: 0; background: transparent;
      font: 400 17px var(--font); color: var(--ink); letter-spacing: -.2px; border-radius: inherit; }
    .field span { position: absolute; left: 16px; top: 18px; font-size: 17px; color: var(--ink-3); pointer-events: none; transition: all .18s ease; letter-spacing: -.2px; }
    .field input:focus + span, .field input:not(:placeholder-shown) + span { top: 8px; font-size: 12px; }
    .field:first-child { border-radius: 11px 11px 0 0; } .field:last-child { border-radius: 0 0 11px 11px; }
    .field:focus-within { box-shadow: 0 0 0 2px var(--accent); z-index: 1; }
    .field input:-webkit-autofill { -webkit-box-shadow: 0 0 0 40px #fff inset; -webkit-text-fill-color: var(--ink); }
    .reveal { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); width: 36px; height: 36px; border: 0; border-radius: 50%;
      background: transparent; cursor: pointer; display: grid; place-items: center; color: var(--ink-3); }
    .reveal:hover { background: #f5f5f7; color: var(--ink); }
    .reveal:focus-visible { outline: 2px solid var(--accent); }
    .reveal mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .hint { margin: 10px 2px 0; font-size: 13px; color: #e30000; }

    .primary { width: 100%; height: 50px; margin-top: 24px; border: 0; border-radius: 980px; background: var(--accent); color: #fff; cursor: pointer;
      font: 500 17px var(--font); letter-spacing: -.2px; display: grid; place-items: center; transition: background .2s; }
    .primary:hover:not([disabled]) { background: var(--accent-hover); }
    .primary:focus-visible { outline: 3px solid rgba(0, 113, 227, .4); outline-offset: 2px; }
    .primary[disabled] { opacity: .6; cursor: progress; }
    .primary mat-spinner { --mdc-circular-progress-active-indicator-color: #fff; }

    .fine { margin: 24px 0 0; font-size: 12px; line-height: 1.5; color: var(--ink-3); text-align: center; }
    .foot { text-align: center; padding: 20px; font-size: 12px; color: var(--ink-3); border-top: 1px solid rgba(0, 0, 0, .06); }

    @media (max-width: 960px) {
      .main { grid-template-columns: 1fr; gap: 40px; padding: 40px 16px 32px; max-width: 480px; }
      .lead { font-size: 17px; margin-bottom: 0; } .stack { display: none; } .org { display: none; }
      .signin { padding: 32px 24px 28px; }
    }
    @media (prefers-reduced-motion: reduce) { .tile { animation: none; } }
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
