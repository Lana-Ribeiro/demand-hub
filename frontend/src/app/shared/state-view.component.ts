import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

/** Estados padronizados de carregamento, erro e vazio. O conteúdo é exibido quando nenhum deles se aplica. */
@Component({
  selector: 'dh-state',
  standalone: true,
  imports: [MatProgressSpinnerModule, MatIconModule, MatButtonModule],
  template: `
    @if (loading) {
      <div class="state" role="status" aria-live="polite">
        <mat-spinner diameter="28"></mat-spinner><span class="muted">{{ loadingText }}</span>
      </div>
    } @else if (error) {
      <div class="state" role="alert">
        <mat-icon class="err">error_outline</mat-icon>
        <span>{{ error }}</span>
        <button mat-stroked-button (click)="retry.emit()">Tentar novamente</button>
      </div>
    } @else if (empty) {
      <div class="state">
        <mat-icon class="muted">{{ emptyIcon }}</mat-icon>
        <span class="muted">{{ emptyText }}</span>
        <ng-content select="[empty-action]"></ng-content>
      </div>
    } @else {
      <ng-content></ng-content>
    }
  `,
  styles: [`.state{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:32px 16px;text-align:center}
            .state .mat-icon{font-size:32px;width:32px;height:32px}.err{color:var(--dh-danger)}`]
})
export class StateViewComponent {
  @Input() loading = false;
  @Input() error: string | null = null;
  @Input() empty = false;
  @Input() emptyText = 'Nada por aqui ainda.';
  @Input() emptyIcon = 'inbox';
  @Input() loadingText = 'Carregando…';
  @Output() retry = new EventEmitter<void>();
}
