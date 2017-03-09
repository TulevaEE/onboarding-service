package ee.tuleva.onboarding.kpr;


import com.sun.xml.ws.client.BindingProviderProperties;
import ee.eesti.xtee6.kpr.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.net.URL;
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
    }

    private XRoadServiceIdentifierType getServiceIdentifier(String serviceName) {
        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setObjectType(XRoadObjectType.SERVICE);
        service.setXRoadInstance(this.xroadInstance);
        service.setMemberClass("COM");
        service.setMemberCode("10111982"); // EVK
        service.setSubsystemCode("kpr");
        service.setServiceCode(serviceName);
        service.setServiceVersion("v1");
        return service;
    }

    private KprV6PortType getPort() {
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
        return getPort().pensionAccountTransaction(
                request,
                new Holder<XRoadClientIdentifierType>(client),
                new Holder<XRoadServiceIdentifierType>(getServiceIdentifier("pensionAccountTransaction")),
                new Holder<String>("EE" + idcode),
                new Holder<String>(UUID.randomUUID().toString()),
                new Holder<String>("4.0"));
    }

    public PensionAccountBalanceResponseType pensionAccountBalance(PensionAccountBalanceType request, String idcode) {
        return getPort().pensionAccountBalance(request,
                new Holder<XRoadClientIdentifierType>(client),
                new Holder<XRoadServiceIdentifierType>(getServiceIdentifier("pensionAccountBalance")),
                new Holder<String>("EE" + idcode),
                new Holder<String>(UUID.randomUUID().toString()),
                new Holder<String>("4.0"));
    }

    public PersonDataResponseType personData(String idcode) {
        return getPort().personData(new VoidType(),
                new Holder<XRoadClientIdentifierType>(client),
                new Holder<XRoadServiceIdentifierType>(getServiceIdentifier("personData")),
                new Holder<String>("EE" + idcode),
                new Holder<String>(UUID.randomUUID().toString()),
                new Holder<String>("4.0"));
    }

    public PersonalSelectionResponseType personalSelection(String idcode) {
        return getPort().personalSelection(new VoidType(),
                new Holder<XRoadClientIdentifierType>(client),
                new Holder<XRoadServiceIdentifierType>(getServiceIdentifier("personalSelection")),
                new Holder<String>("EE" + idcode),
                new Holder<String>(UUID.randomUUID().toString()),
                new Holder<String>("4.0"));
    }

}
