package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundNavRetrieverFactory {

  private final FundRepository fundRepository;
  private final EpisService episService;

  public List<ComparisonIndexRetriever> createAll() {
    return fundRepository.findAllByStatus(ACTIVE).stream()
        .map(Fund::getIsin)
        .peek(isin -> log.info("Creating Fund NAV retriever for {}", isin))
        .map(isin -> new FundNavRetriever(episService, isin))
        .collect(toList());
  }
}
