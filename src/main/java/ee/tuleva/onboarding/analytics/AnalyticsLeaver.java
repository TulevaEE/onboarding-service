package ee.tuleva.onboarding.analytics;

import java.time.LocalDate;

public record AnalyticsLeaver(
    String currentFund,
    String newFund,
    String personalCode,
    String firstName,
    String lastName,
    Double shareAmount,
    Double sharePercentage,
    LocalDate dateCreated,
    Double fundOngoingChargesFigure,
    String fundNameEstonian,
    String email,
    String language,
    Integer age) {}
