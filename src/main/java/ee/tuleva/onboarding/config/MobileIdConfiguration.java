package ee.tuleva.onboarding.config;

import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidClient;
import ee.sk.mid.exception.MidInternalErrorException;
import ee.sk.mid.rest.MidConnector;
import ee.sk.mid.rest.MidSessionStatusPoller;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

@Configuration
@Slf4j
public class MobileIdConfiguration {

    @Value("${truststore.path}")
    private String trustStorePath;

    @Value("${smartid.relyingPartyUUID}")
    private String relyingPartyUUID;

    @Value("${smartid.relyingPartyName}")
    private String relyingPartyName;

    @Value("${mobile-id.hostUrl}")
    private String hostUrl;

    @Value("${mobile-id.pollingSleepTimeoutSeconds}")
    private int pollingSleepTimeoutSeconds;

    @Bean
    @SneakyThrows
    MidClient mobileIDClient(ResourceLoader resourceLoader) {
        return MidClient.newBuilder()
            .withRelyingPartyName(relyingPartyName)
            .withRelyingPartyUUID(relyingPartyUUID)
            .withHostUrl(hostUrl)
            .withPollingSleepTimeoutSeconds(pollingSleepTimeoutSeconds)
            .withSslKeyStore(getTrustStore(resourceLoader))
            .build();
    }

    @Bean
    MidConnector mobileIDConnector(MidClient mobileIDClient) {
        return mobileIDClient.getMobileIdConnector();
    }

    @Bean
    MidSessionStatusPoller mobileIDSessionStatusPoller(MidClient mobileIDClient) {
        return mobileIDClient.getSessionStatusPoller();
    }

    @Bean
    MidAuthenticationResponseValidator mobileIDValidator(ResourceLoader resourceLoader) {
        MidAuthenticationResponseValidator validator = new MidAuthenticationResponseValidator();
        initializeTrustedCertificatesFromTrustStore(validator, resourceLoader);
        return validator;
    }

    private void initializeTrustedCertificatesFromTrustStore(MidAuthenticationResponseValidator validator,
                                                             ResourceLoader resourceLoader) {
        try {
            KeyStore trustStore = getTrustStore(resourceLoader);
            Enumeration<String> aliases = trustStore.aliases();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                X509Certificate certificate = (X509Certificate) trustStore.getCertificate(alias);
                validator.addTrustedCACertificate(certificate);
            }
        } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new MidInternalErrorException("Error initializing trusted CA certificates", e);
        }
    }

    private KeyStore getTrustStore(ResourceLoader resourceLoader)
        throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        Resource resource = resourceLoader.getResource("file:" + trustStorePath);
        InputStream inputStream = resource.getInputStream();
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(inputStream, null);
        return trustStore;
    }
}
