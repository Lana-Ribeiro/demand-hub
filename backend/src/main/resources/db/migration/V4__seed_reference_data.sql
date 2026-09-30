-- Papéis --------------------------------------------------------------------
INSERT INTO roles (code, name, description) VALUES
 ('CLIENT',    'Cliente',     'Abre e acompanha as próprias demandas'),
 ('PMO',       'PMO',         'Triagem, classificação, priorização e aprovação de demandas'),
 ('MANAGER',   'Gestor',      'Acompanha portfólio e aprova alocação de recursos'),
 ('DIRECTOR',  'Diretor',     'Aprovação executiva de demandas'),
 ('ARCHITECT', 'Arquiteto',   'Análise e aprovação de arquitetura'),
 ('DEVELOPER', 'Desenvolvedor','Execução técnica'),
 ('QA',        'QA',          'Validação e registro de resultados'),
 ('ADMIN',     'Administrador','Configuração do sistema');

-- Permissões ----------------------------------------------------------------
INSERT INTO permissions (code, description) VALUES
 ('DEMAND_CREATE',       'Criar demandas'),
 ('DEMAND_VIEW_OWN',     'Visualizar as próprias demandas'),
 ('DEMAND_VIEW_ALL',     'Visualizar todas as demandas'),
 ('DEMAND_EDIT_ALL',     'Editar dados de qualquer demanda enviada'),
 ('DEMAND_TRIAGE',       'Triar: classificar, priorizar, aprovar, rejeitar, solicitar informação'),
 ('DEMAND_TRANSITION',   'Avançar estágios do workflow'),
 ('DEMAND_RESPOND',      'Responder pendências das próprias demandas'),
 ('APPROVAL_DECIDE',     'Decidir aprovações do próprio papel'),
 ('REFINEMENT_MANAGE',   'Registrar refinamento'),
 ('ARCHITECTURE_MANAGE', 'Registrar análise e decisões de arquitetura'),
 ('EXECUTION_VIEW',      'Visualizar execução técnica'),
 ('EXECUTION_MANAGE',    'Gerenciar execução técnica'),
 ('QA_RECORD',           'Registrar resultados de QA'),
 ('DASHBOARD_VIEW',      'Visualizar dashboard gerencial'),
 ('AUDIT_VIEW',          'Visualizar auditoria'),
 ('AI_USE',              'Acionar agentes de IA'),
 ('ADMIN_USERS',         'Administrar usuários e papéis'),
 ('ADMIN_CONFIG',        'Administrar projetos, tipos, workflows e regras'),
 ('INTEGRATION_MANAGE',  'Administrar e reprocessar integrações'),
 ('LEGACY_IMPORT',       'Importar demandas legadas');

INSERT INTO role_permissions (role_code, permission_code) VALUES
 ('CLIENT','DEMAND_CREATE'),('CLIENT','DEMAND_VIEW_OWN'),('CLIENT','DEMAND_RESPOND'),('CLIENT','AI_USE'),

 ('PMO','DEMAND_CREATE'),('PMO','DEMAND_VIEW_OWN'),('PMO','DEMAND_VIEW_ALL'),('PMO','DEMAND_EDIT_ALL'),
 ('PMO','DEMAND_TRIAGE'),('PMO','DEMAND_TRANSITION'),('PMO','DEMAND_RESPOND'),('PMO','APPROVAL_DECIDE'),
 ('PMO','REFINEMENT_MANAGE'),('PMO','EXECUTION_VIEW'),('PMO','EXECUTION_MANAGE'),('PMO','DASHBOARD_VIEW'),
 ('PMO','AUDIT_VIEW'),('PMO','AI_USE'),

 ('MANAGER','DEMAND_CREATE'),('MANAGER','DEMAND_VIEW_OWN'),('MANAGER','DEMAND_VIEW_ALL'),('MANAGER','DEMAND_RESPOND'),
 ('MANAGER','APPROVAL_DECIDE'),('MANAGER','EXECUTION_VIEW'),('MANAGER','DASHBOARD_VIEW'),

 ('DIRECTOR','DEMAND_VIEW_OWN'),('DIRECTOR','DEMAND_VIEW_ALL'),('DIRECTOR','APPROVAL_DECIDE'),('DIRECTOR','DASHBOARD_VIEW'),

 ('ARCHITECT','DEMAND_VIEW_OWN'),('ARCHITECT','DEMAND_VIEW_ALL'),('ARCHITECT','APPROVAL_DECIDE'),
 ('ARCHITECT','REFINEMENT_MANAGE'),('ARCHITECT','ARCHITECTURE_MANAGE'),('ARCHITECT','EXECUTION_VIEW'),('ARCHITECT','AI_USE'),

 ('DEVELOPER','DEMAND_VIEW_OWN'),('DEVELOPER','DEMAND_VIEW_ALL'),('DEVELOPER','EXECUTION_VIEW'),
 ('DEVELOPER','EXECUTION_MANAGE'),('DEVELOPER','AI_USE'),

 ('QA','DEMAND_VIEW_OWN'),('QA','DEMAND_VIEW_ALL'),('QA','EXECUTION_VIEW'),('QA','QA_RECORD'),

 ('ADMIN','DEMAND_CREATE'),('ADMIN','DEMAND_VIEW_OWN'),('ADMIN','DEMAND_VIEW_ALL'),('ADMIN','DEMAND_RESPOND'),
 ('ADMIN','EXECUTION_VIEW'),('ADMIN','DASHBOARD_VIEW'),('ADMIN','AUDIT_VIEW'),('ADMIN','ADMIN_USERS'),
 ('ADMIN','ADMIN_CONFIG'),('ADMIN','INTEGRATION_MANAGE'),('ADMIN','LEGACY_IMPORT');

-- Prioridades (política é contexto para o Priority Agent; decisão final é do PMO)
INSERT INTO priorities (code, name, rank_order, color, policy) VALUES
 ('P1', 'Crítica', 1, '#B42318', 'Obrigação regulatória/legal com prazo, risco operacional crítico ou impacto financeiro relevante imediato.'),
 ('P2', 'Alta',    2, '#C4320A', 'Impacto alto em muitos usuários ou processos-chave; prazo nos próximos 3 meses.'),
 ('P3', 'Média',   3, '#B54708', 'Melhoria relevante com impacto moderado; prazo flexível.'),
 ('P4', 'Baixa',   4, '#475467', 'Ganho incremental, baixo impacto, sem prazo definido.');

-- Lifecycle técnico (GitLab) ------------------------------------------------
INSERT INTO execution_statuses (code, name, position, gitlab_label, done, jira_comment_template) VALUES
 ('TODO',        'A fazer',        1, 'status::todo',        FALSE, 'Execução técnica criada no GitLab e aguardando início.'),
 ('DEVELOPMENT', 'Desenvolvimento',2, 'status::development', FALSE, 'Execução técnica avançou para Desenvolvimento. Implementação em andamento.'),
 ('CODE_REVIEW', 'Code Review',    3, 'status::code-review', FALSE, 'Execução técnica avançou para Code Review. Implementação concluída e em revisão de código.'),
 ('QA',          'QA',             4, 'status::qa',          FALSE, 'Execução técnica avançou para QA. Implementação concluída e aguardando validação.'),
 ('DEPLOY',      'Deploy',         5, 'status::deploy',      FALSE, 'Execução técnica avançou para Deploy. Validação concluída; implantação em andamento.'),
 ('DONE',        'Concluído',      6, 'status::done',        TRUE,  'Execução técnica concluída no GitLab. Entrega implantada.');
