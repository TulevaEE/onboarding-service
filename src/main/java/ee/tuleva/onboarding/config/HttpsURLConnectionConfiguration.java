package ee.tuleva.onboarding.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Slf4j
@Configuration
public class HttpsURLConnectionConfiguration {

    @Value("${ssl.keystore:#{null}}")
    private String base64PKCSKeystore;

    @Value("${ssl.keystorePassword:#{null}}")
    private String keystorePassword;

    @Value("${ssl.trustAllHTTPSHosts}")
    private Boolean trustAllHTTPSHosts;

    @PostConstruct
    public void initialize() {

        KeyManager[] keyManagers = null;

        try {
            if (this.base64PKCSKeystore != null && this.base64PKCSKeystore.trim().length() > 0) {
                byte[] p12 = Base64.getDecoder().decode(this.base64PKCSKeystore);
                ByteArrayInputStream bais = new ByteArrayInputStream(p12);

                KeyStore ks = KeyStore.getInstance("pkcs12");
                ks.load(bais, this.keystorePassword.toCharArray());
                bais.close();

                String defaultAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
                KeyManagerFactory factory = KeyManagerFactory.getInstance(defaultAlgorithm);
                factory.init(ks, this.keystorePassword.toCharArray());
                keyManagers = factory.getKeyManagers();
            }

        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }


        TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String t) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String t) {
            }
        } };

        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            SSLSocketFactory sf = sslContext.getSocketFactory();
            HttpsURLConnection.setDefaultSSLSocketFactory(sf);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }

        if (this.trustAllHTTPSHosts != null && this.trustAllHTTPSHosts) {
            HttpsURLConnection.setDefaultHostnameVerifier(new DummyHostVerifier());
        }
    }

    static class DummyHostVerifier implements HostnameVerifier {
        public boolean verify(String name, SSLSession sess) {
            return true;
        }
    }


}
