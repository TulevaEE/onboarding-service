package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.AmlCheckType.KYC_CHECK;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static ee.tuleva.onboarding.kyb.KybRelationshipRoles.*;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.ariregister.AddressDetails;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyAddress;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KybCompanyDataMapper {

  private static final String NATURAL_PERSON_TYPE = "F"; // füüsiline isik (natural person)

  private final AmlCheckRepository amlCheckRepository;

  KybCompanyData toKybCompanyData(
      CompanyDetail detail,
      PersonalCode personalCode,
      List<CompanyRelationship> relationships,
      BeneficialOwners beneficialOwners,
      SelfCertification selfCertification) {

    var status = detail.getStatus().map(CompanyStatus::valueOf).orElse(null);
    var legalForm = detail.getLegalForm().map(LegalForm::fromString).orElse(null);

    var address = detail.getAddress();
    var countryCode =
        address.map(CompanyAddress::addressDetails).map(AddressDetails::countryCode).orElse(null);
    var fullAddress = address.map(CompanyAddress::fullAddress).orElse(null);
    var foundingDate = detail.getFoundingDate().orElse(null);
    var representationRights = detail.getRepresentationRights();

    var companyDto =
        new CompanyDto(
            new RegistryCode(detail.getRegistryCode()),
            detail.getName(),
            detail.getMainActivity().orElse(null),
            legalForm);

    var beneficialOwnerCodes =
        beneficialOwners.owners().stream()
            .map(BeneficialOwner::personalCode)
            .filter(Objects::nonNull)
            .collect(toSet());

    var grouped =
        relationships.stream()
            .filter(r -> r.personalCode() != null)
            .collect(Collectors.groupingBy(CompanyRelationship::personalCode))
            .entrySet()
            .stream()
            .map(
                entry ->
                    toRelatedPerson(
                        new PersonalCode(entry.getKey()),
                        entry.getValue(),
                        beneficialOwnerCodes.contains(entry.getKey())));

    var ungrouped =
        relationships.stream()
            .filter(r -> r.personalCode() == null)
            .map(r -> toRelatedPerson(null, List.of(r), false));

    var relationshipCodes =
        relationships.stream()
            .map(CompanyRelationship::personalCode)
            .filter(Objects::nonNull)
            .collect(toSet());
    var withoutRelationships =
        beneficialOwners.owners().stream()
            .filter(
                owner ->
                    owner.personalCode() == null
                        || !relationshipCodes.contains(owner.personalCode()))
            .map(this::toRelatedPerson);

    var hidden = Collections.nCopies(beneficialOwners.hiddenCount(), unidentifiedBeneficialOwner());

    var relatedPersons =
        Stream.concat(
                Stream.concat(grouped, ungrouped),
                Stream.concat(withoutRelationships, hidden.stream()))
            .toList();

    return new KybCompanyData(
        companyDto,
        personalCode,
        status,
        relatedPersons,
        selfCertification,
        countryCode,
        fullAddress,
        foundingDate,
        representationRights);
  }

  private KybRelatedPerson toRelatedPerson(
      @Nullable PersonalCode code, List<CompanyRelationship> roles, boolean beneficialOwner) {
    var naturalPerson = roles.stream().allMatch(r -> NATURAL_PERSON_TYPE.equals(r.personType()));
    var boardMember = roles.stream().anyMatch(r -> BOARD_MEMBER_ROLE.equals(r.roleCode()));
    var shareholder = roles.stream().anyMatch(r -> SHAREHOLDER_ROLES.contains(r.roleCode()));
    var ownershipPercent =
        roles.stream()
            .map(CompanyRelationship::ownershipPercent)
            .filter(Objects::nonNull)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    return new KybRelatedPerson(
        code,
        naturalPerson,
        boardMember,
        shareholder,
        beneficialOwner,
        ownershipPercent,
        code != null ? resolveKycStatus(code.value()) : UNKNOWN);
  }

  private KybRelatedPerson toRelatedPerson(BeneficialOwner owner) {
    var code = owner.personalCode() != null ? new PersonalCode(owner.personalCode()) : null;
    return new KybRelatedPerson(
        code,
        true,
        false,
        false,
        true,
        BigDecimal.ZERO,
        code != null ? resolveKycStatus(code.value()) : UNKNOWN);
  }

  private KybRelatedPerson unidentifiedBeneficialOwner() {
    return new KybRelatedPerson(null, true, false, false, true, BigDecimal.ZERO, UNKNOWN);
  }

  private KybKycStatus resolveKycStatus(String personalCode) {
    if (amlCheckRepository.existsByPersonalCodeAndTypeAndSuccessAndCreatedTimeAfter(
        personalCode, KYC_CHECK, true, aYearAgo())) {
      return COMPLETED;
    }
    if (amlCheckRepository.existsByPersonalCodeAndTypeAndSuccessAndCreatedTimeAfter(
        personalCode, KYC_CHECK, false, aYearAgo())) {
      return REJECTED;
    }
    return UNKNOWN;
  }
}
