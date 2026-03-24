package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.KybCheckType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AmlKybCheckEventListener {

  private static final Map<KybCheckType, AmlCheckType> TYPE_MAPPING =
      Map.of(
          KybCheckType.SOLE_MEMBER_OWNERSHIP, AmlCheckType.KYB_SOLE_MEMBER_OWNERSHIP,
          KybCheckType.DUAL_MEMBER_OWNERSHIP, AmlCheckType.KYB_DUAL_MEMBER_OWNERSHIP,
          KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER, AmlCheckType.KYB_SOLE_BOARD_MEMBER_IS_OWNER,
          KybCheckType.COMPANY_ACTIVE, AmlCheckType.KYB_COMPANY_ACTIVE,
          KybCheckType.RELATED_PERSONS_KYC, AmlCheckType.KYB_RELATED_PERSONS_KYC,
          KybCheckType.COMPANY_SANCTION, AmlCheckType.KYB_COMPANY_SANCTION,
          KybCheckType.COMPANY_PEP, AmlCheckType.KYB_COMPANY_PEP,
          KybCheckType.HIGH_RISK_NACE, AmlCheckType.KYB_HIGH_RISK_NACE,
          KybCheckType.SELF_CERTIFICATION, AmlCheckType.KYB_SELF_CERTIFICATION);

  private final AmlService amlService;

  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    event
        .getChecks()
        .forEach(check -> amlService.addCheckIfMissing(toAmlCheck(event.getPersonalCode(), check)));
  }

  private AmlCheck toAmlCheck(String personalCode, KybCheck kybCheck) {
    return AmlCheck.builder()
        .personalCode(personalCode)
        .type(TYPE_MAPPING.get(kybCheck.type()))
        .success(kybCheck.success())
        .metadata(kybCheck.metadata())
        .build();
  }
}
