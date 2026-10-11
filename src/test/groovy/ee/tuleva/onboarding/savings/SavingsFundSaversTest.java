package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.nudge.NudgeAccount;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFundSaversTest {

  @Mock private SavingFundPaymentRepository paymentRepository;
  @InjectMocks private SavingsFundSavers savers;

  @Test
  void aPersonSavesWhenTheirOwnAccountHasAnIssuedPayment() {
    given(paymentRepository.existsIssuedPaymentFor(new PartyId(PartyId.Type.PERSON, "38888888888")))
        .willReturn(true);

    assertThat(savers.savesFor(NudgeAccount.person("38888888888"))).isTrue();
  }

  @Test
  void aCompanyAccountIsLookedUpAsALegalEntity() {
    given(
            paymentRepository.existsIssuedPaymentFor(
                new PartyId(PartyId.Type.LEGAL_ENTITY, "12345678")))
        .willReturn(false);

    assertThat(savers.savesFor(new NudgeAccount(LEGAL_ENTITY, "12345678"))).isFalse();
  }
}
