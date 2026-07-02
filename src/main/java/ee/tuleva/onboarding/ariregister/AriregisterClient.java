package ee.tuleva.onboarding.ariregister;

import ee.tuleva.onboarding.ariregister.generated.EttevottegaSeotudIsikudParing;
import ee.tuleva.onboarding.ariregister.generated.EttevottegaSeotudIsikudV1;
import ee.tuleva.onboarding.ariregister.generated.EttevottegaSeotudIsikudV1Response;
import ee.tuleva.onboarding.ariregister.generated.ObjectFactory;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV2;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV2Response;
import ee.tuleva.onboarding.ariregister.generated.detailandmed.DetailandmedV6Query;
import ee.tuleva.onboarding.ariregister.generated.kasusaajad.TegelikudKasusaajadRequest;
import ee.tuleva.onboarding.ariregister.generated.kasusaajad.TegelikudKasusaajadV2;
import ee.tuleva.onboarding.ariregister.generated.kasusaajad.TegelikudKasusaajadV2Response;
import jakarta.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AriregisterClient {

  private static final ObjectFactory FACTORY = new ObjectFactory();
  private static final ee.tuleva.onboarding.ariregister.generated.detailandmed.ObjectFactory
      DETAILANDMED_FACTORY =
          new ee.tuleva.onboarding.ariregister.generated.detailandmed.ObjectFactory();

  private final WebServiceTemplate ariregisterWebServiceTemplate;
  private final AriregisterProperties properties;

  public List<CompanyRelationship> getCompanyRelationships(String registryCode) {
    log.info("Fetching company relationships: registryCode={}", registryCode);

    var requestBody = new EttevottegaSeotudIsikudParing();
    requestBody.setAriregisterKasutajanimi(properties.username());
    requestBody.setAriregisterParool(properties.password());
    requestBody.setAriregistriKood(new BigInteger(registryCode));
    requestBody.setKeel("est");

    var request = new EttevottegaSeotudIsikudV1();
    request.setKeha(requestBody);

    @SuppressWarnings("unchecked")
    var response =
        (JAXBElement<EttevottegaSeotudIsikudV1Response>)
            ariregisterWebServiceTemplate.marshalSendAndReceive(
                FACTORY.createEttevottegaSeotudIsikudV1(request));

    if (response == null || response.getValue() == null) {
      throw new IllegalStateException(
          "No company relationships response: registryCode=" + registryCode);
    }
    var responseBody = response.getValue().getKeha();
    if (responseBody == null || responseBody.getSeosed() == null) {
      return List.of();
    }

    return responseBody.getSeosed().stream().map(CompanyRelationshipMapper::fromSeos).toList();
  }

  public Optional<CompanyDetail> getCompanyDetails(String registryCode) {
    log.info("Fetching company details: registryCode={}", registryCode);

    var query = new DetailandmedV6Query();
    query.setAriregisterKasutajanimi(
        DETAILANDMED_FACTORY.createDetailandmedV6QueryAriregisterKasutajanimi(
            properties.username()));
    query.setAriregisterParool(
        DETAILANDMED_FACTORY.createDetailandmedV6QueryAriregisterParool(properties.password()));
    query.setAriregistriKood(new BigInteger(registryCode));
    query.setYandmed(true);
    query.setIandmed(true);
    query.setKandmed(false);
    query.setDandmed(false);
    query.setMaarused(false);
    query.setKeel("est");

    var request = new DetailandmedV2();
    request.setKeha(query);

    @SuppressWarnings("unchecked")
    var response =
        (JAXBElement<DetailandmedV2Response>)
            ariregisterWebServiceTemplate.marshalSendAndReceive(
                DETAILANDMED_FACTORY.createDetailandmedV2(request));

    if (response == null || response.getValue() == null) {
      throw new IllegalStateException("No company details response: registryCode=" + registryCode);
    }
    var responseBody = response.getValue().getKeha();
    if (responseBody == null
        || responseBody.getEttevotjad() == null
        || responseBody.getEttevotjad().isNil()) {
      return Optional.empty();
    }

    return responseBody.getEttevotjad().getValue().getItem().stream()
        .findFirst()
        .map(CompanyDetailMapper::fromEttevotja);
  }

  public List<CompanyRelationship> getActiveCompanyRelationships(
      String registryCode, LocalDate asOf) {
    return getCompanyRelationships(registryCode).stream()
        .filter(r -> r.startDate() == null || !r.startDate().isAfter(asOf))
        .filter(r -> r.endDate() == null || !r.endDate().isBefore(asOf))
        .toList();
  }

  public BeneficialOwners getBeneficialOwners(String registryCode) {
    log.info("Fetching beneficial owners: registryCode={}", registryCode);

    var requestBody = new TegelikudKasusaajadRequest();
    requestBody.setAriregisterKasutajanimi(properties.username());
    requestBody.setAriregisterParool(properties.password());
    requestBody.setAriregistriKood(Integer.parseInt(registryCode));
    requestBody.setAinultKehtivad(true);
    requestBody.setKeel("est");

    var request = new TegelikudKasusaajadV2();
    request.setKeha(requestBody);

    var response =
        (TegelikudKasusaajadV2Response)
            ariregisterWebServiceTemplate.marshalSendAndReceive(request);

    if (response == null || response.getKeha() == null) {
      throw new IllegalStateException(
          "Empty beneficial owners response: registryCode=" + registryCode);
    }
    var responseBody = response.getKeha();
    var ownersData = responseBody.getKasusaajad();
    if (ownersData == null) {
      return BeneficialOwners.none();
    }
    var owners =
        ownersData.getKasusaaja().stream().map(BeneficialOwnerMapper::fromKasusaaja).toList();
    return new BeneficialOwners(owners, ownersData.getPeidetudKasusaajateArv().intValue());
  }
}
