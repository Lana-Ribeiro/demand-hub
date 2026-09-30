import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AgentInfo, AiSuggestion, AnalysisView, ChatMessage, ChatReply, SquadAgent } from '../models';

export type ArtifactKind = 'ARCHITECTURE' | 'TECH_SPEC' | 'TECH_PROMPT' | 'SQUAD_PLAN' | 'TECHNICAL_DOC' | 'CLIENT_DOC';

@Injectable({ providedIn: 'root' })
export class AiApi {
  private http = inject(HttpClient);

  status(): Observable<{ provider: string; mock: boolean }> { return this.http.get<{ provider: string; mock: boolean }>('/api/ai/status'); }

  agents(): Observable<{ agents: AgentInfo[]; deterministicAgents: AgentInfo[]; developmentSquad: SquadAgent[] }> {
    return this.http.get<{ agents: AgentInfo[]; deterministicAgents: AgentInfo[]; developmentSquad: SquadAgent[] }>('/api/ai/agents');
  }

  triage(demandId: string): Observable<AnalysisView | null> { return this.http.get<AnalysisView | null>(`/api/demands/${demandId}/ai-analysis`); }

  rerunTriage(demandId: string): Observable<AnalysisView> { return this.http.post<AnalysisView>(`/api/demands/${demandId}/ai-analysis/run`, {}); }

  artifact(demandId: string, kind: ArtifactKind): Observable<AnalysisView | null> {
    return this.http.get<AnalysisView | null>(`/api/demands/${demandId}/artifacts/${kind}`);
  }

  generate(demandId: string, kind: ArtifactKind): Observable<AnalysisView> {
    return this.http.post<AnalysisView>(`/api/demands/${demandId}/artifacts/${kind}`, {});
  }

  editAnalysis(demandId: string, analysisId: string, content: string, reason: string): Observable<AnalysisView> {
    return this.http.put<AnalysisView>(`/api/demands/${demandId}/analyses/${analysisId}`, { content, reason });
  }

  suggestions(demandId: string, pending = false): Observable<AiSuggestion[]> {
    return this.http.get<AiSuggestion[]>(`/api/demands/${demandId}/suggestions`, { params: { pending } });
  }

  decide(demandId: string, suggestionId: string, action: 'ACCEPT' | 'EDIT' | 'REJECT', value?: string, reason?: string): Observable<AiSuggestion> {
    return this.http.post<AiSuggestion>(`/api/demands/${demandId}/suggestions/${suggestionId}/decision`, { action, value, reason });
  }

  checkConsistency(demandId: string): Observable<{ divergences: number }> {
    return this.http.post<{ divergences: number }>(`/api/demands/${demandId}/consistency`, {});
  }

  analyzeDocument(documentId: string): Observable<{ suggestionsCreated: number }> {
    return this.http.post<{ suggestionsCreated: number }>(`/api/documents/${documentId}/analyze`, {});
  }

  chat(demandId: string): Observable<ChatMessage[]> { return this.http.get<ChatMessage[]>(`/api/demands/${demandId}/chat`); }

  send(demandId: string, message: string): Observable<ChatReply> {
    return this.http.post<ChatReply>(`/api/demands/${demandId}/chat`, { message });
  }
}
