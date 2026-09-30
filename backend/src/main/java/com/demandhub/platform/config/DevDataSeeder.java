package com.demandhub.platform.config;

import com.demandhub.platform.demand.service.LegacyImportService;
import com.demandhub.platform.demand.service.LegacyImportService.LegacyRecord;
import com.demandhub.platform.identity.domain.Role;
import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.identity.repository.RoleRepository;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.project.domain.Project;
import com.demandhub.platform.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de DESENVOLVIMENTO (perfil dev): um usuário por papel, projetos de exemplo e demandas legadas de exemplo.
 * A senha vem de DEV_SEED_PASSWORD (.env, não versionado). Sem ela, nenhum usuário é criado.
 */
@Component
@Profile("dev")
@Order(10)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final ProjectRepository projects;
    private final LegacyImportService legacy;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DevDataSeeder(UserRepository users, RoleRepository roles, ProjectRepository projects, LegacyImportService legacy,
                         PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.projects = projects;
        this.legacy = legacy;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String password = props.devSeed() == null ? null : props.devSeed().password();
        if (password == null || password.isBlank()) {
            log.warn("DEV_SEED_PASSWORD não definido: usuários de desenvolvimento NÃO foram criados.");
            return;
        }
        String hash = encoder.encode(password);
        Map<String, User> byRole = new java.util.LinkedHashMap<>();
        for (String[] u : List.of(
                new String[]{"cliente@demandhub.local", "Carla Cliente", "Diretoria de Operações de Rede", Role.CLIENT},
                new String[]{"pmo@demandhub.local", "Paula PMO", "PMO Transformação de Redes", Role.PMO},
                new String[]{"gestor@demandhub.local", "Gustavo Gestor", "Transformação de Redes", Role.MANAGER},
                new String[]{"diretor@demandhub.local", "Diana Diretora", "Diretoria Transformação de Redes", Role.DIRECTOR},
                new String[]{"arquiteto@demandhub.local", "Artur Arquiteto", "Arquitetura", Role.ARCHITECT},
                new String[]{"dev@demandhub.local", "Davi Desenvolvedor", "Desenvolvimento", Role.DEVELOPER},
                new String[]{"qa@demandhub.local", "Quésia QA", "Qualidade", Role.QA},
                new String[]{"admin@demandhub.local", "Ana Admin", "TI", Role.ADMIN})) {
            User user = new User();
            user.setEmail(u[0]);
            user.setFullName(u[1]);
            user.setArea(u[2]);
            user.setPasswordHash(hash);
            user.setRoles(new HashSet<>(List.of(roles.findById(u[3]).orElseThrow())));
            users.save(user);
            byRole.put(u[3], user);
        }

        createProject("SMARTDESK", "SmartDesk", "Central de atendimento e service desk inteligente para operações de rede.", "#2E6FDB", "support_agent",
                byRole.get(Role.MANAGER), byRole.get(Role.PMO));
        createProject("AUTOREDE", "Automação de Rede", "Automação de processos de configuração, provisionamento e monitoração de rede.", "#0E9384", "hub",
                byRole.get(Role.MANAGER), byRole.get(Role.PMO));
        createProject("FIBRA360", "Fibra 360", "Gestão ponta a ponta da expansão e manutenção da rede de fibra óptica.", "#B54708", "cable",
                byRole.get(Role.MANAGER), byRole.get(Role.PMO));

        legacy.importRecords(List.of(
                new LegacyRecord("FORMS-2024-118", "Automação do inventário de equipamentos de rede",
                        "Automatizar a atualização do inventário de equipamentos a partir dos sistemas de gerência.",
                        Instant.parse("2024-08-12T13:00:00Z"), "Em andamento (planilha PMO)", "Equipe PMO", "AUTOREDE", "AUTOMATION",
                        "Diretoria de Operações de Rede", "Importado da planilha de controle do PMO."),
                new LegacyRecord("FORMS-2025-042", "Painel de indicadores de atendimento do SmartDesk", null,
                        Instant.parse("2025-02-03T10:00:00Z"), "Concluído", null, "SMARTDESK", null, null, null)));
        log.info("Dados de desenvolvimento criados: 8 usuários (senha = DEV_SEED_PASSWORD), 3 projetos, 2 demandas legadas.");
    }

    private void createProject(String code, String name, String description, String color, String icon, User owner, User pmo) {
        Project p = new Project();
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        p.setColor(color);
        p.setIcon(icon);
        p.setOwner(owner);
        p.setPmo(pmo);
        projects.save(p);
    }
}
