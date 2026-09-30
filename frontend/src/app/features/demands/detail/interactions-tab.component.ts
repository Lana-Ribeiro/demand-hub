import { Component, EventEmitter, Input, OnChanges, Output, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { forkJoin } from 'rxjs';
import { DemandApi } from '../../../core/api/demand-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Comment, DemandDetail, InformationRequest } from '../../../core/models';
import { NotifyService } from '../../../core/ui/notify.service';
import { StateViewComponent } from '../../../shared/state-view.component';

/** Pendências (solicitações de informação) e comentários. Comentários internos não são visíveis ao cliente. */
@Component({
  selector: 'dh-interactions-tab',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSlideToggleModule, StateViewComponent],
  template: `
    <div class="grid grid-2">
      <section class="card">
        <h3>Pendências</h3>
        <dh-state [loading]="loading()" [empty]="!requests().length" emptyText="Nenhuma solicitação de informação." emptyIcon="contact_support">
          @for (r of requests(); track r.id) {
            <div class="req" [class.open]="r.status === 'OPEN'">
              <div class="row"><mat-icon>{{ r.status === 'OPEN' ? 'help' : 'check_circle' }}</mat-icon>
                <strong>{{ r.question }}</strong></div>
              <div class="small muted">Solicitado por {{ r.requestedBy?.fullName }} em {{ r.requestedAt | date: 'dd/MM/yyyy HH:mm' }}</div>
              @if (r.status === 'ANSWERED') {
                <div class="answer"><div class="field-label">Resposta de {{ r.respondedBy?.fullName }} · {{ r.respondedAt | date: 'dd/MM HH:mm' }}</div><div class="pre">{{ r.response }}</div></div>
              } @else if (canAnswer()) {
                <mat-form-field class="full"><mat-label>Sua resposta</mat-label>
                  <textarea matInput rows="3" [formControl]="answerControl(r.id)"></textarea></mat-form-field>
                <div class="row"><span class="spacer"></span><button mat-flat-button color="primary" (click)="answer(r)">Responder</button></div>
              } @else {
                <span class="chip warning">Aguardando resposta do solicitante</span>
              }
            </div>
          }
        </dh-state>
      </section>

      <section class="card">
        <h3>Comentários</h3>
        <dh-state [loading]="loading()" [empty]="!comments().length" emptyText="Nenhum comentário." emptyIcon="forum">
          @for (c of comments(); track c.id) {
            <div class="comment">
              <div class="row"><strong>{{ c.authorName }}</strong>
                @if (c.source !== 'PLATFORM') { <span class="chip info small">via {{ c.source }}</span> }
                @if (c.visibility === 'INTERNAL') { <span class="chip neutral small">Interno</span> }
                <span class="spacer"></span><span class="small muted">{{ c.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span></div>
              <div class="pre">{{ c.body }}</div>
            </div>
          }
        </dh-state>
        @if (!detail.summary.readOnly || isInternal()) {
          <mat-form-field class="full" style="margin-top:12px"><mat-label>Novo comentário</mat-label>
            <textarea matInput rows="3" [formControl]="commentText"></textarea></mat-form-field>
          <div class="row">
            @if (isInternal()) { <mat-slide-toggle [formControl]="internal">Somente equipe interna</mat-slide-toggle> }
            <span class="spacer"></span>
            <button mat-flat-button color="primary" (click)="comment()" [disabled]="commentText.invalid">Comentar</button>
          </div>
        }
      </section>
    </div>
  `,
  styles: [`.req { border: 1px solid var(--dh-border); border-radius: 8px; padding: 12px; margin-bottom: 10px; }
            .req.open { border-color: var(--dh-warning); background: #fffcf5; }
            .answer { margin-top: 8px; background: #f7f8fc; border-radius: 6px; padding: 8px; }
            .comment { padding: 10px 0; border-bottom: 1px solid var(--dh-border); } .comment:last-child { border-bottom: 0; }`]
})
export class InteractionsTabComponent implements OnChanges {
  private api = inject(DemandApi);
  private auth = inject(AuthService);
  private notify = inject(NotifyService);

  @Input({ required: true }) detail!: DemandDetail;
  @Output() changed = new EventEmitter<void>();

  loading = signal(true);
  requests = signal<InformationRequest[]>([]);
  comments = signal<Comment[]>([]);
  commentText = new FormControl('', [Validators.required, Validators.maxLength(8000)]);
  internal = new FormControl(false);
  private answers = new Map<string, FormControl<string | null>>();

  isInternal = computed(() => this.auth.has('DEMAND_VIEW_ALL'));
  canAnswer = computed(() => this.detail.summary.requester?.id === this.auth.user()?.id && this.auth.has('DEMAND_RESPOND'));

  ngOnChanges(): void { this.load(); }

  load(): void {
    forkJoin({ requests: this.api.informationRequests(this.detail.summary.id), comments: this.api.comments(this.detail.summary.id) })
      .subscribe({ next: r => { this.requests.set(r.requests); this.comments.set(r.comments); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  answerControl(id: string): FormControl<string | null> {
    if (!this.answers.has(id)) this.answers.set(id, new FormControl('', [Validators.required]));
    return this.answers.get(id)!;
  }

  answer(r: InformationRequest): void {
    const c = this.answerControl(r.id);
    if (c.invalid) { c.markAsTouched(); return; }
    this.api.answer(this.detail.summary.id, r.id, c.value ?? '').subscribe({
      next: () => { this.notify.success('Resposta enviada ao PMO.'); this.load(); this.changed.emit(); },
      error: err => this.notify.error(err)
    });
  }

  comment(): void {
    this.api.addComment(this.detail.summary.id, this.commentText.value ?? '', this.internal.value ? 'INTERNAL' : 'PUBLIC').subscribe({
      next: () => { this.commentText.reset(''); this.load(); },
      error: err => this.notify.error(err)
    });
  }
}
