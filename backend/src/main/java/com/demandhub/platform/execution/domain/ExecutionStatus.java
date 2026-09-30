package com.demandhub.platform.execution.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Status configurável do lifecycle técnico (GitLab/GitHub), mapeado para labels status::*. */
@Entity
@Table(name = "execution_statuses")
@Getter
@Setter
@NoArgsConstructor
public class ExecutionStatus {

    @Id
    private String code;

    private String name;
    private int position;
    /** Label no repositório (GitLab e GitHub), ex.: status::qa. */
    @jakarta.persistence.Column(name = "scm_label")
    private String scmLabel;
    private boolean done;
    /** Texto do comentário publicado no Jira PMO quando a execução chega a este status. */
    private String jiraCommentTemplate;
}
