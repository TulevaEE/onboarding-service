package ee.tuleva.onboarding.party;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(SERVICE_UNAVAILABLE)
public class MinorGuardianBackfillUnavailable extends RuntimeException {

  public MinorGuardianBackfillUnavailable() {
    super("Minor guardian backfill cannot run: no requester is configured or its last day is past");
  }
}
