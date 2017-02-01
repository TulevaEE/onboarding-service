package ee.tuleva.onboarding.xroad;


import ee.eesti.xtee6.kpr.KprV6PortType;
import ee.eesti.xtee6.kpr.KprV6Service;
import org.springframework.stereotype.Service;

import javax.xml.ws.BindingProvider;

@Service
public class XRoadClient {

    public KprV6PortType getPort() {
        String xRoadEndpoint = "http://localhost:8089/cgi-bin/TreasuryXrdWS/services/TreasuryXrdWS";

        KprV6PortType kprV6PortType = new KprV6Service().getKprV6Port();
        ((BindingProvider)kprV6PortType).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, xRoadEndpoint);
        return kprV6PortType;
    }

}
