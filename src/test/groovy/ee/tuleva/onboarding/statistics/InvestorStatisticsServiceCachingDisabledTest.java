package ee.tuleva.onboarding.statistics;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.config.CacheConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {InvestorStatisticsService.class, CacheConfiguration.class})
@TestPropertySource(properties = "spring.cache.type=NONE")
class InvestorStatisticsServiceCachingDisabledTest {

  @MockitoBean private InvestorStatisticsRepository investorStatisticsRepository;
  @Autowired private InvestorStatisticsService investorStatisticsService;

  @Test
  void hitsRepositoryOnEveryCallWhenCachingIsDisabled() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(85224L);

    investorStatisticsService.getActiveInvestorCount();
    investorStatisticsService.getActiveInvestorCount();

    verify(investorStatisticsRepository, times(2)).getActiveInvestorCount();
  }
}
