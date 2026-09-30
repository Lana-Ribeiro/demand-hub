import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { DemandApi } from '../../core/api/demand-api.service';
import { PendingApproval } from '../../core/models';
import { ROLE_LABELS } from '../../core/labels';
import { NotifyService } from '../../core/ui/notify.service';
import { PriorityBadgeComponent, ProjectBadgeComponent, StageChipComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';
import { ReasonDialogComponent, ReasonDialogData } from '../../shared/reason-dialog.component';

/** Caixa de aprovações pendentes dos papéis do usuário (diretor, gestor, arquiteto, PMO). */
@Component({
  selector: 'dh-approvals',
  standalone: true,
  imports: [RouterLink, DatePipe, CurrencyPipe, MatButtonModule, MatIconModule, StateViewComponent, ProjectBadgeComponent,
    PriorityBadgeComponent, StageChipComponent],
  template: `
    <div class="page">
      <h1>Aprovações pendentes</h1>
      <p class="muted">Demandas aguardando a decisão dos seus papéis. Com todas as aprovações concluídas, a demanda avança automaticamente.</p>
      <dh-state [loading]="loading()" [error]="error()" (retry)="load()" [empty]="!items().length" emptyText="Nenhuma aprovação pendente." emptyIcon="task_alt">
        @for (p of items(); track p.approval.id) {
          <article class="card appr">
            <div class="row">
              <span class="chip warning">{{ role(p.approval.approverRole) }}</span>
              <span class="small muted">{{ p.approval.ruleName }}</span>
              <span class="spacer"></span>
              <span class="small muted">Solicitada {{ p.approval.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
            </div>
            <h3><a [routerLink]="['/demands', p.demand.id]">{{ p.demand.protocol }} — {{ p.demand.title }}</a></h3>
            <div class="row small">
              <dh-project-badge [project]="p.demand.project"></dh-project-badge>
              <dh-priority-badge [priority]="p.demand.priority"></dh-priority-badge>
              <dh-stage-chip [stage]="p.demand.stage"></dh-stage-chip>
              <span class="muted">Solicitante: {{ p.demand.requester?.fullName }}</span>
            </div>
            <div class="row actions">
              <a mat-button [routerLink]="['/demands', p.demand.id]"><mat-icon>visibility</mat-icon> Ver demanda completa</a>
              <span class="spacer"></span>
              <button mat-stroked-button color="warn" (click)="decide(p, false)">Rejeitar</button>
              <button mat-flat-button color="primary" (click)="decide(p, true)">Aprovar</button>
            </div>
          </article>
        }
      </dh-state>
    </div>
  `,
  styles: [`.appr h3 { margin: 10px 0 6px; font-size: 16px; } .appr h3 a { color: inherit; } .actions { margin-top: 10px; }`]
})
export class ApprovalsComponent implements OnInit {
  private api = inject(DemandApi);
  private notify = inject(NotifyService);
  private dialog = inject(MatDialog);

  items = signal<PendingApproval[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.pendingApprovals().subscribe({
      next: i => { this.items.set(i); this.loading.set(false); this.error.set(null); },
      error: () => { this.error.set('Não foi possível carregar as aprovações.'); this.loading.set(false); }
    });
  }

  role(r: string): string { return ROLE_LABELS[r] ?? r; }

  decide(p: PendingApproval, approve: boolean): void {
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '520px', data: { title: `${approve ? 'Aprovar' : 'Rejeitar'} ${p.demand.protocol}`, required: !approve,
        label: approve ? 'Comentário (opcional)' : 'Motivo da rejeição *', confirmText: approve ? 'Aprovar' : 'Rejeitar', danger: !approve }
    }).afterClosed().subscribe(comment => {
      if (comment === undefined) return;
      this.api.decideApproval(p.approval.id, approve, comment || undefined).subscribe({
        next: () => { this.notify.success('Decisão registrada.'); this.load(); },
        error: err => this.notify.error(err)
      });
    });
  }
}
