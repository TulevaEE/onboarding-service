package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckPerformedEventOrder.ATTRIBUTE_AML_CHECKS;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.AmlService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AmlKybCheckEventListener {

  private static final Map<KybCheckType, AmlCheckType> TYPE_MAPPING =
      Map.ofEntries(
          entry(KybCheckType.COMPANY_STRUCTURE, AmlCheckType.KYB_COMPANY_STRUCTURE),
          entry(KybCheckType.SOLE_MEMBER_OWNERSHIP, AmlCheckType.KYB_SOLE_MEMBER_OWNERSHIP),
          entry(KybCheckType.DUAL_MEMBER_OWNERSHIP, AmlCheckType.KYB_DUAL_MEMBER_OWNERSHIP),
          entry(
              KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP,
              AmlCheckType.KYB_SINGLE_BOARD_MEMBER_OWNERSHIP),
          entry(KybCheckType.COMPANY_ACTIVE, AmlCheckType.KYB_COMPANY_ACTIVE),
          entry(KybCheckType.COMPANY_AGE, AmlCheckType.KYB_COMPANY_AGE),
          entry(KybCheckType.RELATED_PERSONS_KYC, AmlCheckType.KYB_RELATED_PERSONS_KYC),
          entry(KybCheckType.COMPANY_SANCTION, AmlCheckType.KYB_COMPANY_SANCTION),
          entry(KybCheckType.COMPANY_PEP, AmlCheckType.KYB_COMPANY_PEP),
          entry(KybCheckType.HIGH_RISK_NACE, AmlCheckType.KYB_HIGH_RISK_NACE),
          entry(KybCheckType.COMPANY_LEGAL_FORM, AmlCheckType.KYB_COMPANY_LEGAL_FORM),
          entry(
              KybCheckType.COMPANY_REGISTERED_IN_ESTONIA,
              AmlCheckType.KYB_COMPANY_REGISTERED_IN_ESTONIA),
          entry(KybCheckType.SELF_CERTIFICATION, AmlCheckType.KYB_SELF_CERTIFICATION),
          entry(KybCheckType.DATA_CHANGED, AmlCheckType.KYB_DATA_CHANGED));

  private final AmlService amlService;
  private final CompanyIdResolver companyIdResolver;

  @Order(ATTRIBUTE_AML_CHECKS)
  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    UUID companyId = companyIdResolver.resolveId(event.getCompany().registryCode());
    event
        .getChecks()
        .forEach(
            check -> amlService.addCheck(toAmlCheck(event.getPersonalCode(), companyId, check)));
  }

  private AmlCheck toAmlCheck(
      PersonalCode personalCode, @Nullable UUID companyId, KybCheck kybCheck) {
    return AmlCheck.builder()
        .personalCode(personalCode.value())
        .companyId(companyId)
        .type(requireNonNull(TYPE_MAPPING.get(kybCheck.type()), "Unmapped KYB check type"))
        .success(kybCheck.success())
        .metadata(kybCheck.metadata())
        .build();
  }
}
