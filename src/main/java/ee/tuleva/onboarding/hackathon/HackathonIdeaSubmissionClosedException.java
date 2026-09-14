package ee.tuleva.onboarding.hackathon;

import java.time.Instant;

public class HackathonIdeaSubmissionClosedException extends RuntimeException {

  public HackathonIdeaSubmissionClosedException(Instant deadline, Instant now) {
    super("Hackathon idea submission is closed: deadline=" + deadline + ", now=" + now);
  }
}
