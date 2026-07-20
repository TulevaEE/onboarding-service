package ee.tuleva.onboarding.auth.role;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoleController {

  private final RoleSwitchService roleSwitchService;

  @GetMapping("/v1/me/roles")
  public List<Role> getRoles(@AuthenticationPrincipal AuthenticatedPerson person) {
    return roleSwitchService.getRoles(person);
  }

  // Kept separate from /v1/me/roles: these are children the parent can JOIN, not switch to. A
  // PENDING_KYC link grants no access, so it must never appear as a switchable role.
  @GetMapping("/v1/me/pending-child-onboardings")
  public List<PendingChildResponse> getPendingChildOnboardings(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return roleSwitchService.getPendingChildOnboardings(person);
  }

  @PostMapping("/v1/me/role")
  public AuthenticationTokens switchRole(
      @AuthenticationPrincipal AuthenticatedPerson person,
      @Valid @RequestBody SwitchRoleCommand command) {
    return roleSwitchService.switchRole(person, command);
  }
}
