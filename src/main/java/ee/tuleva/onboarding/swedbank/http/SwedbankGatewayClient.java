package ee.tuleva.onboarding.swedbank.http;

import static org.springframework.http.HttpMethod.POST;

import ee.swedbank.gateway.request.Ping;
import jakarta.xml.bind.JAXBContext;
import java.io.StringWriter;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

  @Autowired
  @Qualifier("swedbankGatewayRestTemplate")
  private final RestTemplate restTemplate;

  public void sendPong() {
    HttpEntity<String> requestEntity = new HttpEntity<>(getPingXml(), getHeaders("TEST-ID-1234"));

    restTemplate.exchange(getRequestUrl("communication-tests"), POST, requestEntity, String.class);
  }

  @SneakyThrows
  private String getPingXml() {
    Ping ping = new Ping();
    ping.setValue("Test");

    // TODO WIP, more generic logic in the future
    JAXBContext context = JAXBContext.newInstance(Ping.class);
    var marshaller = context.createMarshaller();

    StringWriter sw = new StringWriter();
    marshaller.marshal(ping, sw);
    return sw.toString();
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
