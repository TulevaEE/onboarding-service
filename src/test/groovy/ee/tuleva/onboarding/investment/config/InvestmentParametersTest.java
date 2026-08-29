package ee.tuleva.onboarding.investment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.InvestmentParameters;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentParametersTest {

  @Mock private InvestmentParameterRepository repository;
  @InjectMocks private InvestmentParameterValues parameters;

  @Test
  void resolvesTheNavImpactVolumeThreshold() {
    var asOf = LocalDate.parse("2026-08-30");
    given(repository.findLatestValue(InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD, asOf))
        .willReturn(new BigDecimal("100000"));

    InvestmentParameters investmentParameters = parameters;

    assertThat(investmentParameters.navImpactVolumeThreshold(asOf))
        .isEqualByComparingTo(new BigDecimal("100000"));
  }
}
