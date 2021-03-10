package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MorningstarFtpConfiguration {
  private static final int TIMEOUT_MILLISECONDS = 60_000;

  @Value("${morningstar.username}")
  private String ftpUsername;

  @Value("${morningstar.password}")
  private String ftpPassword;

  @Value("${morningstar.host}")
  private String ftpHost;

  @Value("${morningstar.port}")
  private int ftpPort;

  @Bean
  public FtpClient morningstarFtpClient() {
    return new FtpClient(ftpClient(), ftpHost, ftpUsername, ftpPassword, ftpPort);
  }

  private FTPClient ftpClient() {
    FTPClient ftpClient = new FTPClient();
    ftpClient.setDefaultTimeout(TIMEOUT_MILLISECONDS);
    ftpClient.setDataTimeout(TIMEOUT_MILLISECONDS);
    ftpClient.setConnectTimeout(TIMEOUT_MILLISECONDS);
    return ftpClient;
  }
}
