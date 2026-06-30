package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.Map.entry;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckHistory;
import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AmlKybCheckHistory implements KybCheckHistory {

  private static final Map<AmlCheckType, KybCheckType> REVERSE_TYPE_MAPPING =
      Map.ofEntries(
          entry(AmlCheckType.KYB_COMPANY_STRUCTURE, KybCheckType.COMPANY_STRUCTURE),
          entry(AmlCheckType.KYB_SOLE_MEMBER_OWNERSHIP, KybCheckType.SOLE_MEMBER_OWNERSHIP),
          entry(AmlCheckType.KYB_DUAL_MEMBER_OWNERSHIP, KybCheckType.DUAL_MEMBER_OWNERSHIP),
          entry(
              AmlCheckType.KYB_SINGLE_BOARD_MEMBER_OWNERSHIP,
              KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP),
          entry(AmlCheckType.KYB_COMPANY_ACTIVE, KybCheckType.COMPANY_ACTIVE),
          entry(AmlCheckType.KYB_COMPANY_AGE, KybCheckType.COMPANY_AGE),
          entry(AmlCheckType.KYB_COMPANY_LEGAL_FORM, KybCheckType.COMPANY_LEGAL_FORM),
          entry(AmlCheckType.KYB_RELATED_PERSONS_KYC, KybCheckType.RELATED_PERSONS_KYC),
          entry(AmlCheckType.KYB_COMPANY_SANCTION, KybCheckType.COMPANY_SANCTION),
          entry(AmlCheckType.KYB_COMPANY_PEP, KybCheckType.COMPANY_PEP),
          entry(AmlCheckType.KYB_HIGH_RISK_NACE, KybCheckType.HIGH_RISK_NACE),
          entry(
              AmlCheckType.KYB_COMPANY_REGISTERED_IN_ESTONIA,
              KybCheckType.COMPANY_REGISTERED_IN_ESTONIA),
          entry(AmlCheckType.KYB_SELF_CERTIFICATION, KybCheckType.SELF_CERTIFICATION));

  private final AmlCheckRepository amlCheckRepository;
  private final CompanyRepository companyRepository;

  @Override
  public List<KybCheck> getLatestChecks(PersonalCode personalCode, RegistryCode registryCode) {
    UUID companyId = resolveCompanyId(registryCode);
    return amlCheckRepository
        .findAllByPersonalCodeAndCompanyIdAndCreatedTimeAfter(
            personalCode.value(), companyId, aYearAgo())
        .stream()
        .filter(check -> REVERSE_TYPE_MAPPING.containsKey(check.getType()))
        .sorted(Comparator.comparing(AmlCheck::getCreatedTime).reversed())
        .map(this::toKybCheck)
        .toList();
  }

  private UUID resolveCompanyId(RegistryCode registryCode) {
    return companyRepository
        .findByRegistryCode(registryCode.value())
        .map(Company::getId)
        .orElse(null);
  }

  private KybCheck toKybCheck(AmlCheck amlCheck) {
    return new KybCheck(
        REVERSE_TYPE_MAPPING.get(amlCheck.getType()), amlCheck.isSuccess(), amlCheck.getMetadata());
  }
}
