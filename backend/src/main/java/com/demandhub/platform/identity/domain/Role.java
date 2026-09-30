package com.demandhub.platform.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor
public class Role {

    public static final String CLIENT = "CLIENT";
    public static final String PMO = "PMO";
    public static final String MANAGER = "MANAGER";
    public static final String DIRECTOR = "DIRECTOR";
    public static final String ARCHITECT = "ARCHITECT";
    public static final String DEVELOPER = "DEVELOPER";
    public static final String QA = "QA";
    public static final String ADMIN = "ADMIN";

    @Id
    private String code;

    private String name;

    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_code"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<Permission> permissions = new HashSet<>();
}
