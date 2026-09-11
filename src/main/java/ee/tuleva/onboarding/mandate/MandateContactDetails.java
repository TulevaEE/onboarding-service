package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.country.Country;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record MandateContactDetails(
    @Nullable String email,
    Country address,
    boolean secondPillarActive,
    boolean thirdPillarActive,
    String noticeNeeded,
    LanguagePreference languagePreference) {

  public enum LanguagePreference {
    EST,
    RUS,
    ENG
  }
}
