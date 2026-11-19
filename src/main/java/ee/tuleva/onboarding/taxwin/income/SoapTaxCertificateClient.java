package ee.tuleva.onboarding.taxwin.income;

import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.taxwin.DeclaredMonth;
import ee.tuleva.onboarding.taxwin.IncomeWindow;
import ee.tuleva.onboarding.taxwin.InvalidTaxDataConsentException;
import ee.tuleva.onboarding.taxwin.TaxCertificateClient;
import ee.tuleva.onboarding.taxwin.TaxDataRequestRejectedException;
import ee.tuleva.onboarding.taxwin.TaxDataUnavailableException;
import ee.tuleva.onboarding.taxwin.TaxHeadroomProperties;
import ee.tuleva.onboarding.taxwin.income.generated.Error;
import ee.tuleva.onboarding.taxwin.income.generated.TaxCertificate;
import ee.tuleva.onboarding.taxwin.income.generated.TaxCertificateRequestType;
import ee.tuleva.onboarding.taxwin.income.generated.TaxCertificateResponse;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.oxm.XmlMappingException;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.client.SoapFaultClientException;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class SoapTaxCertificateClient implements TaxCertificateClient {

  private static final String INVALID_CONSENT = "INVALID_CONSENT";
  private static final String ESTONIAN_PERSONAL_CODE_PREFIX = "EE";
  private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MM.yyyy");

  private static final RetryTemplate RETRY_ONCE_ON_CONNECTION_FAILURE =
      new RetryTemplate(
          RetryPolicy.builder()
              .includes(WebServiceIOException.class)
              .maxRetries(1)
              .delay(ofMillis(200))
              .build());

  private final WebServiceTemplate taxDataWebServiceTemplate;
  private final TaxHeadroomProperties properties;

  @Override
  public Map<YearMonth, DeclaredMonth> declaredIncome(
      String personalCode, String consentReference, IncomeWindow window) {
    TaxCertificate request = request(personalCode, consentReference, window);
    try {
      var response =
          (TaxCertificateResponse)
              RETRY_ONCE_ON_CONNECTION_FAILURE.invoke(
                  () ->
                      taxDataWebServiceTemplate.marshalSendAndReceive(
                          request,
                          new XRoadSoapHeaders(
                              properties.clientId(),
                              properties.taxDataService(),
                              ESTONIAN_PERSONAL_CODE_PREFIX + personalCode,
                              UUID.randomUUID())));
      return TaxCertificateMapper.declaredMonths(
          requireNonNull(response, "Tax data service returned no response").getResponse(), window);
    } catch (SoapFaultClientException e) {
      throw rejection(e);
    } catch (WebServiceIOException e) {
      throw new TaxDataUnavailableException("Tax data service is unavailable", e);
    }
  }

  private static TaxCertificate request(
      String personalCode, String consentReference, IncomeWindow window) {
    var body = new TaxCertificateRequestType();
    body.setCode(personalCode);
    body.setConsentReference(consentReference);
    body.setPeriodStart(window.first().format(PERIOD));
    body.setPeriodEnd(window.last().format(PERIOD));
    var request = new TaxCertificate();
    request.setRequest(body);
    return request;
  }

  private RuntimeException rejection(SoapFaultClientException fault) {
    @Nullable SoapFault soapFault = fault.getSoapFault();
    Optional<String> type =
        soapFault == null ? Optional.empty() : errorType(soapFault.getFaultDetail());
    if (type.isEmpty()) {
      return new TaxDataUnavailableException(
          "Tax data request failed before reaching the tax data service", fault);
    }
    if (INVALID_CONSENT.equals(type.get())) {
      return new InvalidTaxDataConsentException("Tax data consent was rejected", fault);
    }
    return new TaxDataRequestRejectedException(
        "Tax data request was rejected: type=" + type.get(), fault);
  }

  private Optional<String> errorType(@Nullable SoapFaultDetail detail) {
    if (detail == null) {
      return Optional.empty();
    }
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(detail.getDetailEntries(), 0), false)
        .flatMap(entry -> taxDataServiceError(entry).stream())
        .map(Error::getType)
        .findFirst();
  }

  private Optional<Error> taxDataServiceError(SoapFaultDetailElement entry) {
    try {
      return Optional.of(
              requireNonNull(taxDataWebServiceTemplate.getUnmarshaller(), "No unmarshaller")
                  .unmarshal(entry.getSource()))
          .filter(Error.class::isInstance)
          .map(Error.class::cast);
    } catch (IOException | XmlMappingException e) {
      return Optional.empty();
    }
  }
}
