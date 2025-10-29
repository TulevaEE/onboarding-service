package ee.tuleva.onboarding.analytics.paymentrate;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Builder
public record PaymentRateAbandonment(
    @Getter(onMethod_ = @Override) String personalCode,
    @Getter(onMethod_ = @Override) String firstName,
    @Getter(onMethod_ = @Override) String lastName,
    @Getter(onMethod_ = @Override) String email,
    String language,
    LocalDateTime lastEmailSentDate,
    Integer count,
    String path,
    Integer currentRate,
    Integer pendingRate,
    LocalDate pendingRateDate)
    implements Person, Emailable {}
