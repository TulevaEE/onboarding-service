package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.EttevottegaSeotudIsikudParing;
import ee.tuleva.onboarding.ariregister.generated.EttevottegaSeotudIsikudV1;
import ee.tuleva.onboarding.ariregister.generated.EttevottegaSeotudIsikudV1Response;
import ee.tuleva.onboarding.ariregister.generated.ObjectFactory;
import jakarta.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AriregisterClient {

  private static final ObjectFactory FACTORY = new ObjectFactory();

  private final WebServiceTemplate ariregisterWebServiceTemplate;
  private final AriregisterProperties properties;

  public List<CompanyRelationship> getCompanyRelationships(String registryCode) {
    log.info("Fetching company relationships: registryCode={}", registryCode);

    var paring = new EttevottegaSeotudIsikudParing();
    paring.setAriregisterKasutajanimi(properties.username());
    paring.setAriregisterParool(properties.password());
    paring.setAriregistriKood(new BigInteger(registryCode));
    paring.setKeel("est");

    var request = new EttevottegaSeotudIsikudV1();
    request.setKeha(paring);

    @SuppressWarnings("unchecked")
    var response =
        (JAXBElement<EttevottegaSeotudIsikudV1Response>)
            ariregisterWebServiceTemplate.marshalSendAndReceive(
                FACTORY.createEttevottegaSeotudIsikudV1(request));

    var vastus = response.getValue().getKeha();
    if (vastus == null || vastus.getSeosed() == null) {
      return List.of();
    }

    return vastus.getSeosed().stream().map(CompanyRelationshipMapper::fromSeos).toList();
  }

  public List<CompanyRelationship> getActiveCompanyRelationships(
      String registryCode, LocalDate asOf) {
    return getCompanyRelationships(registryCode).stream()
        .filter(r -> r.startDate() == null || !r.startDate().isAfter(asOf))
        .filter(r -> r.endDate() == null || !r.endDate().isBefore(asOf))
        .toList();
  }
}
