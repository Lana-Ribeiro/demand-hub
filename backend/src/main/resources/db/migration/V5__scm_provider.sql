-- Repositório de código genérico (GitLab ou GitHub) para a execução técnica.
ALTER TABLE projects RENAME COLUMN gitlab_project_id TO scm_project_ref;
ALTER TABLE execution_statuses RENAME COLUMN gitlab_label TO scm_label;

UPDATE workflow_stages SET on_enter_actions = REPLACE(on_enter_actions, 'CREATE_GITLAB_ISSUE', 'CREATE_SCM_ISSUE')
 WHERE on_enter_actions LIKE '%CREATE_GITLAB_ISSUE%';

UPDATE execution_statuses SET jira_comment_template = 'Execução técnica criada no repositório de código e aguardando início.' WHERE code = 'TODO';
UPDATE execution_statuses SET jira_comment_template = 'Execução técnica concluída no repositório de código. Entrega implantada.' WHERE code = 'DONE';
