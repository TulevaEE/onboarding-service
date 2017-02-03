package ee.tuleva.onboarding.kpr;


import ee.eesti.xtee6.kpr.*;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;

@Service
public class KPRClient {

    private final XRoadClientIdentifierType client;

    public KPRClient() {
        this.client = new XRoadClientIdentifierType();
        client.setObjectType(XRoadObjectType.SUBSYSTEM);
        client.setXRoadInstance("ee-dev");
        client.setMemberClass("COM");
        client.setMemberCode("14041764"); // Tuleva
        client.setSubsystemCode("tuleva");

        try {
            configureBypassSSL();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private KprV6PortType getPort() {
        String xRoadEndpoint = "http://localhost:8089/cgi-bin/TreasuryXrdWS/services/TreasuryXrdWS";

        KprV6PortType kprV6PortType = new KprV6Service().getKprV6Port();
        ((BindingProvider)kprV6PortType).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, xRoadEndpoint);
        return kprV6PortType;
    }

    public PensionAccountTransactionResponseType pensionAccountTransaction(PensionAccountTransactionType request, String idcode) {
        KprV6PortType port = getPort();

        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setObjectType(XRoadObjectType.SERVICE);
        service.setXRoadInstance("ee-dev");
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
        KprV6PortType port = getPort();

        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setObjectType(XRoadObjectType.SERVICE);
        service.setXRoadInstance("ee-dev");
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

    private static void configureBypassSSL() throws NoSuchAlgorithmException,
            KeyManagementException {
        SSLContext ssl_ctx = SSLContext.getInstance("SSL");
        TrustManager[] trust_mgr = get_trust_mgr();
        ssl_ctx.init(null, // key manager
                trust_mgr, // trust manager
                new SecureRandom()); // random number generator
        SSLSocketFactory sf = ssl_ctx.getSocketFactory();

        HttpsURLConnection.setDefaultSSLSocketFactory(sf);
        HttpsURLConnection.setDefaultHostnameVerifier(new DummyHostVerifier());
    }

    private static TrustManager[] get_trust_mgr() {
        TrustManager[] certs = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String t) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String t) {
            }
        } };
        return certs;
    }

    static class DummyHostVerifier implements HostnameVerifier {
        public boolean verify(String name, SSLSession sess) {
            return true;
        }
    }


}
