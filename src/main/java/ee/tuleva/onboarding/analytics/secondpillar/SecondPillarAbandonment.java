package ee.tuleva.onboarding.analytics.secondpillar;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Builder
public record SecondPillarAbandonment(
    @Getter String personalCode,
    @Getter String firstName,
    @Getter String lastName,
    @Getter String email,
    String language,
    Instant lastEmailSentDate,
    Integer count,
    Instant timestamp,
    String path,
    Integer currentRate,
    Integer pendingRate,
    LocalDate pendingRateDate)
    implements Person, Emailable {}
