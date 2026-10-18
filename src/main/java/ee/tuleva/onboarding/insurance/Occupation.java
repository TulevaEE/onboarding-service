package ee.tuleva.onboarding.insurance;

import static ee.tuleva.onboarding.insurance.Occupation.Status.EMPLOYED;
import static org.springframework.util.StringUtils.hasText;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

public record Occupation(
    @NotNull Status status,
    @Nullable @Size(max = 200) String position,
    @Nullable @Size(max = 200) String employer,
    @Nullable @Size(max = 200) String employerAddress,
    @Nullable @Size(max = 500) String description) {

  @AssertTrue
  public boolean isEmployerNamedWhenEmployed() {
    return status != EMPLOYED
        || (hasText(position) && hasText(employer) && hasText(employerAddress));
  }

  @AssertTrue
  public boolean isDescribedWhenAsked() {
    return status == null || !status.isDescribed() || hasText(description);
  }

  @Getter
  @RequiredArgsConstructor
  public enum Status {
    EMPLOYED(false),
    SELF_EMPLOYED(true),
    COMPANY_OWNER(true),
    STUDENT(false),
    RETIRED(false),
    UNEMPLOYED(false),
    OTHER(true);

    private final boolean described;
  }
}
