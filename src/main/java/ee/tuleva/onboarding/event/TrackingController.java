package ee.tuleva.onboarding.event;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/t")
@Slf4j
@RequiredArgsConstructor
public class TrackingController {

  private final TrackableEventPublisher trackableEventPublisher;

  @PostMapping
  @Operation(summary = "Add tracked event")
  public void track(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody TrackedEventCommand command) {

    trackableEventPublisher.publish(
        authenticatedPerson, TrackableEventType.valueOf(command.getType()), command.getData());
  }
}
