package com.demandhub.platform.refinement.web;

import com.demandhub.platform.ai.domain.AiSuggestion;
import com.demandhub.platform.ai.service.TechnicalArtifactService;
import com.demandhub.platform.refinement.domain.Decision;
import com.demandhub.platform.refinement.domain.Meeting;
import com.demandhub.platform.refinement.domain.RefinementEntities.DecisionType;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemOrigin;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemStatus;
import com.demandhub.platform.refinement.domain.RefinementEntities.ItemType;
import com.demandhub.platform.refinement.domain.RefinementItem;
import com.demandhub.platform.refinement.service.RefinementService;
import com.demandhub.platform.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demands/{demandId}/refinement")
public class RefinementController {

    private final RefinementService service;
    private final TechnicalArtifactService artifacts;

    public RefinementController(RefinementService service, TechnicalArtifactService artifacts) {
        this.service = service;
        this.artifacts = artifacts;
    }

    @GetMapping
    public RefinementService.Snapshot snapshot(@PathVariable UUID demandId) {
        return service.snapshot(demandId, SecurityUtils.currentUser());
    }

    @PostMapping("/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    public Meeting meeting(@PathVariable UUID demandId, @Valid @RequestBody MeetingRequest req) {
        return service.addMeeting(demandId, req.title(), req.heldAt(), req.participants(), req.notes(), SecurityUtils.currentUser());
    }

    /** Refinement Agent: propõe itens e decisões a partir das notas da reunião (sugestões pendentes de aceite). */
    @PostMapping("/meetings/{meetingId}/ai-suggestions")
    public List<AiSuggestion> suggest(@PathVariable UUID demandId, @PathVariable UUID meetingId) {
        return artifacts.suggestFromMeeting(demandId, meetingId, SecurityUtils.currentUser());
    }

    @PostMapping("/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public Decision decision(@PathVariable UUID demandId, @Valid @RequestBody DecisionRequest req) {
        return service.addDecision(demandId, req.meetingId(), req.type(), req.description(), req.rationale(), req.decidedBy(),
                SecurityUtils.currentUser());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public RefinementItem item(@PathVariable UUID demandId, @Valid @RequestBody ItemRequest req) {
        return service.addItem(demandId, req.type(), req.description(), ItemOrigin.HUMAN, SecurityUtils.currentUser());
    }

    @PatchMapping("/items/{itemId}")
    public RefinementItem itemStatus(@PathVariable UUID demandId, @PathVariable UUID itemId, @Valid @RequestBody ItemStatusRequest req) {
        return service.setItemStatus(demandId, itemId, req.status(), SecurityUtils.currentUser());
    }

    public record MeetingRequest(@NotBlank @Size(max = 200) String title, Instant heldAt, @Size(max = 2000) String participants,
                                 @Size(max = 50000) String notes) {}

    public record DecisionRequest(UUID meetingId, @NotNull DecisionType type, @NotBlank @Size(max = 4000) String description,
                                  @Size(max = 4000) String rationale, @Size(max = 200) String decidedBy) {}

    public record ItemRequest(@NotNull ItemType type, @NotBlank @Size(max = 4000) String description) {}

    public record ItemStatusRequest(@NotNull ItemStatus status) {}
}
