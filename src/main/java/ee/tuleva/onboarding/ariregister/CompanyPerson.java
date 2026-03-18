package ee.tuleva.onboarding.ariregister;

import java.time.LocalDate;

public record CompanyPerson(
    String firstName,
    String lastName,
    String personalCode,
    String role,
    LocalDate startDate,
    LocalDate endDate) {}
