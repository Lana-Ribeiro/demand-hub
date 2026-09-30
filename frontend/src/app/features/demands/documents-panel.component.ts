import { Component, EventEmitter, Input, OnChanges, Output, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DemandApi } from '../../core/api/demand-api.service';
import { AiApi } from '../../core/api/ai-api.service';
import { DocumentView } from '../../core/models';
import { DOC_KIND_LABELS } from '../../core/labels';
import { NotifyService } from '../../core/ui/notify.service';
import { StateViewComponent } from '../../shared/state-view.component';

const ACCEPT = '.pdf,.docx,.doc,.pptx,.ppt,.xlsx,.xls,.txt,.md,.csv,.png,.jpg,.jpeg';

/** Upload validado (tipo/tamanho verificados no servidor) e lista de documentos com status de extração. */
@Component({
  selector: 'dh-documents-panel',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatProgressBarModule, MatTooltipModule, StateViewComponent],
  template: `
    @if (canUpload) {
      <div class="upload">
        <mat-form-field class="kind">
          <mat-label>Tipo</mat-label>
          <mat-select [(ngModel)]="kind">
            <mat-option value="DOCUMENT">Documentação (template)</mat-option>
            <mat-option value="PRESENTATION">Apresentação (PPT/Canva)</mat-option>
            <mat-option value="ATTACHMENT">Outro anexo</mat-option>
          </mat-select>
        </mat-form-field>
        <input #file type="file" [accept]="accept" hidden (change)="onFile($event)">
        <button mat-stroked-button type="button" (click)="file.click()" [disabled]="uploading()"><mat-icon>upload</mat-icon> Enviar arquivo</button>
      </div>
      <p class="small muted">PDF, Word, PowerPoint, Excel, texto ou imagem · até 25 MB. A IA lê documentação e apresentação e propõe preenchimentos para sua revisão.</p>
      @if (uploading()) { <mat-progress-bar mode="indeterminate"></mat-progress-bar> }
    }
    <dh-state [loading]="loading()" [empty]="!docs().length" emptyText="Nenhum documento anexado." emptyIcon="description">
      @for (d of docs(); track d.id) {
        <div class="doc">
          <mat-icon>{{ icon(d) }}</mat-icon>
          <div class="info">
            <div class="name">{{ d.fileName }}</div>
            <div class="small muted">{{ label(d.kind) }} · {{ d.sizeBytes / 1024 | number: '1.0-0' }} KB · {{ d.uploadedAt | date: 'dd/MM/yyyy HH:mm' }}</div>
            <div class="small">
              @switch (d.extractionStatus) {
                @case ('EXTRACTED') { <span class="chip success">Texto extraído</span> }
                @case ('FAILED') { <span class="chip danger" [matTooltip]="d.extractionError || ''">Falha na leitura</span> }
                @case ('NOT_APPLICABLE') { <span class="chip neutral">Sem extração</span> }
                @default { <span class="chip">Processando</span> }
              }
            </div>
          </div>
          @if (canAnalyze && d.extractionStatus === 'EXTRACTED' && (d.kind === 'DOCUMENT' || d.kind === 'PRESENTATION')) {
            <button mat-icon-button (click)="analyze(d)" matTooltip="Reanalisar com IA" aria-label="Reanalisar com IA"><mat-icon>auto_awesome</mat-icon></button>
          }
          <button mat-icon-button (click)="download(d)" matTooltip="Baixar" aria-label="Baixar arquivo"><mat-icon>download</mat-icon></button>
        </div>
      }
    </dh-state>
  `,
  styles: [`
    .upload { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; } .kind { width: 260px; }
    .doc { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--dh-border); }
    .doc:last-child { border-bottom: 0; } .info { flex: 1; min-width: 0; } .name { font-weight: 500; word-break: break-all; }
  `]
})
export class DocumentsPanelComponent implements OnChanges {
  private api = inject(DemandApi);
  private ai = inject(AiApi);
  private notify = inject(NotifyService);

  @Input() demandId: string | null = null;
  @Input() canUpload = false;
  @Input() canAnalyze = false;
  @Input() ensureDraft?: () => Promise<string>;
  @Output() uploaded = new EventEmitter<DocumentView>();

  accept = ACCEPT;
  kind = 'DOCUMENT';
  docs = signal<DocumentView[]>([]);
  loading = signal(false);
  uploading = signal(false);

  ngOnChanges(): void { this.reload(); }

  reload(): void {
    if (!this.demandId) { this.docs.set([]); return; }
    this.loading.set(true);
    this.api.documents(this.demandId).subscribe({
      next: d => { this.docs.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 25 * 1024 * 1024) { this.notify.error(null, 'Arquivo excede 25 MB.'); return; }
    this.uploading.set(true);
    try {
      const id = this.demandId ?? (this.ensureDraft ? await this.ensureDraft() : null);
      if (!id) { this.uploading.set(false); return; }
      this.demandId = id;
      this.api.upload(id, file, this.kind).subscribe({
        next: doc => {
          this.uploading.set(false);
          this.notify.success(doc.extractionStatus === 'EXTRACTED' ? 'Arquivo enviado. A IA está analisando o conteúdo.' : 'Arquivo enviado.');
          this.reload();
          this.uploaded.emit(doc);
        },
        error: err => { this.uploading.set(false); this.notify.error(err); }
      });
    } catch (err) {
      this.uploading.set(false);
      this.notify.error(err);
    }
  }

  analyze(d: DocumentView): void {
    this.ai.analyzeDocument(d.id).subscribe({
      next: r => { this.notify.success(`${r.suggestionsCreated} sugestão(ões) gerada(s).`); this.uploaded.emit(d); },
      error: err => this.notify.error(err)
    });
  }

  download(d: DocumentView): void {
    this.api.download(d.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = d.fileName; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: err => this.notify.error(err)
    });
  }

  label(kind: string): string { return DOC_KIND_LABELS[kind] ?? kind; }
  icon(d: DocumentView): string {
    return d.kind === 'PRESENTATION' ? 'slideshow' : d.kind.endsWith('_DOC') ? 'article' : d.contentType.startsWith('image/') ? 'image' : 'description';
  }
}
