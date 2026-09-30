import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin, of, catchError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DemandApi } from '../../core/api/demand-api.service';
import { DemandSummary, PendingApproval } from '../../core/models';
import { StageChipComponent, ProjectBadgeComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';

@Component({
  selector: 'dh-home',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, DatePipe, StageChipComponent, ProjectBadgeComponent, StateViewComponent],
  template: `
    <div class="page">
      <div class="row header">
        <div>
          <h1>Olá, {{ firstName() }}</h1>
          <p class="muted">Acompanhe suas demandas e o que precisa da sua atenção.</p>
        </div>
        <span class="spacer"></span>
        @if (auth.has('DEMAND_CREATE')) {
          <a mat-flat-button color="primary" routerLink="/demands/new"><mat-icon>add</mat-icon> Nova demanda</a>
        }
      </div>

      <dh-state [loading]="loading()" [error]="error()" (retry)="load()">
        <div class="grid grid-4">
          <a class="card kpi" routerLink="/demands/mine">
            <mat-icon>edit_note</mat-icon><div><div class="kpi-value">{{ drafts().length }}</div><div class="muted">Rascunhos</div></div>
          </a>
          <a class="card kpi" routerLink="/demands/mine">
            <mat-icon>pending_actions</mat-icon><div><div class="kpi-value">{{ onHold().length }}</div><div class="muted">Aguardando sua resposta</div></div>
          </a>
          <a class="card kpi" routerLink="/demands/mine">
            <mat-icon>autorenew</mat-icon><div><div class="kpi-value">{{ active().length }}</div><div class="muted">Em andamento</div></div>
          </a>
          @if (auth.has('APPROVAL_DECIDE')) {
            <a class="card kpi warn" routerLink="/approvals">
              <mat-icon>task_alt</mat-icon><div><div class="kpi-value">{{ approvals().length }}</div><div class="muted">Aprovações pendentes</div></div>
            </a>
          } @else {
            <a class="card kpi" routerLink="/demands/mine">
              <mat-icon>check_circle</mat-icon><div><div class="kpi-value">{{ done().length }}</div><div class="muted">Concluídas</div></div>
            </a>
          }
        </div>

        @if (onHold().length) {
          <div class="alert warning" style="margin-top:16px">
            <mat-icon>priority_high</mat-icon>
            <div>O PMO solicitou informações em {{ onHold().length }} demanda(s). Abra a demanda e responda a pendência para que ela volte à triagem.</div>
          </div>
        }

        <div class="grid grid-2" style="margin-top:16px">
          <section class="card">
            <h3>Minhas demandas recentes</h3>
            <dh-state [empty]="!mine().length" emptyText="Você ainda não abriu demandas.">
              @for (d of mine().slice(0, 6); track d.id) {
                <a class="item" [routerLink]="d.lifecycleState === 'DRAFT' ? ['/demands', d.id, 'edit'] : ['/demands', d.id]">
                  <div class="item-main">
                    <div><strong>{{ d.title || '(sem título)' }}</strong></div>
                    <div class="small muted">{{ d.protocol || 'Rascunho' }} · atualizada {{ d.updatedAt | date: 'dd/MM/yyyy HH:mm' }}</div>
                  </div>
                  <dh-stage-chip [stage]="d.stage" [lifecycle]="d.lifecycleState"></dh-stage-chip>
                </a>
              }
            </dh-state>
          </section>

          @if (auth.has('APPROVAL_DECIDE')) {
            <section class="card">
              <h3>Aguardando sua aprovação</h3>
              <dh-state [empty]="!approvals().length" emptyText="Nenhuma aprovação pendente." emptyIcon="task_alt">
                @for (p of approvals().slice(0, 6); track p.approval.id) {
                  <a class="item" [routerLink]="['/demands', p.demand.id]">
                    <div class="item-main">
                      <div><strong>{{ p.demand.title }}</strong></div>
                      <div class="small muted">{{ p.demand.protocol }} · {{ p.approval.ruleName }}</div>
                    </div>
                    <dh-project-badge [project]="p.demand.project" [compact]="true"></dh-project-badge>
                  </a>
                }
              </dh-state>
            </section>
          } @else {
            <section class="card">
              <h3>Como funciona</h3>
              <ol class="steps">
                <li><strong>Abra a demanda</strong> pelo formulário guiado; o assistente de IA ajuda a preencher.</li>
                <li><strong>Anexe a documentação</strong> e a apresentação padrão — a IA sugere campos e aponta divergências.</li>
                <li><strong>O PMO triará</strong> e decidirá; aprovações necessárias acontecem na plataforma.</li>
                <li><strong>Acompanhe cada etapa</strong> até a conclusão e receba a documentação da entrega.</li>
              </ol>
            </section>
          }
        </div>
      </dh-state>
    </div>
  `,
  styles: [`
    .header h1 { margin: 0; font-size: 22px; }
    .kpi { display: flex; align-items: center; gap: 14px; text-decoration: none; color: inherit; }
    .kpi .mat-icon { color: var(--dh-primary); font-size: 30px; width: 30px; height: 30px; }
    .kpi.warn .mat-icon { color: var(--dh-warning); }
    .kpi-value { font-size: 26px; font-weight: 700; }
    .item { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--dh-border); text-decoration: none; color: inherit; }
    .item:last-child { border-bottom: 0; }
    .item-main { flex: 1; min-width: 0; }
    .steps li { margin: 8px 0; }
  `]
})
export class HomeComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(DemandApi);

  loading = signal(true);
  error = signal<string | null>(null);
  mine = signal<DemandSummary[]>([]);
  approvals = signal<PendingApproval[]>([]);

  firstName = computed(() => (this.auth.user()?.fullName ?? '').split(' ')[0]);
  drafts = computed(() => this.mine().filter(d => d.lifecycleState === 'DRAFT'));
  onHold = computed(() => this.mine().filter(d => d.lifecycleState === 'ON_HOLD'));
  active = computed(() => this.mine().filter(d => d.lifecycleState === 'ACTIVE'));
  done = computed(() => this.mine().filter(d => d.lifecycleState === 'COMPLETED'));

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      mine: this.api.mine(),
      approvals: this.auth.has('APPROVAL_DECIDE') ? this.api.pendingApprovals().pipe(catchError(() => of([]))) : of([] as PendingApproval[])
    }).subscribe({
      next: r => { this.mine.set(r.mine); this.approvals.set(r.approvals); this.loading.set(false); },
      error: () => { this.error.set('Não foi possível carregar suas informações.'); this.loading.set(false); }
    });
  }
}
