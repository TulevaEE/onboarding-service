package ee.tuleva.onboarding.ftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtpClientTest {

  @Mock private FTPClient ftp;

  @Test
  void downloadFileStreamReturnsTheStream() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    InputStream stream = new ByteArrayInputStream(new byte[] {1});
    given(ftp.retrieveFileStream("/some/file.gz")).willReturn(stream);

    assertThat(client.downloadFileStream("/some/file.gz")).isSameAs(stream);
  }

  @Test
  void downloadFileStreamFailsFastWhenTheServerReturnsNoStream() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    given(ftp.retrieveFileStream("/some/file.gz")).willReturn(null);
    given(ftp.getReplyString()).willReturn("550 File not found");

    assertThatThrownBy(() -> client.downloadFileStream("/some/file.gz"))
        .isInstanceOf(IllegalStateException.class);
  }
}
