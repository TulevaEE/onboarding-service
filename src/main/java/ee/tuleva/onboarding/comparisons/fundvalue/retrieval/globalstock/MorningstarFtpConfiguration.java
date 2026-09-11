package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock;

import ee.tuleva.onboarding.ftp.FtpClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MorningstarFtpConfiguration {

  @Value("${morningstar.username}")
  private String ftpUsername;

  @Value("${morningstar.password}")
  private String ftpPassword;

  @Value("${morningstar.host}")
  private String ftpHost;

  @Value("${morningstar.port}")
  private int ftpPort;

  @Bean
  public FtpClientFactory morningstarFtpClientFactory() {
    return new FtpClientFactory(ftpHost, ftpUsername, ftpPassword, ftpPort);
  }
}
