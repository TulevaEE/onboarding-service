package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
class KybCompanyDataMapper {

  private static final String BOARD_MEMBER_ROLE = "JUHL";
  private static final String SHAREHOLDER_ROLE = "S";

  KybCompanyData toKybCompanyData(
      CompanyDetail detail,
      PersonalCode personalCode,
      List<CompanyRelationship> relationships,
      SelfCertification selfCertification) {

    var status = detail.getStatus().map(CompanyStatus::valueOf).orElse(null);
    var legalForm = detail.getLegalForm().map(LegalForm::valueOf).orElse(null);

    var companyDto =
        new CompanyDto(
            new RegistryCode(detail.getRegistryCode()),
            detail.getName(),
            detail.getMainActivity().orElse(null),
            legalForm);

    var grouped =
        relationships.stream()
            .filter(r -> r.personalCode() != null)
            .collect(Collectors.groupingBy(CompanyRelationship::personalCode))
            .entrySet()
            .stream()
            .map(entry -> toRelatedPerson(new PersonalCode(entry.getKey()), entry.getValue()));

    var ungrouped =
        relationships.stream()
            .filter(r -> r.personalCode() == null)
            .map(r -> toRelatedPerson(null, List.of(r)));

    var relatedPersons = Stream.concat(grouped, ungrouped).toList();

    return new KybCompanyData(companyDto, personalCode, status, relatedPersons, selfCertification);
  }

  private KybRelatedPerson toRelatedPerson(PersonalCode code, List<CompanyRelationship> roles) {
    var boardMember = roles.stream().anyMatch(r -> BOARD_MEMBER_ROLE.equals(r.roleCode()));
    var shareholder = roles.stream().anyMatch(r -> SHAREHOLDER_ROLE.equals(r.roleCode()));
    var beneficialOwner = roles.stream().anyMatch(r -> r.controlMethod() != null);
    var ownershipPercent =
        roles.stream()
            .map(CompanyRelationship::ownershipPercent)
            .filter(p -> p != null)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    return new KybRelatedPerson(
        code, boardMember, shareholder, beneficialOwner, ownershipPercent, KybKycStatus.UNKNOWN);
  }
}
