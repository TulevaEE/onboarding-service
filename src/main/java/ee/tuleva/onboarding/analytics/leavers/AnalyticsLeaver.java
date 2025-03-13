package ee.tuleva.onboarding.analytics.leavers;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.Emailable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

public record AnalyticsLeaver(
    String currentFund,
    String newFund,
    @Getter String personalCode,
    @Getter String firstName,
    @Getter String lastName,
    Double shareAmount,
    Double sharePercentage,
    LocalDate dateCreated,
    Double fundOngoingChargesFigure,
    String fundNameEstonian,
    @Getter String email,
    String language,
    @Getter LocalDateTime lastEmailSentDate)
    implements Person, Emailable {}
