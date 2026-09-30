import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { PlatformApi } from '../../core/api/platform-api.service';
import { Notification } from '../../core/models';
import { StateViewComponent } from '../../shared/state-view.component';

@Component({
  selector: 'dh-notifications',
  standalone: true,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, StateViewComponent],
  template: `
    <div class="page narrow">
      <div class="row"><h1>Notificações</h1><span class="spacer"></span>
        <button mat-stroked-button (click)="readAll()" [disabled]="!hasUnread()"><mat-icon>done_all</mat-icon> Marcar todas como lidas</button></div>
      <section class="card">
        <dh-state [loading]="loading()" [empty]="!items().length" emptyText="Sem notificações." emptyIcon="notifications_none">
          @for (n of items(); track n.id) {
            <div class="notif" [class.unread]="!n.readAt">
              <mat-icon>{{ n.readAt ? 'drafts' : 'mark_email_unread' }}</mat-icon>
              <div class="body">
                <div class="row"><strong>{{ n.subject }}</strong><span class="spacer"></span><span class="small muted">{{ n.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span></div>
                <div class="pre small">{{ n.body }}</div>
                <div class="row small">
                  @if (n.demandId) { <a [routerLink]="['/demands', n.demandId]" (click)="read(n)">Abrir demanda</a> }
                  @if (!n.readAt) { <button mat-button (click)="read(n)">Marcar como lida</button> }
                </div>
              </div>
            </div>
          }
        </dh-state>
      </section>
    </div>
  `,
  styles: [`.narrow { max-width: 900px; } .notif { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--dh-border); }
            .notif:last-child { border-bottom: 0; } .notif.unread .mat-icon { color: var(--dh-primary); } .body { flex: 1; min-width: 0; }`]
})
export class NotificationsComponent implements OnInit {
  private platform = inject(PlatformApi);
  items = signal<Notification[]>([]);
  loading = signal(true);

  ngOnInit(): void { this.load(); }
  hasUnread(): boolean { return this.items().some(n => !n.readAt); }

  load(): void {
    this.platform.notifications().subscribe({ next: n => { this.items.set(n); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  read(n: Notification): void { if (!n.readAt) this.platform.markRead(n.id).subscribe(() => this.load()); }
  readAll(): void { this.platform.markAllRead().subscribe(() => this.load()); }
}
