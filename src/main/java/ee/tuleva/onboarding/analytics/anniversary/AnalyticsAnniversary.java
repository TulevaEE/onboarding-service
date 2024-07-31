package ee.tuleva.onboarding.analytics.anniversary;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import lombok.Getter;

public record AnalyticsAnniversary(
    @Getter String personalCode,
    @Getter String firstName,
    @Getter String lastName,
    @Getter String email,
    Integer fullYears
) implements Person, Emailable {
}
