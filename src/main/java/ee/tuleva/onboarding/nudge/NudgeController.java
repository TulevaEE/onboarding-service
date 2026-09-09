package ee.tuleva.onboarding.nudge;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/nudge")
@RequiredArgsConstructor
public class NudgeController {

  private final NudgeDecisionService nudgeDecisionService;
  private final UserService userService;

  @Operation(summary = "Which single nudge to show the current user after the given flow")
  @GetMapping
  public NudgeDecision nudge(
      @AuthenticationPrincipal AuthenticatedPerson person, @RequestParam NudgeContext context) {
    User user =
        userService.getByIdOrThrow(
            requireNonNull(person.getUserId(), "User id missing for authenticated person"));
    NudgeAccount actingParty =
        person.getRole() != null ? NudgeAccount.of(person.getRole()) : NudgeAccount.self(person);
    return nudgeDecisionService.decide(user, actingParty, context);
  }
}
