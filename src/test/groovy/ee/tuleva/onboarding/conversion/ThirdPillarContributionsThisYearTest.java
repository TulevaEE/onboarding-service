package ee.tuleva.onboarding.conversion;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThirdPillarContributionsThisYearTest {

  private static final Clock ESTONIAN_CLOCK =
      Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @Mock private ConversionCashFlows conversionCashFlows;

  @Test
  void sumsThisYearsThirdPillarCashContributionsLikeTheStatusBox() {
    given(conversionCashFlows.forPerson(samplePerson))
        .willReturn(
            List.of(
                thirdPillarCash("1000.00", "2026-02-01T10:00:00Z"),
                thirdPillarCash("2260.00", "2026-10-15T10:00:00Z"),
                thirdPillarCash("500.00", "2025-12-31T21:59:59Z"),
                secondPillarCash("300.00", "2026-03-01T10:00:00Z"),
                thirdPillarTransferIn("4000.00", "2026-04-01T10:00:00Z")));

    var contributions = new ThirdPillarContributionsThisYear(conversionCashFlows, ESTONIAN_CLOCK);

    assertThat(contributions.forPerson(samplePerson)).isEqualByComparingTo("3260.00");
  }

  @Test
  void countsAPaymentMadeJustAfterMidnightOnNewYearInEstonia() {
    given(conversionCashFlows.forPerson(samplePerson))
        .willReturn(List.of(thirdPillarCash("100.00", "2025-12-31T22:00:01Z")));

    var contributions = new ThirdPillarContributionsThisYear(conversionCashFlows, ESTONIAN_CLOCK);

    assertThat(contributions.forPerson(samplePerson)).isEqualByComparingTo("100.00");
  }

  private static ConversionCashFlow thirdPillarCash(String amount, String time) {
    return new ConversionCashFlow(
        3, new BigDecimal(amount), Instant.parse(time), true, true, false);
  }

  private static ConversionCashFlow secondPillarCash(String amount, String time) {
    return new ConversionCashFlow(
        2, new BigDecimal(amount), Instant.parse(time), true, true, false);
  }

  private static ConversionCashFlow thirdPillarTransferIn(String amount, String time) {
    return new ConversionCashFlow(
        3, new BigDecimal(amount), Instant.parse(time), false, true, false);
  }
}
