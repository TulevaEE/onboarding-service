package ee.tuleva.onboarding.config;

import ee.tuleva.onboarding.ftp.FTPClientFactory;
import ee.tuleva.onboarding.ftp.FtpClient;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;

@Configuration
@Slf4j
public class MorningstarFTPConfiguration {
    @Value("${morningstar.username}")
    private String ftpUsername;
    @Value("${morningstar.password}")
    private String ftpPassword;
    @Value("${morningstar.host}")
    private String ftpHost;
    @Value("${morningstar.port}")
    private int ftpPort;

    @Bean
    public FTPClientFactory morningstarFTPClientFactory() {
        return new FTPClientFactory(ftpHost, ftpUsername, ftpPassword, ftpPort);
    }
}
