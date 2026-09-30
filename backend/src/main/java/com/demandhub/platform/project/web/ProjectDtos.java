package com.demandhub.platform.project.web;

import com.demandhub.platform.identity.web.UserDtos.UserRef;
import com.demandhub.platform.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {}

    public record ProjectResponse(Long id, String code, String name, String description, String color, String icon,
                                  UserRef owner, UserRef pmo, String jiraProjectKey, String scmProjectRef, boolean active) {
        public static ProjectResponse from(Project p) {
            return new ProjectResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getColor(), p.getIcon(),
                    UserRef.from(p.getOwner()), UserRef.from(p.getPmo()), p.getJiraProjectKey(), p.getScmProjectRef(), p.isActive());
        }
    }

    /** Identidade visual do projeto: sempre código + nome + cor (+ ícone). */
    public record ProjectRef(Long id, String code, String name, String color, String icon) {
        public static ProjectRef from(Project p) {
            return p == null ? null : new ProjectRef(p.getId(), p.getCode(), p.getName(), p.getColor(), p.getIcon());
        }
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Use letras maiúsculas, números, _ ou -") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB") String color,
            @Size(max = 60) String icon,
            UUID ownerId,
            UUID pmoId,
            @Size(max = 40) String jiraProjectKey,
            @Size(max = 100) String scmProjectRef,
            Boolean active) {}
}
