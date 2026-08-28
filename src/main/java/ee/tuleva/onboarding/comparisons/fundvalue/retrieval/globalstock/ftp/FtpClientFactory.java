package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp;

import lombok.RequiredArgsConstructor;
import org.apache.commons.net.ftp.FTPClient;

@RequiredArgsConstructor
public class FtpClientFactory {
  private static final int TIMEOUT_MILLISECONDS = 60_000;

  private final String server;
  private final String user;
  private final String password;
  private final int port;

  public FtpClient create() {
    FTPClient ftpClient = new FTPClient();
    ftpClient.setDefaultTimeout(TIMEOUT_MILLISECONDS);
    ftpClient.setDataTimeout(TIMEOUT_MILLISECONDS);
    ftpClient.setConnectTimeout(TIMEOUT_MILLISECONDS);
    return new FtpClient(ftpClient, server, user, password, port);
  }
}
