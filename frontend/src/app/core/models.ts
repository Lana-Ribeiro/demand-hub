/** Modelos alinhados aos DTOs da API (backend/src/main/java/.../web). */

export type StageCategory =
  | 'DRAFT' | 'INTAKE' | 'TRIAGE' | 'ON_HOLD' | 'ANALYSIS' | 'APPROVAL' | 'REFINEMENT' | 'ARCHITECTURE'
  | 'READY' | 'EXECUTION' | 'DOCUMENTATION' | 'DONE' | 'REJECTED' | 'CANCELLED';

export type LifecycleState = 'DRAFT' | 'ACTIVE' | 'ON_HOLD' | 'REJECTED' | 'COMPLETED' | 'CANCELLED' | 'LEGACY';

export interface UserRef { id: string; fullName: string; email: string; }

export interface User {
  id: string; email: string; fullName: string; area?: string; active: boolean;
  roles: string[]; permissions: string[];
}

export interface LoginResponse { token: string; expiresAt: string; user: User; }

export interface ProjectRef { id: number; code: string; name: string; color: string; icon?: string; }

export interface Project extends ProjectRef {
  description?: string; owner?: UserRef; pmo?: UserRef; jiraProjectKey?: string; scmProjectRef?: string; active: boolean;
}

export interface TypeRef { code: string; name: string; technical: boolean; }
export interface PriorityRef { code: string; name: string; color: string; }
export interface StageRef { code: string; name: string; category: StageCategory; }
export interface LinkRef { system: string; key?: string; url?: string; mode: 'REAL' | 'MOCK'; syncStatus: string; lastError?: string; }
export interface LegacyInfo { originalId?: string; originalCreatedAt?: string; originalStatus?: string; originalOwner?: string; }

export interface DemandSummary {
  id: string; protocol?: string; title?: string; source: string; lifecycleState: LifecycleState; readOnly: boolean;
  project?: ProjectRef; type?: TypeRef; priority?: PriorityRef; stage?: StageRef;
  requester?: UserRef; owner?: UserRef; impactLevel?: string; urgency?: string; desiredDate?: string;
  submittedAt?: string; updatedAt?: string; stageEnteredAt?: string; jira?: LinkRef; executionStatus?: string;
  pendingApprovals: number; legacy?: LegacyInfo;
}

export interface AvailableTransition {
  action: string; label: string; toStageCode: string; toStageName: string;
  requiresReason: boolean; forward: boolean; blockedBy: string[];
}

export interface StageHistoryItem {
  stageCode: string; stageName: string; category: StageCategory; action?: string; actorName?: string;
  reason?: string; enteredAt: string; exitedAt?: string;
}

export interface DemandDetail {
  summary: DemandSummary; fields: Record<string, string | null>; workflowCode?: string; workflowName?: string;
  availableTransitions: AvailableTransition[]; submissionErrors: Record<string, string>;
  stageHistory: StageHistoryItem[]; links: LinkRef[]; createdAt: string; version: number;
}

export interface Page<T> { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number; }; }

export interface FieldMeta {
  key: string; label: string; section: string; type: string; requiredAtSubmit: boolean;
  maxLength: number; help: string; clientEditable: boolean;
}

export interface DemandTypeView {
  id: number; code: string; name: string; description?: string; technical: boolean;
  workflowCode: string; workflowName: string; active: boolean;
}

export interface Priority { code: string; name: string; rankOrder: number; color: string; policy?: string; }

export interface Catalog {
  fields: FieldMeta[]; demandTypes: DemandTypeView[]; priorities: Priority[]; projects: ProjectRef[];
  impactLevels: string[]; urgencies: string[];
}

export type SuggestionKind = 'FIELD_VALUE' | 'MISSING_INFO' | 'INCONSISTENCY' | 'DUPLICATE' | 'REFINEMENT_ITEM';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'EDITED' | 'REJECTED' | 'SUPERSEDED';

export interface AiSuggestion {
  id: string; demandId: string; agentRunId?: string; agent: string; kind: SuggestionKind; field?: string;
  suggestedValue?: string; alternativeValue?: string; confidence?: number; sourceType: string; sourceRef?: string;
  rationale?: string; status: SuggestionStatus; finalValue?: string; decidedAt?: string; decisionReason?: string; createdAt: string;
}

export interface AnalysisView {
  id: string; demandId: string; kind: string; mode: 'REAL' | 'MOCK'; content: string; edited: boolean;
  editedAt?: string; createdAt: string;
}

export interface ChatMessage { id: string; role: 'USER' | 'ASSISTANT'; content: string; createdAt: string; }
export interface ChatReply { message: ChatMessage; suggestions: AiSuggestion[]; mode: 'REAL' | 'MOCK'; }

