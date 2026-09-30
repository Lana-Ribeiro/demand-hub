import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AiApi } from '../../core/api/ai-api.service';
import { ChatMessage } from '../../core/models';
import { NotifyService } from '../../core/ui/notify.service';
import { ModeBadgeComponent } from '../../shared/badges';

/** Assistente de abertura: conversa persistida; sugestões geradas aparecem no painel de sugestões para aceite. */
@Component({
  selector: 'dh-intake-chat',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, ModeBadgeComponent],
  template: `
    <div class="chat">
      <div class="row head"><mat-icon>smart_toy</mat-icon><strong>Assistente de abertura</strong><span class="spacer"></span><dh-mode-badge [mode]="mode()"></dh-mode-badge></div>
      <div class="messages" #scroller aria-live="polite">
        @if (!messages().length) {
          <div class="bubble assistant">
            Olá! Posso ajudar a descrever sua iniciativa. Conte em poucas palavras o que você precisa — por exemplo:
            <em>"Quero criar uma iniciativa para automatizar o processo X."</em><br><br>
            Eu farei perguntas e proporei preenchimentos como <strong>sugestões</strong>; você decide o que aplicar.
          </div>
        }
        @for (m of messages(); track m.id) {
          <div class="bubble" [class.user]="m.role === 'USER'" [class.assistant]="m.role === 'ASSISTANT'">{{ m.content }}</div>
        }
      </div>
      @if (sending()) { <mat-progress-bar mode="indeterminate"></mat-progress-bar> }
      @if (disabled) {
        <p class="small muted">{{ disabledReason }}</p>
      } @else {
        <form class="input" (ngSubmit)="send()">
          <mat-form-field class="full">
            <mat-label>Mensagem</mat-label>
            <textarea matInput [formControl]="text" rows="2" (keydown.enter)="onEnter($event)" maxlength="4000"></textarea>
          </mat-form-field>
          <button mat-icon-button color="primary" type="submit" [disabled]="sending() || text.invalid" aria-label="Enviar mensagem"><mat-icon>send</mat-icon></button>
        </form>
      }
    </div>
  `,
  styles: [`
    .chat { display: flex; flex-direction: column; height: 100%; }
    .head { margin-bottom: 8px; } .head .mat-icon { color: var(--dh-primary); }
    .messages { flex: 1; overflow-y: auto; min-height: 240px; max-height: 52vh; display: flex; flex-direction: column; gap: 8px; padding: 4px; }
    .bubble { padding: 10px 12px; border-radius: 12px; max-width: 90%; white-space: pre-wrap; line-height: 1.45; }
    .assistant { background: #f1f3fb; align-self: flex-start; border-top-left-radius: 4px; }
    .user { background: var(--dh-primary); color: #fff; align-self: flex-end; border-top-right-radius: 4px; }
    .input { display: flex; align-items: center; gap: 4px; margin-top: 8px; }
  `]
})
export class IntakeChatComponent implements OnChanges {
  private ai = inject(AiApi);
  private notify = inject(NotifyService);

  @Input() demandId: string | null = null;
  @Input() disabled = false;
  @Input() disabledReason = '';
  /** Chamado antes de enviar: garante que o rascunho exista e esteja salvo. Deve retornar o id. */
  @Input() ensureDraft?: () => Promise<string>;
  @Output() suggestionsChanged = new EventEmitter<void>();
  @ViewChild('scroller') scroller?: ElementRef<HTMLDivElement>;

  messages = signal<ChatMessage[]>([]);
  sending = signal(false);
  mode = signal<string | null>(null);
  text = new FormControl('', [Validators.required, Validators.maxLength(4000)]);

  ngOnChanges(): void {
    if (this.demandId) {
      this.ai.chat(this.demandId).subscribe({ next: m => { this.messages.set(m); this.scroll(); }, error: () => {} });
    }
  }

  onEnter(e: Event): void {
    const ke = e as KeyboardEvent;
    if (!ke.shiftKey) { ke.preventDefault(); this.send(); }
  }

  async send(): Promise<void> {
    const message = (this.text.value ?? '').trim();
    if (!message || this.sending()) return;
    this.sending.set(true);
    try {
      const id = this.demandId ?? (this.ensureDraft ? await this.ensureDraft() : null);
      if (!id) { this.sending.set(false); return; }
      this.demandId = id;
      const optimistic: ChatMessage = { id: 'tmp', role: 'USER', content: message, createdAt: new Date().toISOString() };
      this.messages.update(m => [...m, optimistic]);
      this.text.reset('');
      this.scroll();
      this.ai.send(id, message).subscribe({
        next: reply => {
          this.mode.set(reply.mode);
          this.ai.chat(id).subscribe(m => { this.messages.set(m); this.scroll(); });
          if (reply.suggestions.length) this.suggestionsChanged.emit();
          this.sending.set(false);
        },
        error: err => { this.notify.error(err); this.sending.set(false); }
      });
    } catch (err) {
      this.notify.error(err);
      this.sending.set(false);
    }
  }

  private scroll(): void {
    setTimeout(() => { const el = this.scroller?.nativeElement; if (el) el.scrollTop = el.scrollHeight; });
  }
}
