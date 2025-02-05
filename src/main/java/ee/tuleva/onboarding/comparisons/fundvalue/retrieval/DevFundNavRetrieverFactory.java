package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.util.Collections.emptyList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.fund.FundRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "staging"})
public class DevFundNavRetrieverFactory extends FundNavRetrieverFactory {

  public DevFundNavRetrieverFactory(FundRepository fundRepository, EpisService episService) {
    super(fundRepository, episService);
  }

  @Override
  public List<ComparisonIndexRetriever> createAll() {
    return emptyList();
  }
}
