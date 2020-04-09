package ee.tuleva.onboarding.ftp;


import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FTPClientFactory {
    private final String server;
    private final String user;
    private final String password;
    private final int port;

    public FtpClient createClient() {
        return new FtpClient(server, user, password, port);
    }
}
