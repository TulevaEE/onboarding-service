package ee.tuleva.onboarding.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestorStatisticsServiceTest {

  @Mock private InvestorStatisticsRepository investorStatisticsRepository;
  @InjectMocks private InvestorStatisticsService investorStatisticsService;

  @Test
  void getActiveInvestorCount_delegatesToRepository() {
    given(investorStatisticsRepository.getActiveInvestorCount()).willReturn(85224L);

    assertThat(investorStatisticsService.getActiveInvestorCount()).isEqualTo(85224L);
  }
}
