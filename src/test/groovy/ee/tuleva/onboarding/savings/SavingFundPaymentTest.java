package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPaymentFixture.aPayment;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.party.PartyId;
import org.junit.jupiter.api.Test;

class SavingFundPaymentTest {

  @Test
  void toStringOmitsPersonalAndFreeTextData() {
    var payment =
        aPayment()
            .status(RETURNED)
            .partyId(new PartyId(PERSON, "38888888888"))
            .description("Testi Testija payment 38888888888")
            .remitterIban("EE000000000000000000")
            .remitterName("Testi Testija")
            .remitterIdCode("38888888888")
            .beneficiaryIban("EE111111111111111111")
            .beneficiaryName("Testa Testija")
            .beneficiaryIdCode("49999999999")
            .returnReason("Returned on request of Testi Testija")
            .build();

    var string = payment.toString();

    assertThat(string)
        .contains(payment.getId().toString(), RETURNED.name())
        .doesNotContain(
            "Testi Testija",
            "Testa Testija",
            "38888888888",
            "49999999999",
            "EE000000000000000000",
            "EE111111111111111111",
            "Returned on request");
  }

  @Test
  void equalityStillCoversExcludedFields() {
    var builder = aPayment().remitterName("Testi Testija");
    var payment = builder.build();

    assertThat(builder.build()).isEqualTo(payment).hasSameHashCodeAs(payment);
    assertThat(builder.remitterName("Testa Testija").build()).isNotEqualTo(payment);
  }
}
