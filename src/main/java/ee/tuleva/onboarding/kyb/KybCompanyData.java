package ee.tuleva.onboarding.kyb;

import jakarta.annotation.Nullable;
import java.util.List;

public record KybCompanyData(
    CompanyDto company,
    PersonalCode personalCode,
    CompanyStatus status,
    List<KybRelatedPerson> relatedPersons,
    SelfCertification selfCertification,
    @Nullable String countryCode,
    @Nullable String fullAddress) {}
