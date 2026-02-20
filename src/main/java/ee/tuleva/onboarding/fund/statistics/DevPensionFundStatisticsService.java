package ee.tuleva.onboarding.fund.statistics;

import static java.util.Collections.emptyList;

import java.util.List;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "staging"})
public class DevPensionFundStatisticsService extends PensionFundStatisticsService {

  public DevPensionFundStatisticsService(RestTemplateBuilder restTemplateBuilder) {
    super(restTemplateBuilder);
  }

  @Override
  public List<PensionFundStatistics> getCachedStatistics() {
    return emptyList();
  }

  @Override
  public List<PensionFundStatistics> refreshCachedStatistics() {
    return emptyList();
  }
}
