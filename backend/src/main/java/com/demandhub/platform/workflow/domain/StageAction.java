package com.demandhub.platform.workflow.domain;

/** Ações sistêmicas executadas (após commit) quando uma demanda entra em um estágio. */
public enum StageAction {
    RUN_TRIAGE_ANALYSIS,
    CREATE_JIRA_ISSUE,
    CREATE_SCM_ISSUE
}
