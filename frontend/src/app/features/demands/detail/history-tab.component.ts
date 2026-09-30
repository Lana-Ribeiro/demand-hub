import { Component, Input, OnChanges, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { DemandApi } from '../../../core/api/demand-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AuditLog, DemandDetail } from '../../../core/models';
import { StateViewComponent } from '../../../shared/state-view.component';

/** Linha do tempo de etapas (todos) e trilha de auditoria completa (usuários internos). */
@Component({
  selector: 'dh-history-tab',
  standalone: true,
  imports: [DatePipe, MatIconModule, StateViewComponent],
  template: `
    <div class="grid layout">
      <section class="card">
        <h3>Etapas</h3>
        <dh-state [empty]="!detail.stageHistory.length" emptyText="Sem histórico de etapas (demandas legadas não têm histórico recriado)." emptyIcon="timeline">
          <ol class="timeline">
            @for (h of detail.stageHistory; track h.enteredAt) {
              <li>
                <div class="row"><strong>{{ h.stageName }}</strong><span class="spacer"></span><span class="small muted">{{ h.enteredAt | date: 'dd/MM/yyyy HH:mm' }}</span></div>
                <div class="small muted">{{ h.action }} · {{ h.actorName }}@if (h.exitedAt) { · até {{ h.exitedAt | date: 'dd/MM HH:mm' }} }</div>
                @if (h.reason) { <div class="small">“{{ h.reason }}”</div> }
              </li>
            }
          </ol>
        </dh-state>
      </section>
      @if (isInternal()) {
        <section class="card">
          <h3>Auditoria</h3>
          <dh-state [loading]="loading()" [empty]="!logs().length" emptyText="Sem registros." emptyIcon="history">
            @for (l of logs(); track l.id) {
              <div class="log">
                <div class="row"><span class="chip neutral small mono">{{ l.action }}</span><strong>{{ l.actorName }}</strong>
                  @if (l.actorRoles) { <span class="small muted">({{ l.actorRoles }})</span> }
                  @if (l.aiSuggestionId) { <span class="chip info small">decisão sobre sugestão da IA</span> }
                  <span class="spacer"></span><span class="small muted">{{ l.createdAt | date: 'dd/MM/yyyy HH:mm:ss' }}</span></div>
                @if (l.field) { <div class="small">{{ l.field }}: <span class="muted">{{ l.beforeValue || '∅' }}</span> → <strong>{{ l.afterValue || '∅' }}</strong></div> }
                @if (l.reason) { <div class="small">Motivo: {{ l.reason }}</div> }
                @if (l.metadata) { <div class="small muted">{{ l.metadata }}</div> }
              </div>
            }
          </dh-state>
        </section>
      }
    </div>
  `,
  styles: [`.layout { grid-template-columns: 1fr 1.4fr; } @media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
            .timeline { list-style: none; padding: 0 0 0 14px; border-left: 2px solid var(--dh-border); margin: 0; }
            .timeline li { position: relative; padding: 0 0 14px 12px; } .timeline li::before { content: ''; position: absolute; left: -21px; top: 4px; width: 10px; height: 10px; border-radius: 50%; background: var(--dh-primary); }
            .log { padding: 8px 0; border-bottom: 1px solid var(--dh-border); } .log:last-child { border-bottom: 0; }`]
})
export class HistoryTabComponent implements OnChanges {
  private api = inject(DemandApi);
  private auth = inject(AuthService);

  @Input({ required: true }) detail!: DemandDetail;
  loading = signal(false);
  logs = signal<AuditLog[]>([]);
  isInternal = computed(() => this.auth.has('DEMAND_VIEW_ALL'));

  ngOnChanges(): void {
    if (!this.isInternal()) return;
    this.loading.set(true);
    this.api.audit(this.detail.summary.id).subscribe({ next: l => { this.logs.set(l); this.loading.set(false); }, error: () => this.loading.set(false) });
  }
}
