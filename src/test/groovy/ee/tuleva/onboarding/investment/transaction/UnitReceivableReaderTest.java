package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.RegistrarCashFlowRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitReceivableReaderTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate LAST_DAY_OF_2026 = LocalDate.of(2026, 12, 31);
  private static final LocalDate FIRST_DAY_OF_2027 = LocalDate.of(2027, 1, 1);

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private RegistrarCashFlowRepository registrarCashFlows;

  @Test
  void aSecondPillarFundMayNotCountTheReceivableBeforeItArrivesIn2026() {
    var reader = readerOn(LAST_DAY_OF_2026);

    assertThat(
            reader
                .read(TUK00, LAST_DAY_OF_2026.minusDays(1), LAST_DAY_OF_2026)
                .countedBeforeItArrives())
        .isFalse();
  }

  @Test
  void fromTheStartOf2027ASecondPillarFundMayCountTheReceivableBeforeItArrives() {
    var reader = readerOn(FIRST_DAY_OF_2027);

    assertThat(reader.read(TUK00, LAST_DAY_OF_2026, FIRST_DAY_OF_2027).countedBeforeItArrives())
        .isTrue();
  }

  @Test
  void aCalculationDatedInto2027ButMadeIn2026FollowsTheRuleOfTheDayItIsMade() {
    var reader = readerOn(LAST_DAY_OF_2026);

    assertThat(reader.read(TUK00, LAST_DAY_OF_2026, FIRST_DAY_OF_2027).countedBeforeItArrives())
        .isFalse();
  }

  @Test
  void theThirdPillarFundMayCountTheReceivableBeforeItArrivesAlready() {
    var reader = readerOn(LAST_DAY_OF_2026);

    assertThat(
            reader
                .read(TUV100, LAST_DAY_OF_2026.minusDays(1), LAST_DAY_OF_2026)
                .countedBeforeItArrives())
        .isTrue();
  }

  private UnitReceivableReader readerOn(LocalDate today) {
    return new UnitReceivableReader(
        fundPositionRepository,
        registrarCashFlows,
        Clock.fixed(today.atStartOfDay(TALLINN).toInstant(), TALLINN));
  }
}
