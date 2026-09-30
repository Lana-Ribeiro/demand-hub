import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PlatformApi } from '../../core/api/platform-api.service';
import { NotifyService } from '../../core/ui/notify.service';

const EXAMPLE = JSON.stringify([{
  originalId: 'FORMS-2024-200', title: 'Título da demanda histórica', objective: 'Objetivo (se houver)',
  originalCreatedAt: '2024-05-10T12:00:00Z', originalStatus: 'Em andamento', originalOwner: 'Nome do responsável',
  projectCode: 'SMARTDESK', typeCode: 'DEVELOPMENT', requesterArea: 'Diretoria X', notes: 'Observações'
}], null, 2);

/** Importação de demandas históricas: somente leitura, sem histórico inventado; campos ausentes ficam vazios. */
@Component({
  selector: 'dh-legacy',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="page">
      <h1>Importar demandas legadas</h1>
      <div class="alert info"><mat-icon>info</mat-icon><div>As demandas importadas ficam marcadas como <strong>LEGADO</strong>, somente leitura e sem etapas recriadas.
        Apenas <code>originalId</code> e <code>title</code> são obrigatórios; reimportar o mesmo <code>originalId</code> é ignorado. Elas passam a ser consideradas na detecção de duplicidade.</div></div>
      <section class="card" style="margin-top:16px">
        <h3>Registros (JSON)</h3>
        <input #file type="file" accept=".json" hidden (change)="loadFile($event)">
        <div class="row"><button mat-stroked-button (click)="file.click()"><mat-icon>upload_file</mat-icon> Carregar arquivo .json</button>
          <button mat-button (click)="text.setValue(example)">Inserir exemplo</button></div>
        <mat-form-field class="full" style="margin-top:8px"><mat-label>Lista de registros</mat-label><textarea matInput rows="16" class="mono" [formControl]="text"></textarea></mat-form-field>
        <div class="row"><span class="spacer"></span><button mat-flat-button color="primary" (click)="import()" [disabled]="busy()">Importar</button></div>
        @if (result(); as r) {
          <div class="alert" [class.info]="r.imported > 0" [class.warning]="r.imported === 0" style="margin-top:12px"><mat-icon>fact_check</mat-icon>
            <div><strong>{{ r.imported }} importada(s), {{ r.skipped }} ignorada(s).</strong><ul>@for (m of r.messages; track $index) { <li>{{ m }}</li> }</ul></div></div>
        }
      </section>
    </div>
  `
})
export class LegacyComponent {
  private platform = inject(PlatformApi);
  private notify = inject(NotifyService);
  example = EXAMPLE;
  text = new FormControl('', Validators.required);
  busy = signal(false);
  result = signal<{ imported: number; skipped: number; messages: string[] } | null>(null);

  loadFile(e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (f) f.text().then(t => this.text.setValue(t));
  }

  import(): void {
    let records: unknown;
    try {
      records = JSON.parse(this.text.value ?? '');
    } catch {
      this.notify.error(null, 'JSON inválido.');
      return;
    }
    if (!Array.isArray(records)) { this.notify.error(null, 'Informe uma lista (array) de registros.'); return; }
    this.busy.set(true);
    this.platform.importLegacy(records).subscribe({
      next: r => { this.result.set(r); this.busy.set(false); }, error: err => { this.notify.error(err); this.busy.set(false); }
    });
  }
}
