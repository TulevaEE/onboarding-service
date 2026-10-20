package ee.tuleva.onboarding.oauth.server;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/connected-apps")
@RequiredArgsConstructor
class ConnectedAppsController {

  private final ConnectedApps connectedApps;

  @GetMapping
  List<ConnectedAppResponse> list(@AuthenticationPrincipal AuthenticatedPerson person) {
    return connectedApps.of(person.getPersonalCode());
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> revoke(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedPerson person) {
    if (!connectedApps.revoke(person.getPersonalCode(), id)) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.noContent().build();
  }
}
