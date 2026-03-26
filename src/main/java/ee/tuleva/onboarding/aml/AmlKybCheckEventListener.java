package ee.tuleva.onboarding.aml;

import static java.util.Map.entry;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.PersonalCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AmlKybCheckEventListener {

  private static final Map<KybCheckType, AmlCheckType> TYPE_MAPPING =
      Map.ofEntries(
          entry(KybCheckType.SOLE_MEMBER_OWNERSHIP, AmlCheckType.KYB_SOLE_MEMBER_OWNERSHIP),
          entry(KybCheckType.DUAL_MEMBER_OWNERSHIP, AmlCheckType.KYB_DUAL_MEMBER_OWNERSHIP),
          entry(
              KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER, AmlCheckType.KYB_SOLE_BOARD_MEMBER_IS_OWNER),
          entry(KybCheckType.COMPANY_ACTIVE, AmlCheckType.KYB_COMPANY_ACTIVE),
          entry(KybCheckType.RELATED_PERSONS_KYC, AmlCheckType.KYB_RELATED_PERSONS_KYC),
          entry(KybCheckType.COMPANY_SANCTION, AmlCheckType.KYB_COMPANY_SANCTION),
          entry(KybCheckType.COMPANY_PEP, AmlCheckType.KYB_COMPANY_PEP),
          entry(KybCheckType.HIGH_RISK_NACE, AmlCheckType.KYB_HIGH_RISK_NACE),
          entry(KybCheckType.COMPANY_LEGAL_FORM, AmlCheckType.KYB_COMPANY_LEGAL_FORM),
          entry(KybCheckType.SELF_CERTIFICATION, AmlCheckType.KYB_SELF_CERTIFICATION),
          entry(KybCheckType.DATA_CHANGED, AmlCheckType.KYB_DATA_CHANGED));

  private final AmlService amlService;

  @EventListener
  @Transactional
  public void onKybCheckPerformed(KybCheckPerformedEvent event) {
    event
        .getChecks()
        .forEach(check -> amlService.addCheck(toAmlCheck(event.getPersonalCode(), check)));
  }

  private AmlCheck toAmlCheck(PersonalCode personalCode, KybCheck kybCheck) {
    return AmlCheck.builder()
        .personalCode(personalCode.value())
        .type(TYPE_MAPPING.get(kybCheck.type()))
        .success(kybCheck.success())
        .metadata(kybCheck.metadata())
        .build();
  }
}