export interface DocumentView {
  id: string; demandId: string; kind: string; fileName: string; contentType: string; sizeBytes: number;
  extractionStatus: string; extractionError?: string; uploadedAt: string;
}

export interface InformationRequest {
  id: string; question: string; requestedBy?: UserRef; requestedAt: string; response?: string;
  respondedBy?: UserRef; respondedAt?: string; status: 'OPEN' | 'ANSWERED' | 'CANCELLED';
}

export interface Comment {
  id: string; authorName: string; body: string; visibility: 'PUBLIC' | 'INTERNAL'; source: string; createdAt: string;
}

export interface ApprovalView {
  id: string; demandId: string; ruleName: string; stageCode: string; approverRole: string; status: string;
  decidedBy?: UserRef; decidedAt?: string; comment?: string; createdAt: string;
}

export interface PendingApproval { approval: ApprovalView; demand: DemandSummary; }

export interface AuditLog {
  id: string; actorName: string; actorRoles?: string; action: string; entityType: string; entityId?: string;
  demandId?: string; field?: string; beforeValue?: string; afterValue?: string; reason?: string;
  aiSuggestionId?: string; metadata?: string; createdAt: string;
}

export interface Meeting { id: string; title: string; heldAt: string; participants?: string; notes?: string; createdAt: string; }
export interface Decision { id: string; meetingId?: string; type: string; description: string; rationale?: string; decidedBy?: string; createdAt: string; }
export interface RefinementItem { id: string; type: string; description: string; status: 'OPEN' | 'RESOLVED'; origin: string; createdAt: string; }
export interface RefinementSnapshot { meetings: Meeting[]; decisions: Decision[]; items: RefinementItem[]; }

export interface ExecutionStatus { code: string; name: string; position: number; scmLabel: string; done: boolean; jiraCommentTemplate: string; }
export interface LinkView { system: string; mode: string; key?: string; projectRef?: string; url?: string; syncStatus: string; lastError?: string; lastSyncAt?: string; }
export interface ExecutionEvent { id: string; fromStatus?: string; toStatus: string; summary?: string; source: string; receivedAt: string; }
export interface ExecutionPanel {
  demandLifecycle: { stage?: string; jiraStatus?: string; lifecycleState: string; jira?: LinkView; };
  technicalExecution?: { id: string; status: string; strategy: string; system: string; startedAt: string; completedAt?: string; repository?: LinkView; events: ExecutionEvent[]; };
  statuses: ExecutionStatus[]; jiraMode: string; scmMode: string; scmSystem: string; technicalDemand: boolean;
}
export interface IntegrationStatus { jira: string; scm: string; scmSystem: string; }
export interface ExecutionRow { id: string; status: string; strategy: string; lastEventAt?: string; demand: DemandSummary; }

export interface BoardColumn { category: StageCategory; label: string; items: DemandSummary[]; }

export interface Notification { id: string; channel: string; eventType: string; subject: string; body: string; demandId?: string; status: string; createdAt: string; readAt?: string; }

export interface CountItem { key: string; label: string; count: number; color?: string; }
export interface TimeMetric { label: string; averageHours?: number; sampleSize: number; sufficient: boolean; }
export interface Dashboard {
  openDemands: number; completedDemands: number; rejectedDemands: number; pendingApprovals: number;
  inRefinement: number; inDevelopment: number; inQa: number; byStage: CountItem[]; byProject: CountItem[];
  byType: CountItem[]; byPriority: CountItem[];
  stalled: { id: string; protocol: string; title: string; stage: string; daysInStage: number }[];
  stalledThresholdDays: number; averageTimes: TimeMetric[]; volumeByMonth: CountItem[]; generatedAt: string;
}

export interface WorkflowStage { id: number; code: string; name: string; category: string; position: number; autoAdvance: boolean; jiraStatus?: string; onEnterActions: string[]; exitRequirements: string[]; }
export interface WorkflowTransition { id: number; from: string; to: string; action: string; label: string; requiredPermission?: string; requiresReason: boolean; forward: boolean; systemOnly: boolean; }
export interface ApprovalRule { id: number; workflowId: number; stageCode: string; name: string; approverRole: string; conditionField?: string; conditionOperator?: string; conditionValue?: string; projectId?: number; active: boolean; }
export interface WorkflowView { id: number; code: string; name: string; description?: string; stages: WorkflowStage[]; transitions: WorkflowTransition[]; approvalRules: ApprovalRule[]; }
export interface WorkflowMetadata { exitRequirements: string[]; stageActions: string[]; conditionFields: string[]; operators: string[]; roles: string[]; }
export interface RoleView { code: string; name: string; description?: string; permissions: string[]; }

export interface AgentInfo { name: string; responsibility: string; }
export interface SquadAgent { name: string; responsibility: string; context: string; allowedTools: string[]; inputs: string[]; outputs: string[]; doneCriteria: string[]; safetyRules: string[]; }
