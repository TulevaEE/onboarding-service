package ee.tuleva.onboarding.party;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.time.LocalDate;

@JsonInclude(NON_NULL)
public record ChildResponse(
    Status status,
    @Nullable String firstName,
    @Nullable String lastName,
    @Nullable LocalDate dateOfBirth) {

  public enum Status {
    VERIFIED,
    UNDER_REVIEW
  }

  static ChildResponse verified(ChildOnboardingResult result) {
    return new ChildResponse(
        Status.VERIFIED, result.firstName(), result.lastName(), result.dateOfBirth());
  }

  static ChildResponse underReview() {
    return new ChildResponse(Status.UNDER_REVIEW, null, null, null);
  }
}
