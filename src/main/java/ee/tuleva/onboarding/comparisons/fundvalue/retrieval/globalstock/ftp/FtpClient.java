package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp;

import lombok.RequiredArgsConstructor;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class FtpClient {
    private final FTPClient ftp;
    private final String server;
    private final String user;
    private final String password;
    private final int port;


    public void open() throws IOException {
        ftp.connect(server, port);
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("Error connecting to FTP " + server + ":" + port + ". Reply code: " + reply);
        }
        ftp.enterLocalPassiveMode();
        ftp.login(user, password);
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
    }

    public void close() throws IOException {
        ftp.disconnect();
    }

    public List<String> listFiles(String path) throws IOException {
        FTPFile[] files = ftp.listFiles(path);
        return Arrays.stream(files)
            .map(FTPFile::getName)
            .collect(toList());
    }

    public InputStream downloadFileStream(String source) throws IOException {
        return ftp.retrieveFileStream(source);
    }

    public boolean completePendingCommand() throws IOException {
        return ftp.completePendingCommand();
    }
}