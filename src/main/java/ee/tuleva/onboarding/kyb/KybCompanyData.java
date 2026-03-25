package ee.tuleva.onboarding.kyb;

import java.util.List;

public record KybCompanyData(
    CompanyDto company,
    PersonalCode personalCode,
    CompanyStatus status,
    List<KybRelatedPerson> relatedPersons,
    SelfCertification selfCertification) {}
