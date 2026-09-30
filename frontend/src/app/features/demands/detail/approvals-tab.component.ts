import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { DemandApi } from '../../../core/api/demand-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ApprovalView, DemandDetail } from '../../../core/models';
import { ROLE_LABELS } from '../../../core/labels';
import { NotifyService } from '../../../core/ui/notify.service';
import { StateViewComponent } from '../../../shared/state-view.component';
import { ReasonDialogComponent, ReasonDialogData } from '../../../shared/reason-dialog.component';

/** Approval gates da demanda. Decidir é permitido somente a quem possui o papel aprovador. */
@Component({
  selector: 'dh-approvals-tab',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, StateViewComponent],
  template: `
    <section class="card">
      <h3>Aprovações</h3>
      <p class="small muted">As regras de aprovação são configuráveis (Administração → Workflows). Aprovações pendentes bloqueiam o avanço da demanda.</p>
      <dh-state [loading]="loading()" [empty]="!approvals().length" emptyText="Nenhuma aprovação registrada." emptyIcon="task_alt">
        @for (a of approvals(); track a.id) {
          <div class="appr">
            <mat-icon [class]="a.status">{{ icon(a.status) }}</mat-icon>
            <div class="info">
              <div><strong>{{ role(a.approverRole) }}</strong> · <span class="muted">{{ a.ruleName }}</span></div>
              <div class="small muted">Estágio {{ a.stageCode }} · solicitada {{ a.createdAt | date: 'dd/MM/yyyy HH:mm' }}</div>
              @if (a.decidedAt) {
                <div class="small">{{ statusLabel(a.status) }} por {{ a.decidedBy?.fullName || 'sistema' }} em {{ a.decidedAt | date: 'dd/MM/yyyy HH:mm' }}
                  @if (a.comment) { — “{{ a.comment }}” }</div>
              }
            </div>
            @if (a.status === 'PENDING' && canDecide(a)) {
              <button mat-flat-button color="primary" (click)="decide(a, true)">Aprovar</button>
              <button mat-stroked-button color="warn" (click)="decide(a, false)">Rejeitar</button>
            } @else if (a.status === 'PENDING') {
              <span class="chip warning">Pendente</span>
            }
          </div>
        }
      </dh-state>
    </section>
  `,
  styles: [`.appr { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--dh-border); flex-wrap: wrap; }
            .appr:last-child { border-bottom: 0; } .info { flex: 1; min-width: 220px; }
            .APPROVED { color: var(--dh-success); } .REJECTED { color: var(--dh-danger); } .PENDING { color: var(--dh-warning); } .CANCELLED { color: var(--dh-muted); }`]
})
export class ApprovalsTabComponent implements OnChanges {
  private api = inject(DemandApi);
  private auth = inject(AuthService);
  private notify = inject(NotifyService);
  private dialog = inject(MatDialog);

  @Input({ required: true }) detail!: DemandDetail;
  @Output() changed = new EventEmitter<void>();

  loading = signal(true);
  approvals = signal<ApprovalView[]>([]);

  ngOnChanges(): void {
    this.api.approvals(this.detail.summary.id).subscribe({ next: a => { this.approvals.set(a); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  canDecide(a: ApprovalView): boolean { return this.auth.has('APPROVAL_DECIDE') && this.auth.hasRole(a.approverRole); }
  role(r: string): string { return ROLE_LABELS[r] ?? r; }
  icon(s: string): string { return ({ APPROVED: 'check_circle', REJECTED: 'cancel', PENDING: 'hourglass_top', CANCELLED: 'block' } as Record<string, string>)[s] ?? 'help'; }
  statusLabel(s: string): string { return ({ APPROVED: 'Aprovado', REJECTED: 'Rejeitado', CANCELLED: 'Cancelado', PENDING: 'Pendente' } as Record<string, string>)[s] ?? s; }

  decide(a: ApprovalView, approve: boolean): void {
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '520px', data: { title: approve ? 'Aprovar demanda' : 'Rejeitar demanda', label: approve ? 'Comentário (opcional)' : 'Motivo da rejeição *',
        required: !approve, confirmText: approve ? 'Aprovar' : 'Rejeitar', danger: !approve,
        message: approve ? 'Com todas as aprovações concluídas, a demanda avança automaticamente.' : 'A rejeição encerra a demanda conforme o workflow.' }
    }).afterClosed().subscribe(comment => {
      if (comment === undefined) return;
      this.api.decideApproval(a.id, approve, comment || undefined).subscribe({
        next: () => { this.notify.success(approve ? 'Aprovação registrada.' : 'Rejeição registrada.'); this.changed.emit(); },
        error: err => this.notify.error(err)
      });
    });
  }
}
