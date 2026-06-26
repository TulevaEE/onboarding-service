package ee.tuleva.onboarding.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvestorStatisticsService {

  private final InvestorStatisticsRepository investorStatisticsRepository;

  public long getActiveInvestorCount() {
    return investorStatisticsRepository.getActiveInvestorCount();
  }
}
