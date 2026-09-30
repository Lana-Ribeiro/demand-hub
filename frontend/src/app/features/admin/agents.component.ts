import { Component, OnInit, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { AiApi } from '../../core/api/ai-api.service';
import { PlatformApi } from '../../core/api/platform-api.service';
import { AgentInfo, IntegrationStatus, SquadAgent } from '../../core/models';
import { ModeBadgeComponent } from '../../shared/badges';
import { StateViewComponent } from '../../shared/state-view.component';

/** Transparência: agentes do produto, responsabilidades, provedor em uso e o squad de desenvolvimento. */
@Component({
  selector: 'dh-agents',
  standalone: true,
  imports: [MatIconModule, ModeBadgeComponent, StateViewComponent],
  template: `
    <div class="page">
      <h1>Agentes de IA</h1>
      <dh-state [loading]="loading()">
        <section class="card">
          <div class="row"><h3>Provedor</h3><dh-mode-badge [mode]="mock() ? 'MOCK' : 'REAL'"></dh-mode-badge></div>
          <p>{{ provider() }}</p>
          <p class="small muted">Agentes apenas sugerem: não aprovam, não alteram workflow e não escrevem no banco. Conteúdo de documentos é tratado como dado não confiável (proteção contra prompt injection).
            Integrações: Jira {{ integrations().jira }} · {{ integrations().scmSystem }} {{ integrations().scm }}.</p>
        </section>
        <section class="card">
          <h3>Agentes com LLM</h3>
          <div class="grid grid-2">@for (a of agents(); track a.name) { <div class="agent"><strong>{{ a.name }}</strong><div class="small">{{ a.responsibility }}</div></div> }</div>
        </section>
        <section class="card">
          <h3>Agentes determinísticos</h3>
          <div class="grid grid-2">@for (a of deterministic(); track a.name) { <div class="agent"><strong>{{ a.name }}</strong><div class="small">{{ a.responsibility }}</div></div> }</div>
        </section>
        <section class="card">
          <h3>Squad de desenvolvimento (Estratégia B)</h3>
          <div class="alert warning small"><mat-icon>info</mat-icon><div>A plataforma gera o plano do squad e o anexa à issue do repositório (GitLab/GitHub). A execução autônoma depende de um runner externo, não habilitado nesta versão.</div></div>
          <div class="grid grid-2" style="margin-top:12px">
            @for (s of squad(); track s.name) {
              <div class="agent"><strong>{{ s.name }}</strong><div class="small">{{ s.responsibility }}</div>
                <div class="small muted">Ferramentas: {{ s.allowedTools.join(', ') }}</div>
                <div class="small muted">Conclusão: {{ s.doneCriteria.join('; ') }}</div>
                <div class="small muted">Segurança: {{ s.safetyRules.join('; ') }}</div></div>
            }
          </div>
        </section>
      </dh-state>
    </div>
  `,
  styles: [`.agent { border: 1px solid var(--dh-border); border-radius: 8px; padding: 10px; display: flex; flex-direction: column; gap: 4px; }`]
})
export class AgentsComponent implements OnInit {
  private ai = inject(AiApi);
  private platform = inject(PlatformApi);
  loading = signal(true);
  provider = signal('');
  mock = signal(false);
  agents = signal<AgentInfo[]>([]);
  deterministic = signal<AgentInfo[]>([]);
  squad = signal<SquadAgent[]>([]);
  integrations = signal<IntegrationStatus>({ jira: '—', scm: '—', scmSystem: 'Repositório' });

  ngOnInit(): void {
    forkJoin({ status: this.ai.status(), agents: this.ai.agents(), integrations: this.platform.integrationStatus() }).subscribe({
      next: r => {
        this.provider.set(r.status.provider); this.mock.set(r.status.mock);
        this.agents.set(r.agents.agents); this.deterministic.set(r.agents.deterministicAgents); this.squad.set(r.agents.developmentSquad);
        this.integrations.set(r.integrations); this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
