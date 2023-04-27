package ee.tuleva.onboarding.contribution;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
class ContributionController {

  private final EpisService episService;

  @Operation(summary = "Get Contributions")
  @GetMapping("/contributions")
  public List<Contribution> getContributions(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return episService.getContributions(person);
  }
}
