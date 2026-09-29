package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class FundSettlementTermsSeedTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Autowired private InstrumentReferenceRepository repository;

  @Test
  void theEquityIndexFundsWithAQuarterPastOneCutoffSettleFourBusinessDaysAfterDealing() {
    assertThat(termsOf("IE00BFG1TM61"))
        .isEqualTo(new SettlementTerms(LocalTime.of(13, 15), TALLINN, 4));
    assertThat(termsOf("IE00BKPTWY98"))
        .isEqualTo(new SettlementTerms(LocalTime.of(13, 15), TALLINN, 4));
  }

  @Test
  void theLuxembourgBondFundsWithAQuarterPastElevenCutoffSettleThreeBusinessDaysAfterDealing() {
    assertThat(termsOf("LU0826455353"))
        .isEqualTo(new SettlementTerms(LocalTime.of(11, 15), TALLINN, 3));
    assertThat(termsOf("LU0839970364"))
        .isEqualTo(new SettlementTerms(LocalTime.of(11, 15), TALLINN, 3));
  }

  @Test
  void theIrishBondFundsWithAHalfPastNineCutoffSettleThreeBusinessDaysAfterDealing() {
    assertThat(termsOf("IE0005032192"))
        .isEqualTo(new SettlementTerms(LocalTime.of(9, 30), TALLINN, 3));
    assertThat(termsOf("IE0031080751"))
        .isEqualTo(new SettlementTerms(LocalTime.of(9, 30), TALLINN, 3));
  }

  @Test
  void aFundBeingRetiredKeepsTheFlatRule() {
    assertThat(repository.findByIsin("IE0009FT4LX4").flatMap(InstrumentReference::settlementTerms))
        .isEmpty();
  }

  private SettlementTerms termsOf(String isin) {
    return repository.findByIsin(isin).flatMap(InstrumentReference::settlementTerms).orElseThrow();
  }
}
