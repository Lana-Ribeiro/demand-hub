import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DatePipe, PercentPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ITEM_TYPE_LABELS, SOURCE_LABELS, confidenceLabel } from '../core/labels';
import { AiSuggestion } from '../core/models';
import { ReasonDialogComponent, ReasonDialogData } from './reason-dialog.component';

export interface SuggestionDecision {
  suggestion: AiSuggestion;
  action: 'ACCEPT' | 'EDIT' | 'REJECT';
  value?: string;
  reason?: string;
}

/**
 * Sugestões da IA com decisão humana explícita (aceitar, editar, rejeitar).
 * Sempre exibe origem, confiança e justificativa; nada é aplicado sem ação do usuário.
 */
@Component({
  selector: 'dh-suggestion-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, PercentPipe, DatePipe],
  template: `
    @for (s of suggestions; track s.id) {
      <article class="sugg" [class.inconsistency]="s.kind === 'INCONSISTENCY'" [attr.aria-label]="'Sugestão para ' + label(s)">
        <header class="row">
          <mat-icon class="kind-icon">{{ icon(s) }}</mat-icon>
          <strong>{{ title(s) }}</strong>
          <span class="chip neutral small">Sugestão da IA</span>
          <span class="spacer"></span>
          <span class="muted small" [matTooltip]="((s.confidence ?? 0) | percent) ?? ''">Confiança: {{ confidence(s) }}</span>
        </header>

        @if (s.kind === 'INCONSISTENCY') {
          <div class="compare">
            <div><div class="field-label">Formulário / valor atual</div><div class="field-value">{{ s.alternativeValue || '—' }}</div></div>
            <div><div class="field-label">{{ source(s) }}</div><div class="field-value">{{ s.suggestedValue }}</div></div>
          </div>
        } @else if (s.kind === 'DUPLICATE') {
          <div class="field-value"><strong>{{ s.suggestedValue }}</strong> — {{ s.alternativeValue }}</div>
        } @else {
          <div class="field-value value">{{ s.suggestedValue }}</div>
          @if (s.kind === 'FIELD_VALUE' && s.alternativeValue) {
            <div class="muted small">Valor atual: {{ s.alternativeValue }}</div>
          }
        }

        <footer class="muted small">
          Origem: {{ source(s) }} · Agente: {{ s.agent }} · {{ s.createdAt | date: 'dd/MM HH:mm' }}
          @if (s.rationale) { <div class="rationale">{{ s.rationale }}</div> }
        </footer>

        @if (s.status === 'PENDING' && !readOnly) {
          <div class="row actions">
            <button mat-flat-button color="primary" (click)="emit(s, 'ACCEPT')">{{ acceptLabel(s) }}</button>
            @if (s.kind === 'FIELD_VALUE' || s.kind === 'INCONSISTENCY' || s.kind === 'REFINEMENT_ITEM') {
              <button mat-stroked-button (click)="edit(s)">Editar e aplicar</button>
            }
            <button mat-button (click)="reject(s)">{{ rejectLabel(s) }}</button>
          </div>
        } @else if (s.status !== 'PENDING') {
          <div class="small muted">Decisão: <strong>{{ s.status }}</strong>@if (s.finalValue) { → {{ s.finalValue }} }</div>
        }
      </article>
    }
  `,
  styles: [`
    .sugg{border:1px solid var(--dh-border);border-left:3px solid var(--dh-primary);border-radius:8px;padding:12px;margin-bottom:10px;background:#fff}
    .sugg.inconsistency{border-left-color:var(--dh-warning)}
    .kind-icon{color:var(--dh-primary)} .inconsistency .kind-icon{color:var(--dh-warning)}
    .value{margin:6px 0;max-height:160px;overflow:auto}
    .compare{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:8px 0}
    @media(max-width:600px){.compare{grid-template-columns:1fr}}
    .rationale{margin-top:4px;font-style:italic}
    .actions{margin-top:8px}
  `]
})
export class SuggestionListComponent {
  private dialog = inject(MatDialog);

  @Input() suggestions: AiSuggestion[] = [];
  @Input() fieldLabels: Record<string, string> = {};
  @Input() staff = false;
  @Input() readOnly = false;
  @Output() decide = new EventEmitter<SuggestionDecision>();

  label(s: AiSuggestion): string {
    if (s.kind === 'REFINEMENT_ITEM') {
      return s.field?.startsWith('DECISION:') ? 'Decisão' : ITEM_TYPE_LABELS[s.field ?? ''] ?? s.field ?? '';
    }
    return s.field ? this.fieldLabels[s.field] ?? s.field : '';
  }

  title(s: AiSuggestion): string {
    switch (s.kind) {
      case 'INCONSISTENCY': return `Divergência: ${this.label(s)}`;
      case 'MISSING_INFO': return `Informação faltante${s.field ? ': ' + this.label(s) : ''}`;
      case 'DUPLICATE': return 'Possível duplicidade';
      case 'REFINEMENT_ITEM': return this.label(s);
      default: return this.label(s);
    }
  }

  icon(s: AiSuggestion): string {
    return { INCONSISTENCY: 'compare_arrows', MISSING_INFO: 'help_outline', DUPLICATE: 'content_copy', REFINEMENT_ITEM: 'checklist', FIELD_VALUE: 'auto_awesome' }[s.kind];
  }

  source(s: AiSuggestion): string { return SOURCE_LABELS[s.sourceType] ?? s.sourceType; }
  confidence(s: AiSuggestion): string { return confidenceLabel(s.confidence); }

  acceptLabel(s: AiSuggestion): string {
    switch (s.kind) {
      case 'INCONSISTENCY': return `Usar valor da ${this.source(s).toLowerCase()}`;
      case 'MISSING_INFO': return this.staff ? 'Converter em pendência' : 'Ciente';
      case 'DUPLICATE': return 'Confirmar duplicidade';
      case 'REFINEMENT_ITEM': return 'Registrar';
      default: return 'Aceitar';
    }
  }

  rejectLabel(s: AiSuggestion): string {
    return s.kind === 'INCONSISTENCY' ? 'Manter valor atual' : s.kind === 'DUPLICATE' ? 'Não é duplicidade' : 'Rejeitar';
  }

  emit(s: AiSuggestion, action: 'ACCEPT' | 'EDIT' | 'REJECT', value?: string, reason?: string): void {
    this.decide.emit({ suggestion: s, action, value, reason });
  }

  edit(s: AiSuggestion): void {
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '560px', data: { title: `Editar sugestão — ${this.label(s)}`, label: 'Valor a aplicar', required: true, initial: s.suggestedValue, confirmText: 'Aplicar' }
    }).afterClosed().subscribe(value => { if (value) this.emit(s, 'EDIT', value); });
  }

  reject(s: AiSuggestion): void {
    this.dialog.open<ReasonDialogComponent, ReasonDialogData, string>(ReasonDialogComponent, {
      width: '480px', data: { title: this.rejectLabel(s), label: 'Motivo (opcional)', confirmText: 'Confirmar' }
    }).afterClosed().subscribe(reason => { if (reason !== undefined) this.emit(s, 'REJECT', undefined, reason || undefined); });
  }
}
