package ee.tuleva.onboarding.ariregister;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompanyRelationship(
    String personType,
    String roleCode,
    String role,
    String firstName,
    String lastName,
    String personalCode,
    LocalDate birthDate,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal ownershipPercent,
    String controlMethod,
    String countryCode) {}
