package ee.tuleva.onboarding.secondpillarassets;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
class SecondPillarAssetsController {

  private final EpisService episService;

  @Operation(summary = "Get second pillar assets breakdown")
  @GetMapping("/second-pillar-assets")
  public SecondPillarAssets getSecondPillarAssets(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return episService.getSecondPillarAssets(person);
  }
}
