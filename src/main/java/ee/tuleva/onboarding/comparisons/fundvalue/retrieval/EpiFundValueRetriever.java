package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.PensionikeskusDataDownloader.CsvParserConfig;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EpiFundValueRetriever implements ComparisonIndexRetriever {

  private final PensionikeskusDataDownloader downloader;
  private final EpiIndex index;

  @Override
  public String getKey() {
    return index.getKey();
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var baseUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/";
    var indexColumn = 1;
    var valueColumn = 2;
    var config =
        CsvParserConfig.builder()
            .keyPrefix(index.getKey())
            .filterColumn(indexColumn)
            .filterValue(index.getValue())
            .valueColumn(valueColumn)
            .build();
    return downloader.downloadData(baseUrl, startDate, endDate, config);
  }
}
