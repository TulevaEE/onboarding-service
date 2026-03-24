package ee.tuleva.onboarding.kyb;

import java.util.List;

public record KybCompanyData(
    CompanyDto company,
    String personalCode,
    CompanyStatus status,
    List<KybRelatedPerson> relatedPersons,
    SelfCertification selfCertification) {}
