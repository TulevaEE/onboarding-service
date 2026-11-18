package ee.tuleva.onboarding.party;

import static org.springframework.http.HttpStatus.CONFLICT;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(CONFLICT)
public class MinorGuardianLookupAlreadyRunning extends RuntimeException {

  public MinorGuardianLookupAlreadyRunning() {
    super("Minor guardian lookup is already running");
  }
}
