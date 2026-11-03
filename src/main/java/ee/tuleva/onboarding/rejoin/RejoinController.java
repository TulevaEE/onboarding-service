package ee.tuleva.onboarding.rejoin;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
class RejoinController {

  private final RejoinPermissions rejoinPermissions;

  @Operation(summary = "Whether the logged-in person may rejoin the II pillar")
  @GetMapping("/rejoin-permission")
  public RejoinPermissionResponse getRejoinPermission(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return new RejoinPermissionResponse(rejoinPermissions.decideFor(person));
  }
}
