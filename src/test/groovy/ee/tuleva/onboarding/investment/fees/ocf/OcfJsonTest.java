package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.NO_PUBLISHED_NAV_CALCULATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OcfJsonTest {

  private final OcfJson ocfJson = new OcfJson(JsonMapper.builder().build());

  @Test
  void theChecksNameTheGapsBeforeTheExclusionsSoTheFingerprintIsTheSameInEveryRun() {
    assertThat(ocfJson.checks(List.of(NO_PUBLISHED_NAV_CALCULATION)))
        .isEqualTo(
            "{\"gaps\":[\"NO_PUBLISHED_NAV_CALCULATION\"],\"excluded\":[\"ENTRY_CHARGES\","
                + "\"EXIT_CHARGES\",\"PERFORMANCE_FEES\",\"BORROWING_INTEREST\","
                + "\"DERIVATIVE_HOLDING_COSTS\",\"SOFT_COMMISSIONS\"]}");
  }

  @Test
  void aCompleteCalculationHasAnEmptyGapListFirst() {
    assertThat(ocfJson.checks(List.of()))
        .startsWith("{\"gaps\":[],\"excluded\":[\"ENTRY_CHARGES\",");
  }
}
