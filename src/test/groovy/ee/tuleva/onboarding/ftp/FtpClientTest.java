package ee.tuleva.onboarding.ftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

  @Test
  void openConnectsEntersPassiveModeThenLogsInAndSetsBinaryType() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    given(ftp.getReplyCode()).willReturn(230);

    client.open();

    InOrder inOrder = inOrder(ftp);
    inOrder.verify(ftp).connect("server", 21);
    inOrder.verify(ftp).enterLocalPassiveMode();
    inOrder.verify(ftp).login("user", "password");
    inOrder.verify(ftp).setFileType(FTP.BINARY_FILE_TYPE);
  }

  @Test
  void openDisconnectsAndFailsFastWhenTheServerRejectsTheConnection() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    given(ftp.getReplyCode()).willReturn(500);

    assertThatThrownBy(() -> client.open()).isInstanceOf(IOException.class);

    verify(ftp).disconnect();
    verify(ftp, never()).login(any(), any());
  }

  @Test
  void closeDisconnectsTheUnderlyingFtpClient() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);

    client.close();

    verify(ftp).disconnect();
  }

  @Test
  void listFilesReturnsTheNamesOfTheFilesFound() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    FTPFile first = new FTPFile();
    first.setName("a.csv");
    FTPFile second = new FTPFile();
    second.setName("b.csv");
    given(ftp.listFiles("/some/path")).willReturn(new FTPFile[] {first, second});

    assertThat(client.listFiles("/some/path")).containsExactly("a.csv", "b.csv");
  }

  @Test
  void completePendingCommandReturnsTrueWhenTheUnderlyingClientConfirms() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    given(ftp.completePendingCommand()).willReturn(true);

    assertThat(client.completePendingCommand()).isTrue();
  }

  @Test
  void completePendingCommandReturnsFalseWhenTheUnderlyingClientDoesNotConfirm() throws Exception {
    var client = new FtpClient(ftp, "server", "user", "password", 21);
    given(ftp.completePendingCommand()).willReturn(false);

    assertThat(client.completePendingCommand()).isFalse();
  }
}
