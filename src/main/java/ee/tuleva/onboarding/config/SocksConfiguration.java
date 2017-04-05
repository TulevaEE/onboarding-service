package ee.tuleva.onboarding.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Configuration
@Slf4j
public class SocksConfiguration {

    @Value("${socksProxyUrl:#{null}}")
    private String socksProxyUrl;

    @PostConstruct
    private void initialize() {
        if (!isBlank(socksProxyUrl)) {
            try {
                log.info("Socks proxy url {}", socksProxyUrl);
                URL url = new URL(socksProxyUrl);
                String authString = url.getUserInfo();
                String username = authString.split(":")[0];
                String password = authString.split(":")[1];
                String host = url.getHost();
                int port = url.getPort();

                log.info("Socks proxy username {} password {} host {} port {}", username, password, host, port);

                System.setProperty("socksProxyHost", host);
                System.setProperty("socksProxyPort", Integer.toString(port));
                System.setProperty("java.net.socks.username", username);
                System.setProperty("java.net.socks.password", password);
                Authenticator.setDefault(new ProxyAuth(username, password));

            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.warn("socksProxyUrl conf property is not set, not configuring global Java Socks Proxy");
        }
    }


    public class ProxyAuth extends Authenticator {
        private PasswordAuthentication auth;

        private ProxyAuth(String user, String password) {
            auth = new PasswordAuthentication(user, password == null ? new char[]{} : password.toCharArray());
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return auth;
        }
    }

}
