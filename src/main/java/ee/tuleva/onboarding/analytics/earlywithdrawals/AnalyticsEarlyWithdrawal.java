package ee.tuleva.onboarding.analytics.earlywithdrawals;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

public record AnalyticsEarlyWithdrawal(
    @Getter String personalCode,
    @Getter String firstName,
    @Getter String lastName,
    @Getter String email,
    String language,
    LocalDate earlyWithdrawalDate,
    String earlyWithdrawalStatus,
    @Getter LocalDateTime lastEmailSentDate)
    implements Person, Emailable {}
