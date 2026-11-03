package ee.tuleva.onboarding.insurance;

import static org.springframework.util.StringUtils.hasText;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

public record IncomeSource(@NotNull Type source, @Nullable @Size(max = 300) String details) {

  @AssertTrue
  public boolean isDetailedWhenAsked() {
    return source == null || !source.isDetailed() || hasText(details);
  }

  @Getter
  @RequiredArgsConstructor
  public enum Type {
    SALARY(false),
    SELF_EMPLOYMENT(false),
    DIVIDENDS(true),
    INVESTMENTS(true),
    LOAN(true),
    INHERITANCE(true),
    RENTAL(true),
    REAL_ESTATE(true),
    FAMILY_TRANSFERS(true),
    OTHER(true);

    private final boolean detailed;
  }
}
