import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import {
  AiSuggestion, ApprovalRule, AuditLog, Catalog, Dashboard, Decision, ExecutionPanel, ExecutionRow, ExecutionStatus, LinkView,
  Meeting, Notification, Page, Priority, Project, RefinementItem, RefinementSnapshot, RoleView, User, UserRef, WorkflowMetadata,
  WorkflowStage, WorkflowView, DemandTypeView
} from '../models';
import { IntegrationStatus } from '../models';

/** Catálogo, refinamento, execução, notificações, dashboard e auditoria. */
@Injectable({ providedIn: 'root' })
export class PlatformApi {
  private http = inject(HttpClient);
  private catalog$?: Observable<Catalog>;

  catalog(refresh = false): Observable<Catalog> {
    if (!this.catalog$ || refresh) {
      this.catalog$ = this.http.get<Catalog>('/api/catalog').pipe(shareReplay(1));
    }
    return this.catalog$;
  }

  assignableUsers(): Observable<UserRef[]> { return this.http.get<UserRef[]>('/api/users/assignable'); }

  // Refinamento
  refinement(demandId: string): Observable<RefinementSnapshot> { return this.http.get<RefinementSnapshot>(`/api/demands/${demandId}/refinement`); }
  addMeeting(demandId: string, body: { title: string; heldAt?: string; participants?: string; notes?: string }): Observable<Meeting> {
    return this.http.post<Meeting>(`/api/demands/${demandId}/refinement/meetings`, body);
  }
  meetingSuggestions(demandId: string, meetingId: string): Observable<AiSuggestion[]> {
    return this.http.post<AiSuggestion[]>(`/api/demands/${demandId}/refinement/meetings/${meetingId}/ai-suggestions`, {});
  }
  addDecision(demandId: string, body: { type: string; description: string; rationale?: string; decidedBy?: string }): Observable<Decision> {
    return this.http.post<Decision>(`/api/demands/${demandId}/refinement/decisions`, body);
  }
  addItem(demandId: string, type: string, description: string): Observable<RefinementItem> {
    return this.http.post<RefinementItem>(`/api/demands/${demandId}/refinement/items`, { type, description });
  }
  setItemStatus(demandId: string, itemId: string, status: 'OPEN' | 'RESOLVED'): Observable<RefinementItem> {
    return this.http.patch<RefinementItem>(`/api/demands/${demandId}/refinement/items/${itemId}`, { status });
  }

  // Execução e integrações
  execution(demandId: string): Observable<ExecutionPanel> { return this.http.get<ExecutionPanel>(`/api/demands/${demandId}/execution`); }
  executions(): Observable<ExecutionRow[]> { return this.http.get<ExecutionRow[]>('/api/executions'); }
  setStrategy(demandId: string, strategy: string): Observable<unknown> {
    return this.http.put(`/api/demands/${demandId}/execution/strategy`, { strategy });
  }
  simulateScm(demandId: string, status: string): Observable<unknown> {
    return this.http.post(`/api/integrations/scm/mock/executions/${demandId}/status`, { status });
  }
  manualStatus(demandId: string, status: string, note?: string): Observable<unknown> {
    return this.http.post(`/api/demands/${demandId}/execution/status`, { status, note });
  }
  jiraSync(demandId: string): Observable<LinkView> { return this.http.post<LinkView>(`/api/demands/${demandId}/jira`, {}); }
  scmRetry(demandId: string): Observable<unknown> { return this.http.post(`/api/demands/${demandId}/scm/retry`, {}); }
  integrationStatus(): Observable<IntegrationStatus> { return this.http.get<IntegrationStatus>('/api/integrations/status'); }

  // Notificações
  notifications(): Observable<Notification[]> { return this.http.get<Notification[]>('/api/notifications'); }
  unread(): Observable<{ unread: number }> { return this.http.get<{ unread: number }>('/api/notifications/unread-count'); }
  markRead(id: string): Observable<void> { return this.http.post<void>(`/api/notifications/${id}/read`, {}); }
  markAllRead(): Observable<void> { return this.http.post<void>('/api/notifications/read-all', {}); }

  // Dashboard e auditoria
  dashboard(projectId?: number | null): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard', { params: projectId ? { projectId } : {} });
  }
  auditSearch(params: Record<string, string | number>): Observable<Page<AuditLog>> {
    return this.http.get<Page<AuditLog>>('/api/audit', { params });
  }

  // Administração
  users(): Observable<User[]> { return this.http.get<User[]>('/api/admin/users'); }
  createUser(body: { email: string; fullName: string; area?: string; password: string; roles: string[] }): Observable<User> {
    return this.http.post<User>('/api/admin/users', body);
  }
  updateUser(id: string, body: { fullName?: string; area?: string; roles?: string[]; active?: boolean }): Observable<User> {
    return this.http.put<User>(`/api/admin/users/${id}`, body);
  }
  roles(): Observable<RoleView[]> { return this.http.get<RoleView[]>('/api/admin/roles'); }

  projects(onlyActive = false): Observable<Project[]> { return this.http.get<Project[]>('/api/projects', { params: { onlyActive } }); }
  saveProject(body: Partial<Project> & { ownerId?: string | null; pmoId?: string | null }, id?: number): Observable<Project> {
    return id ? this.http.put<Project>(`/api/projects/${id}`, body) : this.http.post<Project>('/api/projects', body);
  }

  workflows(): Observable<WorkflowView[]> { return this.http.get<WorkflowView[]>('/api/admin/workflows'); }
  workflowMetadata(): Observable<WorkflowMetadata> { return this.http.get<WorkflowMetadata>('/api/admin/workflow-metadata'); }
  updateStage(id: number, body: Partial<WorkflowStage>): Observable<WorkflowStage> { return this.http.put<WorkflowStage>(`/api/admin/workflows/stages/${id}`, body); }
  saveRule(body: Partial<ApprovalRule>, id?: number): Observable<ApprovalRule> {
    return id ? this.http.put<ApprovalRule>(`/api/admin/approval-rules/${id}`, body) : this.http.post<ApprovalRule>('/api/admin/approval-rules', body);
  }
  deactivateRule(id: number): Observable<void> { return this.http.delete<void>(`/api/admin/approval-rules/${id}`); }

  executionStatuses(): Observable<ExecutionStatus[]> { return this.http.get<ExecutionStatus[]>('/api/admin/execution-statuses'); }
  updateExecutionStatus(code: string, body: Partial<ExecutionStatus>): Observable<ExecutionStatus> {
    return this.http.put<ExecutionStatus>(`/api/admin/execution-statuses/${code}`, body);
  }
  saveDemandType(body: Partial<DemandTypeView>, id?: number): Observable<DemandTypeView> {
    return id ? this.http.put<DemandTypeView>(`/api/admin/demand-types/${id}`, body) : this.http.post<DemandTypeView>('/api/admin/demand-types', body);
  }
  updatePriority(code: string, body: Partial<Priority>): Observable<Priority> { return this.http.put<Priority>(`/api/admin/priorities/${code}`, body); }
  importLegacy(records: unknown[]): Observable<{ imported: number; skipped: number; messages: string[] }> {
    return this.http.post<{ imported: number; skipped: number; messages: string[] }>('/api/admin/legacy/import', { records });
  }
}
