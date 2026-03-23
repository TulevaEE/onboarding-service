package ee.tuleva.onboarding.kyb;

import java.util.List;

public record KybCompanyData(
    String registryCode,
    String personalCode,
    CompanyStatus status,
    List<KybRelatedPerson> relatedPersons) {}
