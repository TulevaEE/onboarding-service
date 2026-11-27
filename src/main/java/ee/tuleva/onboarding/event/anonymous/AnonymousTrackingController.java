package ee.tuleva.onboarding.event.anonymous;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Open without login: a sink for analytics only. It stores the event and does nothing else, so a
// forged call can add noise to anonymous_event but cannot change anything about anyone.
@RestController
@RequiredArgsConstructor
public class AnonymousTrackingController {

  private final AnonymousEventRepository repository;
  private final AnonymousEventRateLimiter rateLimiter;
  private final Clock clock;

  @PostMapping("/v1/t/anonymous")
  @ResponseStatus(NO_CONTENT)
  @Operation(summary = "Add an event from a person who is not logged in")
  public void track(@RequestBody AnonymousEventCommand command, HttpServletRequest request) {
    if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
      throw new ResponseStatusException(TOO_MANY_REQUESTS);
    }
    if (!command.isAcceptable()) {
      throw new ResponseStatusException(BAD_REQUEST, "Event not accepted");
    }
    repository.save(command, clock.instant());
  }
}
