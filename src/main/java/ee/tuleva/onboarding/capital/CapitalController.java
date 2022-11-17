package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.auth.AuthenticatedPersonPrincipal;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CapitalController {

  public static final String CAPITAL_URI = "/me/capital";
  private final UserService userService;
  private final CapitalService capitalService;

  @Operation(summary = "Get info about current user initial capital")
  @GetMapping(CAPITAL_URI)
  public CapitalStatement capitalStatement(
      @AuthenticatedPersonPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);
    return user.getMember()
        .map(member -> capitalService.getCapitalStatement(member.getId()))
        .orElseThrow(() -> new RuntimeException());
  }
}
