package ee.tuleva.onboarding.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class HttpsURLConnectionConfiguration {

  @Value("${ssl.keystore:#{null}}")
  private String keystore;

  @Value("${ssl.keystore.password:#{null}}")
  private String keystorePassword;

  @Value("${ssl.trustAllHTTPSHosts:#{false}}")
  private Boolean trustAllHTTPSHosts;

  @PostConstruct
  public void initialize() {

    KeyManager[] keyManagers = getKeyManagers();
    TrustManager[] trustManagers = getTrustManagers();
    initializeSslContext(keyManagers, trustManagers);

    if (trustAllHTTPSHosts) {
      HttpsURLConnection.setDefaultHostnameVerifier(new DummyHostVerifier());
    }
  }

  private void initializeSslContext(KeyManager[] keyManagers, TrustManager[] trustManagers) {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
      sslContext.init(keyManagers, trustManagers, new SecureRandom());
      SSLSocketFactory socketFactory = sslContext.getSocketFactory();
      HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
      SSLContext.setDefault(sslContext);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new RuntimeException(e);
    }
  }

  private TrustManager[] getTrustManagers() {
    return new TrustManager[] {
      new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String t) {}

        public void checkServerTrusted(X509Certificate[] certs, String t) {}
      }
    };
  }

  private KeyManager[] getKeyManagers() {
    KeyManager[] keyManagers = null;

    try {
      if (!isBlank(keystore)) {
        byte[] p12 = Files.readAllBytes(Paths.get(keystore));
        ByteArrayInputStream stream = new ByteArrayInputStream(p12);

        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(stream, this.keystorePassword.toCharArray());
        stream.close();

        String defaultAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(defaultAlgorithm);
        keyManagerFactory.init(keyStore, this.keystorePassword.toCharArray());
        keyManagers = keyManagerFactory.getKeyManagers();
      }

    } catch (KeyStoreException
        | IOException
        | CertificateException
        | UnrecoverableKeyException
        | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    return keyManagers;
  }

  static class DummyHostVerifier implements HostnameVerifier {
    public boolean verify(String name, SSLSession sess) {
      return true;
    }
  }
}
