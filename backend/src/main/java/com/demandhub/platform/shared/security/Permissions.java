package com.demandhub.platform.shared.security;

/** Códigos de permissão (seed em V4__seed_reference_data.sql). */
public final class Permissions {

    private Permissions() {}

    public static final String DEMAND_CREATE = "DEMAND_CREATE";
    public static final String DEMAND_VIEW_OWN = "DEMAND_VIEW_OWN";
    public static final String DEMAND_VIEW_ALL = "DEMAND_VIEW_ALL";
    public static final String DEMAND_EDIT_ALL = "DEMAND_EDIT_ALL";
    public static final String DEMAND_TRIAGE = "DEMAND_TRIAGE";
    public static final String DEMAND_TRANSITION = "DEMAND_TRANSITION";
    public static final String DEMAND_RESPOND = "DEMAND_RESPOND";
    public static final String APPROVAL_DECIDE = "APPROVAL_DECIDE";
    public static final String REFINEMENT_MANAGE = "REFINEMENT_MANAGE";
    public static final String ARCHITECTURE_MANAGE = "ARCHITECTURE_MANAGE";
    public static final String EXECUTION_VIEW = "EXECUTION_VIEW";
    public static final String EXECUTION_MANAGE = "EXECUTION_MANAGE";
    public static final String QA_RECORD = "QA_RECORD";
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String AI_USE = "AI_USE";
    public static final String ADMIN_USERS = "ADMIN_USERS";
    public static final String ADMIN_CONFIG = "ADMIN_CONFIG";
    public static final String INTEGRATION_MANAGE = "INTEGRATION_MANAGE";
    public static final String LEGACY_IMPORT = "LEGACY_IMPORT";
}
