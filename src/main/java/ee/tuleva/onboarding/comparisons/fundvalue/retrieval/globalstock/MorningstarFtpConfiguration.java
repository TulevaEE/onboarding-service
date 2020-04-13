package ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;

@Configuration
@Slf4j
public class MorningstarFtpConfiguration {
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
        return new FtpClient(ftpHost, ftpUsername, ftpPassword, ftpPort);
    }
}
