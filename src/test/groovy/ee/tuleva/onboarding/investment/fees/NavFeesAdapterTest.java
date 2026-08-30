package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeBases;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeResult;
import ee.tuleva.onboarding.savings.fund.nav.NavFeeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavFeesAdapterTest {

  @Mock private FeeCalculationService feeCalculationService;
  @Mock private FeeChargedToFundPolicy feeChargedToFundPolicy;

  private NavFeesAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new NavFeesAdapter(feeCalculationService, feeChargedToFundPolicy);
  }

  @Test
  void calculateFeesForNav_delegatesAndMapsFieldByField() {
    LocalDate positionReportDate = LocalDate.of(2025, 1, 15);
    Instant feeCutoff = Instant.parse("2025-01-16T00:00:00Z");
    NavFeeBases bases = new NavFeeBases(new BigDecimal("100000.00"), new BigDecimal("120000.00"));
    Map<String, ResolvedPrice> securityPrices = Map.of();
    FeeResult delegateResult = new FeeResult(new BigDecimal("52.08"), new BigDecimal("6.85"));

    given(
            feeCalculationService.calculateFeesForNav(
                TKF100,
                positionReportDate,
                new FeeBases(new BigDecimal("100000.00"), new BigDecimal("120000.00")),
                feeCutoff,
                securityPrices))
        .willReturn(delegateResult);

    NavFeeResult result =
        adapter.calculateFeesForNav(TKF100, positionReportDate, bases, feeCutoff, securityPrices);

    assertThat(result).isEqualTo(new NavFeeResult(new BigDecimal("52.08"), new BigDecimal("6.85")));
  }

  @Test
  void chargedToFund_delegatesWithMappedFeeType() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    given(feeChargedToFundPolicy.chargedToFund(TKF100, FeeType.DEPOT, date)).willReturn(false);

    boolean result = adapter.chargedToFund(TKF100, NavFeeType.DEPOT, date);

    assertThat(result).isFalse();
    verify(feeChargedToFundPolicy).chargedToFund(TKF100, FeeType.DEPOT, date);
  }

  @Test
  void chargedToFund_mapsManagementFeeType() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    given(feeChargedToFundPolicy.chargedToFund(TKF100, FeeType.MANAGEMENT, date)).willReturn(true);

    boolean result = adapter.chargedToFund(TKF100, NavFeeType.MANAGEMENT, date);

    assertThat(result).isTrue();
  }
}
