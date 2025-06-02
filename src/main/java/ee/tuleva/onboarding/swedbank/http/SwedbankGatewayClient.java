package ee.tuleva.onboarding.swedbank.http;

import static org.springframework.http.HttpMethod.*;

import ee.swedbank.gateway.request.AccountStatement;
import ee.swedbank.gateway.response.B4B;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
  private String baseUrl;

  @Value("${swedbank-gateway.client-id}")
  private String clientId;

  private final Clock clock;

  private final SwedbankGatewayMarshaller marshaller;

  @Autowired
  @Qualifier("swedbankGatewayRestTemplate")
  private final RestTemplate restTemplate;

  public void sendStatementRequest(AccountStatement entity, String uuid) {
    var requestXml = marshaller.marshalToString(entity);

    HttpEntity<String> requestEntity = new HttpEntity<>(requestXml, getHeaders(uuid));
    restTemplate.exchange(getRequestUrl("account-statement"), POST, requestEntity, String.class);
  }

  public Optional<SwedbankGatewayResponse> getResponse() {
    HttpEntity<Void> messageEntity = new HttpEntity<>(getHeaders(UUID.randomUUID().toString()));

    var messagesResponse =
        restTemplate.exchange(getRequestUrl("messages"), GET, messageEntity, String.class);
    if (messagesResponse.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(204))) {
      // 204 no content
      return Optional.empty();
    }

    var response = marshaller.unMarshal(messagesResponse.getBody(), B4B.class);
    return Optional.of(
        new SwedbankGatewayResponse(
            response,
            messagesResponse.getHeaders().get("X-Request-ID").getFirst(),
            messagesResponse.getHeaders().get("X-Tracking-ID").getFirst()));
  }

  public void acknowledgeResponse(SwedbankGatewayResponse response) {
    String requestId = UUID.randomUUID().toString();
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

  private HttpHeaders getHeaders(String requestId) {
    var headers = new HttpHeaders();
    headers.add("X-Request-ID", requestId);
    headers.add("X-Agreement-ID", "1234"); // TODO sandbox hardcode
    headers.add(
        "Date",
        DateTimeFormatter.RFC_1123_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(clock.instant()));
    headers.add("Content-Type", "application/xml; charset=utf-8");

    return headers;
  }
}
