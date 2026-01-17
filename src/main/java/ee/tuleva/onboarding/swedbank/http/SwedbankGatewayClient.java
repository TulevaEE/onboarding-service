package ee.tuleva.onboarding.swedbank.http;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import ee.tuleva.onboarding.banking.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import jakarta.xml.bind.JAXBElement;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class SwedbankGatewayClient {

  @Value("${swedbank-gateway.url}")
  private final String baseUrl;

  @Value("${swedbank-gateway.client-id}")
  private final String clientId;

  @Value("${swedbank-gateway.agreement-id}")
  private final String agreementId;

  private final Clock clock;
  private final Iso20022Marshaller marshaller;
  private final PaymentMessageGenerator paymentMessageGenerator;

  @Qualifier("swedbankGatewayRestTemplate")
  private final RestTemplate restTemplate;

  public void sendPaymentRequest(PaymentRequest paymentRequest, UUID requestId) {
    var paymentMessage = paymentMessageGenerator.generatePaymentMessage(paymentRequest);
    var requestEntity = new HttpEntity<>(paymentMessage, getHeaders(requestId));
    restTemplate.exchange(getRequestUrl("payment-initiations"), POST, requestEntity, String.class);
  }

  public void sendStatementRequest(
      JAXBElement<ee.tuleva.onboarding.banking.iso20022.camt060.Document> entity, UUID uuid) {
    var requestXml = marshaller.marshalToString(entity);

    HttpEntity<String> requestEntity = new HttpEntity<>(requestXml, getHeaders(uuid));
    restTemplate.exchange(getRequestUrl("account-statements"), POST, requestEntity, String.class);
  }

  public Optional<SwedbankGatewayResponseDto> getResponse() {
    HttpEntity<Void> messageEntity = new HttpEntity<>(getHeaders(UUID.randomUUID()));

    var messagesResponse =
        restTemplate.exchange(getRequestUrl("messages"), GET, messageEntity, String.class);
    if (messagesResponse.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(204))) {
      // 204 no content
      return Optional.empty();
    }

    return Optional.of(
        new SwedbankGatewayResponseDto(
            messagesResponse.getBody(),
            // TODO handle weird statement request-id?
            messagesResponse.getHeaders().get("X-Request-ID").getFirst(),
            messagesResponse.getHeaders().get("X-Tracking-ID").getFirst()));
  }

  public void acknowledgeResponse(SwedbankGatewayResponseDto response) {
    var requestId = UUID.randomUUID();
    HttpEntity<Void> messageEntity = new HttpEntity<>(getHeaders(requestId));

    URI uri =
        UriComponentsBuilder.fromUriString(getRequestUrl("messages"))
            .queryParam("trackingId", response.responseTrackingId())
            .build()
            .toUri();

    restTemplate.exchange(uri, DELETE, messageEntity, String.class);
  }

  private String getRequestUrl(String path) {
    return UriComponentsBuilder.fromUriString(baseUrl + path)
        .queryParam("client_id", clientId)
        .build()
        .toUriString();
  }

  private HttpHeaders getHeaders(UUID requestId) {
    var headers = new HttpHeaders();
    headers.add("X-Request-ID", serializeRequestId(requestId));
    headers.add("X-Agreement-ID", agreementId);
    headers.add(
        "Date",
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("UTC")).format(clock.instant()));
    headers.add("Content-Type", "application/xml; charset=utf-8");

    return headers;
  }

  private static String serializeRequestId(UUID requestId) {
    return requestId.toString().replace("-", "");
  }
}
