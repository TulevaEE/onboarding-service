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
public class FundAumRetriever implements ComparisonIndexRetriever {

  public static final String KEY = "AUM";
  private final PensionikeskusDataDownloader downloader;

  @Override
  public List<FundValue> retrieveValuesForRange(LocalDate startDate, LocalDate endDate) {
    var baseUrl =
        "https://www.pensionikeskus.ee/en/statistics/ii-pillar/value-of-assets-of-funded-pension/";
    var config =
        CsvParserConfig.builder()
            .key(KEY)
            .keyColumn(3) // ISIN
            .valueColumn(4) // AUM
            .build();
    return downloader.downloadData(baseUrl, startDate, endDate, config);
  }

  @Override
  public String getKey() {
    return KEY;
  }
}
