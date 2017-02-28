package ee.tuleva.onboarding.kpr;


import com.sun.xml.ws.client.BindingProviderProperties;
import ee.eesti.xtee6.kpr.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class KPRClient {

    private final XRoadClientIdentifierType client;
    private final String endpoint;
    private final String xroadInstance;
    private final int requestTimeout;
    private final int connectionTimeout;

    @Autowired
    public KPRClient(XRoadConfiguration conf) {
        this.endpoint = conf.getKprEndpoint();
        this.xroadInstance = conf.getInstance();
        this.requestTimeout = conf.getRequestTimeout();
        this.connectionTimeout = conf.getConnectionTimeout();

        this.client = new XRoadClientIdentifierType();
        client.setObjectType(XRoadObjectType.SUBSYSTEM);
        client.setXRoadInstance(this.xroadInstance);
        client.setMemberClass(conf.getMemberClass());
        client.setMemberCode(conf.getMemberCode());
        client.setSubsystemCode(conf.getSubsystemCode());

        new QuotaGuardProxyAuthenticator();

        if (conf.isInsecureHTTPS()) {
            try {
                configureBypassSSL();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (KeyManagementException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private KprV6PortType getPort() {

        System.setProperty("http.proxyHost", "eu-west-1-babbage.quotaguard.com");
        System.setProperty("http.proxyPort", String.valueOf(9293));
        System.setProperty("https.proxyHost","eu-west-1-babbage.quotaguard.com");
        System.setProperty("https.proxyPort", String.valueOf(9293));

        // copypaste from wsimport non-wrapped java code
        URL KPRV6SERVICE_WSDL_LOCATION = ee.eesti.xtee6.kpr.KprV6Service.class.getResource("kpr-v6.wsdl");
        if (KPRV6SERVICE_WSDL_LOCATION == null) {
            throw new WebServiceException("Cannot find 'kpr-v6.wsdl' wsdl. Place the resource correctly in the classpath.");
        }

        KprV6PortType kprV6PortType = new KprV6Service(KPRV6SERVICE_WSDL_LOCATION).getKprV6Port();
        Map<String, Object> requestContext = ((BindingProvider)kprV6PortType).getRequestContext();
        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, this.endpoint);
        requestContext.put(BindingProviderProperties.REQUEST_TIMEOUT, this.requestTimeout);
        requestContext.put(BindingProviderProperties.CONNECT_TIMEOUT, this.connectionTimeout);

        return kprV6PortType;
    }

    public PensionAccountTransactionResponseType pensionAccountTransaction(PensionAccountTransactionType request, String idcode) {

        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setObjectType(XRoadObjectType.SERVICE);
        service.setXRoadInstance(this.xroadInstance);
        service.setMemberClass("COM");
        service.setMemberCode("10111982"); // EVK
        service.setSubsystemCode("kpr");
        service.setServiceCode("pensionAccountTransaction");
        service.setServiceVersion("v1");

        return getPort().pensionAccountTransaction(
                request,
                new Holder<XRoadClientIdentifierType>(client),
                new Holder<XRoadServiceIdentifierType>(service),
                new Holder<String>("EE" + idcode),
                new Holder<String>(UUID.randomUUID().toString()),
                new Holder<String>("4.0"));
    }


    public PensionAccountBalanceResponseType pensionAccountBalance(PensionAccountBalanceType request, String idcode) {
        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setObjectType(XRoadObjectType.SERVICE);
        service.setXRoadInstance(this.xroadInstance);
        service.setMemberClass("COM");
        service.setMemberCode("10111982"); // EVK
        service.setSubsystemCode("kpr");
        service.setServiceCode("pensionAccountBalance");
        service.setServiceVersion("v1");

        return getPort().pensionAccountBalance(request,
                new Holder<XRoadClientIdentifierType>(client),
                new Holder<XRoadServiceIdentifierType>(service),
                new Holder<String>("EE" + idcode),
                new Holder<String>(UUID.randomUUID().toString()),
                new Holder<String>("4.0"));
    }

    private static void configureBypassSelfSignedSSL() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("SSL");

        KeyManagerFactory factory = null;
        try {
            String base64PKCSKeystore = System.getenv("XTEE_KEYSTORE");
            String keystorePassword = System.getenv("XTEE_KEYSTORE_PASS");
            byte[] p12 = Base64.getDecoder().decode(base64PKCSKeystore);
            ByteArrayInputStream bais = new ByteArrayInputStream(p12);

            KeyStore ks = KeyStore.getInstance("pkcs12");
            ks.load(bais, keystorePassword.toCharArray());
            bais.close();

            String defaultAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
            factory = KeyManagerFactory.getInstance(defaultAlgorithm);
            factory.init(ks, keystorePassword.toCharArray());
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
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

        sslContext.init(factory.getKeyManagers(), trustManagers, new SecureRandom());
        SSLSocketFactory sf = sslContext.getSocketFactory();
        HttpsURLConnection.setDefaultSSLSocketFactory(sf);
        HttpsURLConnection.setDefaultHostnameVerifier(new DummyHostVerifier());
    }
    
    static class DummyHostVerifier implements HostnameVerifier {
        public boolean verify(String name, SSLSession sess) {
            return true;
        }
    }


}
