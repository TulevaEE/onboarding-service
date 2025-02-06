package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FundAumRetrieverConfiguration {

  private static final String secondPillarBaseUrl =
      "https://www.pensionikeskus.ee/en/statistics/ii-pillar/value-of-assets-of-funded-pension/";
  private static final String thirdPillarBaseUrl =
      "https://www.pensionikeskus.ee/en/statistics/iii-pillar/value-of-assets-of-suppl-funded-pension/";

  @Bean
  public FundAumRetriever secondPillarBondAumRetriever(PensionikeskusDataDownloader downloader) {
    String url = secondPillarBaseUrl + "?f[0]=76";
    String isin = "EE3600109443";
    return new FundAumRetriever(downloader, url, isin);
  }

  @Bean
  public FundAumRetriever secondPillarStockAumRetriever(PensionikeskusDataDownloader downloader) {
    String url = secondPillarBaseUrl + "?f[0]=77";
    String isin = "EE3600109435";
    return new FundAumRetriever(downloader, url, isin);
  }

  @Bean
  public FundAumRetriever thirdPillarAumRetriever(PensionikeskusDataDownloader downloader) {
    String url = thirdPillarBaseUrl + "?f[0]=81";
    String isin = "EE3600001707";
    return new FundAumRetriever(downloader, url, isin);
  }
}
