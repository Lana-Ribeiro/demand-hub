import { Component, Input, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { Catalog, DemandDetail, FieldMeta } from '../../../core/models';
import { IMPACT_LABELS, SECTION_LABELS, URGENCY_LABELS } from '../../../core/labels';
import { DocumentsPanelComponent } from '../documents-panel.component';

/** Visão completa: todos os dados originais do formulário por seção, documentos e dados de legado. */
@Component({
  selector: 'dh-full-tab',
  standalone: true,
  imports: [DatePipe, MatIconModule, DocumentsPanelComponent],
  template: `
    @if (detail.summary.legacy; as l) {
      <section class="card">
        <h3>Dados originais (legado)</h3>
        <p class="small muted">Demanda importada de fonte histórica. Nenhum histórico de etapas foi recriado; somente os dados existentes são exibidos.</p>
        <div class="grid grid-4">
          <div><div class="field-label">ID original</div><div class="field-value">{{ l.originalId || '—' }}</div></div>
          <div><div class="field-label">Criada em (original)</div><div class="field-value">{{ l.originalCreatedAt ? (l.originalCreatedAt | date: 'dd/MM/yyyy') : '—' }}</div></div>
          <div><div class="field-label">Status original</div><div class="field-value">{{ l.originalStatus || '—' }}</div></div>
          <div><div class="field-label">Responsável original</div><div class="field-value">{{ l.originalOwner || '—' }}</div></div>
        </div>
      </section>
    }
    @for (s of sections(); track s) {
      <section class="card">
        <h3>{{ label(s) }}</h3>
        <div class="grid grid-2">
          @for (f of fieldsOf(s); track f.key) {
            <div>
              <div class="field-label">{{ f.label }}</div>
              <div class="field-value" [class.empty]="!detail.fields[f.key]">{{ display(f) }}</div>
            </div>
          }
        </div>
      </section>
    }
    <section class="card">
      <h3>Documentos e apresentação</h3>
      <dh-documents-panel [demandId]="detail.summary.id" [canUpload]="canUpload()"></dh-documents-panel>
    </section>
  `
})
export class FullTabComponent {
  private catalogSignal = signal<Catalog | null>(null);
  @Input({ required: true }) detail!: DemandDetail;
  @Input() set catalog(c: Catalog | null) { this.catalogSignal.set(c); }

  sections = computed(() => ['REQUESTER', 'INITIATIVE', 'IMPACT', 'FINANCIAL', 'STAKEHOLDERS', 'COMPLEMENTS', 'CLASSIFICATION']);

  canUpload(): boolean {
    return !this.detail.summary.readOnly && this.detail.summary.lifecycleState !== 'DRAFT';
  }

  fieldsOf(section: string): FieldMeta[] {
    return (this.catalogSignal()?.fields ?? []).filter(f => f.section === section);
  }

  label(s: string): string { return SECTION_LABELS[s] ?? s; }

  display(f: FieldMeta): string {
    const v = this.detail.fields[f.key];
    if (v === null || v === undefined || v === '') return 'Não informado';
    switch (f.type) {
      case 'BOOLEAN': return v === 'true' ? 'Sim' : 'Não';
      case 'IMPACT_LEVEL': return IMPACT_LABELS[v] ?? v;
      case 'URGENCY': return URGENCY_LABELS[v] ?? v;
      case 'DATE': return new Date(v + 'T00:00:00').toLocaleDateString('pt-BR');
      case 'DECIMAL': return Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
      case 'DEMAND_TYPE': return this.catalogSignal()?.demandTypes.find(t => t.code === v)?.name ?? v;
      case 'PROJECT': { const p = this.catalogSignal()?.projects.find(x => x.code === v); return p ? `${p.code} — ${p.name}` : v; }
      default: return v;
    }
  }
}
