package ee.tuleva.onboarding.common;

import lombok.*;
import org.apache.commons.net.*;
import org.apache.commons.net.ftp.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

@RequiredArgsConstructor
public class FtpClient {
    private final String server;
    private final String user;
    private final String password;

    private FTPClient ftp;

    public void open() throws IOException {
        ftp = new FTPClient();

        ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));

        ftp.connect(server, 21);
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }

        ftp.login(user, password);
    }

    public void close() throws IOException {
        ftp.disconnect();
    }

    public Collection<String> listFiles(String path) throws IOException {
        FTPFile[] files = ftp.listFiles(path);
        return Arrays.stream(files)
            .map(FTPFile::getName)
            .collect(Collectors.toList());
    }

    public void downloadFile(String source, String destination) throws IOException {
        FileOutputStream out = new FileOutputStream(destination);
        ftp.retrieveFile(source, out);
    }

    public InputStream downloadFileStream(String source) throws IOException {
        return ftp.retrieveFileStream(source);
    }
}