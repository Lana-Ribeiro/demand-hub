/** Rótulos de apresentação (pt-BR). Sem regra de negócio: somente texto. */

export const IMPACT_LABELS: Record<string, string> = { LOW: 'Baixo', MEDIUM: 'Médio', HIGH: 'Alto', CRITICAL: 'Crítico' };
export const URGENCY_LABELS: Record<string, string> = { LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta' };

export const LIFECYCLE_LABELS: Record<string, string> = {
  DRAFT: 'Rascunho', ACTIVE: 'Em andamento', ON_HOLD: 'Aguardando informação', REJECTED: 'Rejeitada',
  COMPLETED: 'Concluída', CANCELLED: 'Cancelada', LEGACY: 'Legado (importado)'
};

export const CATEGORY_LABELS: Record<string, string> = {
  DRAFT: 'Rascunho', INTAKE: 'Análise da IA', TRIAGE: 'Triagem PMO', ON_HOLD: 'Aguardando informação',
  ANALYSIS: 'Análise', APPROVAL: 'Aprovação', REFINEMENT: 'Refinamento', ARCHITECTURE: 'Arquitetura',
  READY: 'Pronta p/ desenvolvimento', EXECUTION: 'Em execução', DOCUMENTATION: 'Documentação',
  DONE: 'Concluída', REJECTED: 'Rejeitada', CANCELLED: 'Cancelada'
};

export const SECTION_LABELS: Record<string, string> = {
  REQUESTER: 'Solicitante', INITIATIVE: 'Iniciativa', IMPACT: 'Impacto e prazo', FINANCIAL: 'Financeiro',
  STAKEHOLDERS: 'Envolvidos', COMPLEMENTS: 'Complementos', CLASSIFICATION: 'Classificação'
};

export const ROLE_LABELS: Record<string, string> = {
  CLIENT: 'Cliente', PMO: 'PMO', MANAGER: 'Gestor', DIRECTOR: 'Diretor', ARCHITECT: 'Arquiteto',
  DEVELOPER: 'Desenvolvedor', QA: 'QA', ADMIN: 'Administrador'
};

export const SOURCE_LABELS: Record<string, string> = {
  FORM: 'Formulário', DOCUMENT: 'Documento', PRESENTATION: 'Apresentação', CHAT: 'Assistente (chat)',
  ANALYSIS: 'Análise da IA', MEETING: 'Reunião'
};

export const ITEM_TYPE_LABELS: Record<string, string> = {
  FUNCTIONAL_REQUIREMENT: 'Requisito funcional', NON_FUNCTIONAL_REQUIREMENT: 'Requisito não funcional',
  ACCEPTANCE_CRITERION: 'Critério de aceite', DEPENDENCY: 'Dependência', RISK: 'Risco', QUESTION: 'Dúvida',
  PENDING_ITEM: 'Pendência'
};

export const DECISION_TYPE_LABELS: Record<string, string> = { BUSINESS: 'Negócio', ARCHITECTURAL: 'Arquitetural', TECHNICAL: 'Técnica' };

export const DOC_KIND_LABELS: Record<string, string> = {
  DOCUMENT: 'Documentação', PRESENTATION: 'Apresentação', ATTACHMENT: 'Anexo',
  TECHNICAL_DOC: 'Documentação técnica', CLIENT_DOC: 'Documentação para o cliente'
};

export function confidenceLabel(c?: number): string {
  if (c == null) return '—';
  if (c >= 0.75) return 'Alta';
  if (c >= 0.5) return 'Média';
  return 'Baixa';
}
