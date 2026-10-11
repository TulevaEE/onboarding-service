package ee.tuleva.onboarding.hackathon;

import java.time.Instant;

public class HackathonRegistrationClosedException extends HackathonException {

  public HackathonRegistrationClosedException(Instant deadline, Instant now) {
    super(
        "HACKATHON_REGISTRATION_CLOSED",
        "Hackathon registration is closed: deadline=" + deadline + ", now=" + now);
  }
}
