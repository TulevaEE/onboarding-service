package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class KidScenarioHistoryCsvTest {

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  @Test
  void writesOneRowPerMonthHorizonAndScenarioAsTheKidPrintsThemInTheNavCsvsConventions() {
    var csv =
        new KidScenarioHistoryCsv(
            List.of(
                published(YearMonth.of(2026, 10), 5, STRESS, "5290", "-0.1196"),
                published(YearMonth.of(2026, 10), 5, UNFAVOURABLE, "8620", "-0.0293"),
                published(YearMonth.of(2026, 10), 5, MODERATE, "11480", "0.0281"),
                published(YearMonth.of(2026, 10), 5, FAVOURABLE, "19250", "0.1399"),
                published(YearMonth.of(2026, 9), 1, STRESS, "6620", "-0.3383")));

    assertThat(csv.toBytes())
        .isEqualTo(
            withBom(
                "Seisuga;Hoidmisperiood (aastat);Stsenaarium;Summa (EUR);"
                    + "Keskmine aastane tootlus (%)\r\n"
                    + "31.10.2026;5;Stress;5290;-11.96\r\n"
                    + "31.10.2026;5;Ebasoodne;8620;-2.93\r\n"
                    + "31.10.2026;5;Mõõdukas;11480;2.81\r\n"
                    + "31.10.2026;5;Soodne;19250;13.99\r\n"
                    + "30.09.2026;1;Stress;6620;-33.83\r\n"));
  }

  @Test
  void anEmptyHistoryIsTheHeaderAlone() {
    assertThat(new KidScenarioHistoryCsv(List.of()).toBytes())
        .isEqualTo(
            withBom(
                "Seisuga;Hoidmisperiood (aastat);Stsenaarium;Summa (EUR);"
                    + "Keskmine aastane tootlus (%)\r\n"));
  }

  private static PublishedScenario published(
      YearMonth month, int horizonYears, ScenarioKind kind, String amount, String averageReturn) {
    return new PublishedScenario(
        month, horizonYears, kind, new BigDecimal(amount), new BigDecimal(averageReturn));
  }

  private static byte[] withBom(String text) {
    var body = text.getBytes(UTF_8);
    var bytes = new byte[UTF8_BOM.length + body.length];
    System.arraycopy(UTF8_BOM, 0, bytes, 0, UTF8_BOM.length);
    System.arraycopy(body, 0, bytes, UTF8_BOM.length, body.length);
    return bytes;
  }
}
