package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.config.CacheConfiguration;
import ee.tuleva.onboarding.fund.FundInvestorsInRegister;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {FundBalanceUnitCounts.class, CacheConfiguration.class})
class FundBalanceUnitCountsCachingTest {

  @MockitoBean private FundBalanceRepository fundBalanceRepository;
  @Autowired private FundInvestorsInRegister fundInvestorsInRegister;

  @Test
  void readsTheRegisterOnceForEveryFundListServedFromTheCache() {
    given(fundBalanceRepository.findAllOnLatestRequestDate())
        .willReturn(
            List.of(FundBalance.builder().isin("EE3600001707").countInvestors(74292).build()));

    fundInvestorsInRegister.latestInvestorCounts();

    assertThat(fundInvestorsInRegister.latestInvestorCounts())
        .isEqualTo(Map.of("EE3600001707", 74292));
    verify(fundBalanceRepository, times(1)).findAllOnLatestRequestDate();
  }
}
