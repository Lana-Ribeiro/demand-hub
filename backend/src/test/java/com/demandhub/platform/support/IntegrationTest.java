package com.demandhub.platform.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.demandhub.platform.identity.domain.User;
import com.demandhub.platform.identity.repository.RoleRepository;
import com.demandhub.platform.identity.repository.UserRepository;
import com.demandhub.platform.project.domain.Project;
import com.demandhub.platform.project.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base de testes de integração: contexto completo, H2 (modo PostgreSQL), IA/Jira/GitLab em modo MOCK, execução síncrona. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String TEST_PASSWORD = "senha-de-teste-123";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected UserRepository users;
    @Autowired protected RoleRepository roles;
    @Autowired protected ProjectRepository projects;
    @Autowired protected PasswordEncoder encoder;

    /** Usuário de teste com os papéis informados; retorna o token obtido pelo endpoint real de login. */
    protected String userWithRoles(String... roleCodes) throws Exception {
        String email = "u-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setEmail(email);
        u.setFullName("Teste " + String.join("/", roleCodes));
        u.setArea("Área de teste");
        u.setPasswordHash(encoder.encode(TEST_PASSWORD));
        u.setRoles(new HashSet<>(Set.of(roleCodes).stream().map(r -> roles.findById(r).orElseThrow()).collect(Collectors.toSet())));
        users.save(u);
        return login(email, TEST_PASSWORD);
    }

    protected String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).path("token").asText();
    }

    protected Project project(String code) {
        return projects.findByCodeIgnoreCase(code).orElseGet(() -> {
            Project p = new Project();
            p.setCode(code);
            p.setName("Projeto " + code);
            p.setDescription("Projeto de teste " + code + " automação rede inventário");
            p.setColor("#123456");
            return projects.save(p);
        });
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    protected JsonNode json(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return body.isEmpty() ? mapper.nullNode() : mapper.readTree(body);
    }

    protected JsonNode getJson(String url, String token) throws Exception {
        return json(mvc.perform(auth(get(url), token)).andReturn());
    }

    protected MvcResult postJson(String url, String token, Object body) throws Exception {
        return mvc.perform(auth(post(url), token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn();
    }

    protected String write(Object o) throws Exception {
        return mapper.writeValueAsString(o);
    }

    /** Formulário completo e válido. */
    protected Map<String, String> completeForm(String typeCode, String projectCode) {
        Map<String, String> f = new java.util.HashMap<>();
        f.put("requesterArea", "Diretoria de Operações de Rede");
        f.put("sponsorName", "Maria Patrocinadora");
        f.put("title", "Automatizar o processo de inventário de rede " + UUID.randomUUID().toString().substring(0, 6));
        f.put("demandType", typeCode);
        if (projectCode != null) f.put("project", projectCode);
        f.put("objective", "Automatizar a atualização do inventário de equipamentos de rede a partir dos sistemas de gerência.");
        f.put("currentProblem", "Hoje o inventário é atualizado manualmente em planilhas, gerando divergências e retrabalho semanal.");
        f.put("justification", "Divergências no inventário causam falhas no planejamento de capacidade e atrasos em manutenções.");
        f.put("expectedBenefits", "Redução de 80% do esforço manual e inventário confiável para planejamento.");
        f.put("impactedAreas", "Operações de Rede, Planejamento");
        f.put("impactLevel", "HIGH");
        f.put("urgency", "MEDIUM");
        f.put("businessFocalPoint", "João Ponto Focal");
        f.put("systemsInvolved", "NetCool, Inventário Corporativo");
        return f;
    }
}
