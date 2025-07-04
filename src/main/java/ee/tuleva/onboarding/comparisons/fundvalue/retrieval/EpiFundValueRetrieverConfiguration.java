package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex.EPI;
import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.EpiIndex.EPI_3;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
class EpiFundValueRetrieverConfiguration {

  private final PensionikeskusDataDownloader downloader;

  @Bean
  ComparisonIndexRetriever secondPillarEpiFundValueRetriever() {
    return new EpiFundValueRetriever(downloader, EPI);
  }

  @Bean
  ComparisonIndexRetriever thirdPillarEpiFundValueRetriever() {
    return new EpiFundValueRetriever(downloader, EPI_3);
  }
}
