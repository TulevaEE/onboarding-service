package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.ariregister.RepresentationRight;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;

public record KybCompanyData(
    CompanyDto company,
    PersonalCode personalCode,
    CompanyStatus status,
    List<KybRelatedPerson> relatedPersons,
    SelfCertification selfCertification,
    @Nullable String countryCode,
    @Nullable String fullAddress,
    @Nullable LocalDate foundingDate,
    List<RepresentationRight> representationRights) {}
