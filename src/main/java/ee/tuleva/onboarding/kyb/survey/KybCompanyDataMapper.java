package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
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

    var companyDto =
        new CompanyDto(
            new RegistryCode(detail.getRegistryCode()),
            detail.getName(),
            detail.getMainActivity().orElse(null));

    var relatedPersons =
        relationships.stream()
            .collect(Collectors.groupingBy(CompanyRelationship::personalCode))
            .entrySet()
            .stream()
            .map(
                entry -> {
                  var code = new PersonalCode(entry.getKey());
                  var roles = entry.getValue();
                  var boardMember =
                      roles.stream().anyMatch(r -> BOARD_MEMBER_ROLE.equals(r.roleCode()));
                  var shareholder =
                      roles.stream().anyMatch(r -> SHAREHOLDER_ROLE.equals(r.roleCode()));
                  var beneficialOwner = roles.stream().anyMatch(r -> r.controlMethod() != null);
                  var ownershipPercent =
                      roles.stream()
                          .map(CompanyRelationship::ownershipPercent)
                          .filter(p -> p != null)
                          .max(BigDecimal::compareTo)
                          .orElse(BigDecimal.ZERO);
                  return new KybRelatedPerson(
                      code,
                      boardMember,
                      shareholder,
                      beneficialOwner,
                      ownershipPercent,
                      KybKycStatus.UNKNOWN); // TODO: look up actual KYC status from onboarding
                })
            .toList();

    return new KybCompanyData(companyDto, personalCode, status, relatedPersons, selfCertification);
  }
}
