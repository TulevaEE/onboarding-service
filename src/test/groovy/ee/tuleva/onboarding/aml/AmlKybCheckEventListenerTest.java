package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmlKybCheckEventListenerTest {

  private final AmlService amlService = mock(AmlService.class);
  private final AmlKybCheckEventListener listener = new AmlKybCheckEventListener(amlService);

  @Test
  void savesAmlChecksForEachKybCheck() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of("personalCode", "38501010001")));
    var company = new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ);
    var relatedPersons =
        List.of(
            new KybRelatedPerson(
                new PersonalCode("38501010001"),
                true,
                true,
                true,
                BigDecimal.valueOf(100),
                KybKycStatus.COMPLETED));
    var event =
        new KybCheckPerformedEvent(
            this, company, new PersonalCode("38501010001"), relatedPersons, checks);

    listener.onKybCheckPerformed(event);

    verify(amlService)
        .addCheck(
            argThat(
                check ->
                    check.getType() == KYB_COMPANY_ACTIVE
                        && check.isSuccess()
                        && check.getPersonalCode().equals("38501010001")));
    verify(amlService)
        .addCheck(
            argThat(
                check ->
                    check.getType() == KYB_SOLE_MEMBER_OWNERSHIP
                        && !check.isSuccess()
                        && check.getPersonalCode().equals("38501010001")));
  }
}
