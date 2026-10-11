package ee.tuleva.onboarding.hackathon;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/hackathon-ideas")
@RequiredArgsConstructor
public class HackathonIdeaController {

  private final HackathonIdeaService hackathonIdeaService;

  @GetMapping
  @Operation(summary = "Get the hackathon ideas I have submitted and whether submission is open")
  public HackathonIdeasDto getIdeas(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return hackathonIdeaService.getIdeas(authenticatedPerson);
  }

  @PostMapping
  @Operation(summary = "Submit a hackathon idea")
  public HackathonIdeaDto submit(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody HackathonIdeaRequest request) {
    return hackathonIdeaService.submit(authenticatedPerson, request);
  }
}
