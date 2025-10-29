package ee.tuleva.onboarding.analytics.paymentrate;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Builder
public record PaymentRateAbandonment(
    @Getter String personalCode,
    @Getter String firstName,
    @Getter String lastName,
    @Getter String email,
    String language,
    @Getter LocalDateTime lastEmailSentDate,
    Integer count,
    String path,
    Integer currentRate,
    Integer pendingRate,
    LocalDate pendingRateDate)
    implements Person, Emailable {}
