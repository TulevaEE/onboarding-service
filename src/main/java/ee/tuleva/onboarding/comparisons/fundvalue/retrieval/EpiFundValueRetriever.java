package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.PensionikeskusDataDownloader.CsvParserConfig;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpiFundValueRetriever implements ComparisonIndexRetriever {

  public static final String KEY = "EPI";
  private final PensionikeskusDataDownloader downloader;

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var baseUrl = "https://www.pensionikeskus.ee/en/statistics/ii-pillar/epi-charts/";
    var config =
        CsvParserConfig.builder()
            .key(KEY)
            .filterColumn(1)
            .filterValue("EPI")
            .valueColumn(2)
            .build();
    return downloader.downloadData(baseUrl, startDate, endDate, config);
  }
}
