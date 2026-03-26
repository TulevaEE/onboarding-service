package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.Map.entry;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckHistory;
import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.PersonalCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
              AmlCheckType.KYB_SOLE_BOARD_MEMBER_IS_OWNER, KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER),
          entry(AmlCheckType.KYB_COMPANY_ACTIVE, KybCheckType.COMPANY_ACTIVE),
          entry(AmlCheckType.KYB_RELATED_PERSONS_KYC, KybCheckType.RELATED_PERSONS_KYC),
          entry(AmlCheckType.KYB_COMPANY_SANCTION, KybCheckType.COMPANY_SANCTION),
          entry(AmlCheckType.KYB_COMPANY_PEP, KybCheckType.COMPANY_PEP),
          entry(AmlCheckType.KYB_HIGH_RISK_NACE, KybCheckType.HIGH_RISK_NACE),
          entry(AmlCheckType.KYB_SELF_CERTIFICATION, KybCheckType.SELF_CERTIFICATION));

  private final AmlCheckRepository amlCheckRepository;

  @Override
  public List<KybCheck> getLatestChecks(PersonalCode personalCode) {
    return amlCheckRepository
        .findAllByPersonalCodeAndCreatedTimeAfter(personalCode.value(), aYearAgo())
        .stream()
        .filter(check -> REVERSE_TYPE_MAPPING.containsKey(check.getType()))
        .sorted(Comparator.comparing(AmlCheck::getCreatedTime).reversed())
        .map(this::toKybCheck)
        .toList();
  }

  private KybCheck toKybCheck(AmlCheck amlCheck) {
    return new KybCheck(
        REVERSE_TYPE_MAPPING.get(amlCheck.getType()), amlCheck.isSuccess(), amlCheck.getMetadata());
  }
}
