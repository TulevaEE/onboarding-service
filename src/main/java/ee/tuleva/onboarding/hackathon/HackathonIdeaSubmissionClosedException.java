package ee.tuleva.onboarding.hackathon;

import java.time.Instant;

public class HackathonIdeaSubmissionClosedException extends HackathonException {

  public HackathonIdeaSubmissionClosedException(Instant deadline, Instant now) {
    super(
        "HACKATHON_IDEA_SUBMISSION_CLOSED",
        "Hackathon idea submission is closed: deadline=" + deadline + ", now=" + now);
  }
}
