package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckPerformedEvent;
import ee.tuleva.onboarding.kyb.PersonalCode;
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
    var event = new KybCheckPerformedEvent(this, new PersonalCode("38501010001"), checks);

    listener.onKybCheckPerformed(event);

    verify(amlService)
        .addCheckIfMissing(
            argThat(
                check ->
                    check.getType() == KYB_COMPANY_ACTIVE
                        && check.isSuccess()
                        && check.getPersonalCode().equals("38501010001")));
    verify(amlService)
        .addCheckIfMissing(
            argThat(
                check ->
                    check.getType() == KYB_SOLE_MEMBER_OWNERSHIP
                        && !check.isSuccess()
                        && check.getPersonalCode().equals("38501010001")));
  }
}
