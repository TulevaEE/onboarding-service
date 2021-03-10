package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
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
  private final FundValueRepository fundValueRepository;
  private final EpisService episService;

  public List<ComparisonIndexRetriever> createAll() {
    List<String> allKeys = fundValueRepository.findAllKeys();
    return stream(fundRepository.findAll().spliterator(), false)
        .map(Fund::getIsin)
        .filter(allKeys::contains)
        .peek(isin -> log.info("Creating Fund NAV retriever for {}", isin))
        .map(isin -> new FundNavRetriever(episService, isin))
        .collect(toList());
  }
}
