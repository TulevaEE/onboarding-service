package ee.tuleva.onboarding.ftp;

import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class FtpClientFactoryTest {

  @Test
  void createConfiguresTheUnderlyingFtpClientWithTheConfiguredTimeouts() {
    FtpClientFactory factory = new FtpClientFactory("server", "user", "password", 21);

    try (MockedConstruction<FTPClient> mockedFtpClient = mockConstruction(FTPClient.class)) {
      factory.create();

      FTPClient createdFtp = mockedFtpClient.constructed().get(0);
      verify(createdFtp).setDefaultTimeout(60_000);
      verify(createdFtp).setDataTimeout(Duration.ofMillis(60_000));
      verify(createdFtp).setConnectTimeout(60_000);
    }
  }
}
