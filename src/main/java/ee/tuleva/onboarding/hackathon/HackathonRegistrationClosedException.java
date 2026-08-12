package ee.tuleva.onboarding.hackathon;

import java.time.Instant;

public class HackathonRegistrationClosedException extends RuntimeException {

  public HackathonRegistrationClosedException(Instant deadline, Instant now) {
    super("Hackathon registration is closed: deadline=" + deadline + ", now=" + now);
  }
}
