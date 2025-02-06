package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.PensionikeskusDataDownloader.CsvParserConfig;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FundAumRetriever implements ComparisonIndexRetriever {

  public static final String KEY = "AUM";

  private final PensionikeskusDataDownloader downloader;
  private final String baseUrl;
  private final String isin;

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var isinColumn = 3;
    var aumColumn = 4;
    var config =
        CsvParserConfig.builder()
            .keyPrefix(KEY)
            .filterColumn(isinColumn)
            .filterValue(isin)
            .keyColumn(isinColumn)
            .valueColumn(aumColumn)
            .build();
    return downloader.downloadData(baseUrl, startDate, endDate, config);
  }

  @Override
  public String getKey() {
    return KEY + "_" + isin;
  }
}
