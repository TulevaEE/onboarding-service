package ee.tuleva.onboarding.event;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/t")
@Slf4j
@RequiredArgsConstructor
public class TrackingController {

  private final ApplicationEventPublisher eventPublisher;

  @PostMapping
  @Operation(summary = "Add tracked event")
  public void track(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody TrackedEventCommand command) {

    if (command.getType() == null || !command.getType().isClientPublishable()) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Event type is missing or server-only: type=" + command.getType());
    }

    eventPublisher.publishEvent(
        new TrackableEvent(authenticatedPerson, command.getType(), command.getData()));
  }
}
