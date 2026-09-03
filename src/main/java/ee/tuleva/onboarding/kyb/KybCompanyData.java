package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.ariregister.RepresentationRight;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record KybCompanyData(
    CompanyDto company,
    PersonalCode personalCode,
    CompanyStatus status,
    List<KybRelatedPerson> relatedPersons,
    @Nullable SelfCertification selfCertification,
    @Nullable String countryCode,
    @Nullable String fullAddress,
    @Nullable LocalDate foundingDate,
    List<RepresentationRight> representationRights) {}
