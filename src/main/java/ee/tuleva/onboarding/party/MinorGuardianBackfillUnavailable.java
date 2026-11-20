package ee.tuleva.onboarding.party;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(SERVICE_UNAVAILABLE)
public class MinorGuardianBackfillUnavailable extends RuntimeException {

  public MinorGuardianBackfillUnavailable() {
    super(
        "Minor guardian backfill cannot run: aml-service designates no requester or cannot be"
            + " asked, or its last day is past");
  }
}
