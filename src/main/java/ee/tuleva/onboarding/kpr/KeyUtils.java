package ee.tuleva.onboarding.kpr;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Base64;

public class KeyUtils {

    /**
     * Creates keystores from base64 string, that is alternative to "no-file" environments (Heroku).
     */
    public static KeyStore createKeystoreFromPKCS12Store(String base64PKCS12Store, String keystorePassword) {
        byte[] p12 = Base64.getDecoder().decode(base64PKCS12Store);
        ByteArrayInputStream bais = new ByteArrayInputStream(p12);
        try {
            KeyStore keyStore = KeyStore.getInstance("pkcs12");
            keyStore.load(bais, keystorePassword.toCharArray());
            bais.close();
            return keyStore;
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }


    public static SSLContext createSSLContext(String keyStore, String keyStorePassword, String trustStore, String trustStorePassword) {
        if (keyStore == null || keyStore.isEmpty()) {
            throw new IllegalArgumentException("Keystore is not specified!");
        }

        if (trustStore == null || trustStore.isEmpty()) {
            throw new IllegalArgumentException("Truststore is not specified!");
        }

        KeyStore ks = createKeystoreFromPKCS12Store(keyStore, keyStorePassword);
        KeyStore ts = createKeystoreFromPKCS12Store(trustStore, trustStorePassword);

        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyStorePassword.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SSLContext sslContext = SSLContext.getInstance("SSLv3");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }


}
