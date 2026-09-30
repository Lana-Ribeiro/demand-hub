import { Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CATEGORY_LABELS, LIFECYCLE_LABELS } from '../core/labels';
import { PriorityRef, ProjectRef, StageRef } from '../core/models';

/** Identidade do projeto: SEMPRE código + nome, cor apenas como reforço visual. */
@Component({
  selector: 'dh-project-badge',
  standalone: true,
  template: `
    @if (project) {
      <span class="project" [title]="project.name">
        <span class="dot" [style.background]="project.color" aria-hidden="true"></span>
        <strong>{{ project.code }}</strong>@if (!compact) {<span class="muted"> · {{ project.name }}</span>}
      </span>
    } @else {
      <span class="muted small">Sem projeto</span>
    }
  `,
  styles: [`.project{display:inline-flex;align-items:center;gap:6px;font-size:13px}.dot{width:10px;height:10px;border-radius:3px;flex:none}`]
})
export class ProjectBadgeComponent {
  @Input() project?: ProjectRef | null;
  @Input() compact = false;
}

@Component({
  selector: 'dh-priority-badge',
  standalone: true,
  template: `
    @if (priority) {
      <span class="chip" [style.background]="priority.color + '1f'" [style.color]="priority.color" [title]="priority.name">
        {{ priority.code }} · {{ priority.name }}
      </span>
    } @else {
      <span class="chip neutral">Sem prioridade</span>
    }
  `
})
export class PriorityBadgeComponent {
  @Input() priority?: PriorityRef | null;
}

@Component({
  selector: 'dh-stage-chip',
  standalone: true,
  template: `
    @if (stage) {
      <span class="chip" [class]="'chip ' + tone(stage.category)" [title]="categoryLabel">{{ stage.name }}</span>
    } @else if (lifecycle) {
      <span class="chip" [class]="'chip ' + (lifecycle === 'LEGACY' ? 'neutral' : 'info')">{{ lifecycleLabel }}</span>
    }
  `
})
export class StageChipComponent {
  @Input() stage?: StageRef | null;
  @Input() lifecycle?: string | null;

  get categoryLabel(): string { return this.stage ? CATEGORY_LABELS[this.stage.category] ?? '' : ''; }
  get lifecycleLabel(): string { return this.lifecycle ? LIFECYCLE_LABELS[this.lifecycle] ?? this.lifecycle : ''; }

  tone(category: string): string {
    switch (category) {
      case 'DONE': return 'success';
      case 'REJECTED': case 'CANCELLED': return 'danger';
      case 'ON_HOLD': case 'APPROVAL': return 'warning';
      case 'DRAFT': return 'neutral';
      case 'EXECUTION': case 'READY': case 'DOCUMENTATION': return 'info';
      default: return '';
    }
  }
}

/** Selo explícito de modo MOCK — integrações/IA simuladas nunca são apresentadas como reais. */
@Component({
  selector: 'dh-mode-badge',
  standalone: true,
  imports: [MatIconModule, MatTooltipModule],
  template: `
    @if (mode === 'MOCK') {
      <span class="chip mock" matTooltip="Simulação local para desenvolvimento — não é a integração/IA real">
        <mat-icon style="font-size:14px;width:14px;height:14px">science</mat-icon> MOCK
      </span>
    } @else if (mode === 'REAL') {
      <span class="chip success">REAL</span>
    } @else if (mode === 'DISABLED') {
      <span class="chip neutral">Desabilitada</span>
    }
  `
})
export class ModeBadgeComponent {
  @Input() mode?: string | null;
}
