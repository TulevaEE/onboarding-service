package ee.tuleva.onboarding.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.config.CacheConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {InvestorStatisticsService.class, CacheConfiguration.class})
class InvestorStatisticsServiceCachingTest {

  @MockitoBean private InvestorStatisticsRepository investorStatisticsRepository;
  @Autowired private InvestorStatisticsService investorStatisticsService;

  @Test
  void servesRepeatedCallsFromCache() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(85224L);

    long first = investorStatisticsService.getActiveInvestorCount();
    long second = investorStatisticsService.getActiveInvestorCount();

    assertThat(first).isEqualTo(85224L);
    assertThat(second).isEqualTo(85224L);
    verify(investorStatisticsRepository, times(1)).getActiveInvestorCount();
  }
}
