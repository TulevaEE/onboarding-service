package ee.tuleva.onboarding.ftp;


import ee.tuleva.onboarding.log.LogInfoOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class FtpClient {
    private final String server;
    private final String user;
    private final String password;
    private final int port;

    private FTPClient ftp;

    public void open() {
        try {
            ftp = new FTPClient();
            ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(new LogInfoOutputStream())));
            ftp.connect(server, port);
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
            }

            ftp.login(user, password);
        } catch (IOException e) {
            throw new UncheckedIOException(e);

        }
    }

    public void close() throws IOException {
        ftp.disconnect();
    }

    public List<String> listFiles(String path) throws IOException {
        FTPFile[] files = ftp.listFiles(path);
        return Arrays.stream(files)
            .map(FTPFile::getName)
            .collect(Collectors.toList());
    }

    public InputStream downloadFileStream(String source) throws IOException {
        return ftp.retrieveFileStream(source);
    }

    public boolean completePendingCommand() throws IOException {
        return ftp.completePendingCommand();
    }
}