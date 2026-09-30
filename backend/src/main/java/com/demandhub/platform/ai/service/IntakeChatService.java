package com.demandhub.platform.ai.service;

import com.demandhub.platform.ai.agent.AgentOrchestrator;
import com.demandhub.platform.ai.agent.AgentResult;
import com.demandhub.platform.ai.agent.AgentSupport.FieldSuggestion;
import com.demandhub.platform.ai.agent.intake.IntakeAssistantAgent;
import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.domain.ChatMessage;
import com.demandhub.platform.ai.llm.LlmClient.LlmMessage;
import com.demandhub.platform.ai.repository.AiRepositories.ChatMessageRepository;
import com.demandhub.platform.demand.domain.Demand;
import com.demandhub.platform.demand.domain.DemandField;
import com.demandhub.platform.demand.service.DemandFieldAccessor;
import com.demandhub.platform.demand.service.DemandService;
import com.demandhub.platform.demand.service.DemandSubmissionValidator;
import com.demandhub.platform.shared.error.ApiException;
import com.demandhub.platform.shared.security.CurrentUser;
import com.demandhub.platform.shared.security.Permissions;
import com.demandhub.platform.shared.util.Texts;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chatbot de abertura: conversa persistida, sugestões de campo como PENDING (nunca aplicadas automaticamente). */
@Service
public class IntakeChatService {

    private static final int HISTORY_LIMIT = 12;

    private final ChatMessageRepository messages;
    private final DemandService demandService;
    private final DemandFieldAccessor accessor;
    private final DemandSubmissionValidator validator;
    private final AgentOrchestrator orchestrator;
    private final IntakeAssistantAgent agent;
    private final AiSuggestionService suggestions;
    private final Clock clock;

    public IntakeChatService(ChatMessageRepository messages, DemandService demandService, DemandFieldAccessor accessor,
                             DemandSubmissionValidator validator, AgentOrchestrator orchestrator, IntakeAssistantAgent agent,
                             AiSuggestionService suggestions, Clock clock) {
        this.messages = messages;
        this.demandService = demandService;
        this.accessor = accessor;
        this.validator = validator;
        this.orchestrator = orchestrator;
        this.agent = agent;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> history(UUID demandId, CurrentUser user) {
        demandService.getForView(demandId, user);
        return messages.findByDemandIdOrderByCreatedAtAsc(demandId);
    }

    @Transactional
    public ChatReply send(UUID demandId, String text, CurrentUser user) {
        if (!user.has(Permissions.AI_USE)) {
            throw ApiException.forbidden("Sem permissão para usar o assistente de IA.");
        }
        Demand demand = demandService.getForView(demandId, user);
        if (!demand.isOwnedBy(user.id()) || !demand.isDraft()) {
            throw ApiException.businessRule("CHAT_NOT_AVAILABLE", "O assistente de abertura está disponível apenas para o solicitante durante o rascunho.");
        }
        List<ChatMessage> previous = messages.findByDemandIdOrderByCreatedAtAsc(demandId);
        save(demandId, "USER", text, null);

        Map<String, String> form = accessor.readAll(demand);
        form.remove(DemandField.PRIORITY.key());
        List<String> missing = new ArrayList<>(validator.validate(demand).keySet());
        String lastAssistant = previous.stream().filter(m -> "ASSISTANT".equals(m.getRole()))
                .reduce((a, b) -> b).map(ChatMessage::getContent).orElse(null);
        List<LlmMessage> history = previous.stream().skip(Math.max(0, previous.size() - HISTORY_LIMIT))
                .map(m -> "USER".equals(m.getRole()) ? LlmMessage.user(m.getContent()) : LlmMessage.assistant(m.getContent()))
                .collect(Collectors.toList());

        AgentResult<IntakeAssistantAgent.Output> result = orchestrator.run(agent,
                new IntakeAssistantAgent.Input(form, missing, lastAssistant, text), demandId, history);
        if (!result.success()) {
            ChatMessage reply = save(demandId, "ASSISTANT",
                    "Não consegui processar sua mensagem agora. Você pode continuar preenchendo o formulário normalmente.", result.runId());
            return new ChatReply(reply, List.of(), result.mode());
        }
        ChatMessage reply = save(demandId, "ASSISTANT", result.output().reply(), result.runId());
        List<AiSuggestion> created = new ArrayList<>();
        for (FieldSuggestion fs : result.output().suggestions()) {
            String current = form.get(fs.field());
            if (!Texts.isBlank(current) && current.trim().equalsIgnoreCase(fs.value().trim())) {
                continue;
            }
            created.add(suggestions.create(demandId, result.runId(), agent.name(), AiSuggestion.Kind.FIELD_VALUE, fs.field(),
                    fs.value(), current, fs.confidence(), AiSuggestion.SourceType.CHAT, null,
                    fs.rationale() == null ? "Sugestão do assistente com base na conversa." : fs.rationale()));
        }
        return new ChatReply(reply, created, result.mode());
    }

    private ChatMessage save(UUID demandId, String role, String content, UUID runId) {
        ChatMessage m = new ChatMessage();
        m.setDemandId(demandId);
        m.setRole(role);
        m.setContent(Texts.truncate(content, 8000));
        m.setAgentRunId(runId);
        m.setCreatedAt(clock.instant());
        return messages.save(m);
    }

    public record ChatReply(ChatMessage message, List<AiSuggestion> suggestions, String mode) {}
}
