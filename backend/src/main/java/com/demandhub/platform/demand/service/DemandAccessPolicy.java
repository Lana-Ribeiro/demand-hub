package com.demandhub.platform.demand.service;

import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import org.springframework.stereotype.Component;

/** Regras de acesso por propriedade (complementa o RBAC por permissão). */
@Component
public class DemandAccessPolicy {

    public boolean canView(Demand d, CurrentUser user) {
        return user.has(Permissions.DEMAND_VIEW_ALL) || (user.has(Permissions.DEMAND_VIEW_OWN) && d.isOwnedBy(user.id()));
    }

    public void assertCanView(Demand d, CurrentUser user) {
        if (!canView(d, user)) {
            // 404 em vez de 403: não revela a existência de demandas de terceiros.
            throw ApiException.notFound("Demanda", d.getId());
        }
    }

    public void assertOwnerOfDraft(Demand d, CurrentUser user) {
        assertCanView(d, user);
        if (!d.isOwnedBy(user.id())) {
            throw ApiException.forbidden("Somente o solicitante pode editar o rascunho.");
        }
        if (!d.isDraft() || d.isReadOnly()) {
            throw ApiException.businessRule("NOT_A_DRAFT", "A demanda já foi enviada e não pode ser editada pelo solicitante.");
        }
    }

    public boolean isInternal(CurrentUser user) {
        return user.has(Permissions.DEMAND_VIEW_ALL);
    }
}
