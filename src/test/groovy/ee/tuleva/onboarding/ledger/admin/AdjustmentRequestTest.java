package ee.tuleva.onboarding.ledger.admin;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.party.PartyId;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AdjustmentRequestTest {

  @Test
  void debitParty_isNullWhenDebitPartyCodeIsNull() {
    var request = sampleRequest(null, null, "CREDIT", "38812121215", "PERSON");

    assertThat(request.debitParty()).isNull();
  }

  @Test
  void debitParty_resolvesPartyIdWhenCodePresent() {
    var request = sampleRequest("38812121215", "PERSON", "CREDIT", null, null);

    assertThat(request.debitParty()).isEqualTo(new PartyId(PartyId.Type.PERSON, "38812121215"));
  }

  @Test
  void creditParty_isNullWhenCreditPartyCodeIsNull() {
    var request = sampleRequest("38812121215", "PERSON", "CREDIT", null, null);

    assertThat(request.creditParty()).isNull();
  }

  @Test
  void creditParty_resolvesPartyIdWhenCodePresent() {
    var request = sampleRequest(null, null, "CREDIT", "48709090311", "PERSON");

    assertThat(request.creditParty()).isEqualTo(new PartyId(PartyId.Type.PERSON, "48709090311"));
  }

  private static AdjustmentRequest sampleRequest(
      String debitPartyCode,
      String debitPartyType,
      String creditAccount,
      String creditPartyCode,
      String creditPartyType) {
    return new AdjustmentRequest(
        "DEBIT",
        debitPartyCode,
        debitPartyType,
        creditAccount,
        creditPartyCode,
        creditPartyType,
        new BigDecimal("10.00"),
        null,
        "test adjustment");
  }
}
