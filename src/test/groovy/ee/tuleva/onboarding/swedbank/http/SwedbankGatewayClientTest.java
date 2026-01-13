package ee.tuleva.onboarding.swedbank.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;

import ee.tuleva.onboarding.banking.converter.LocalDateToXmlGregorianCalendarConverter;
import ee.tuleva.onboarding.banking.converter.ZonedDateTimeToXmlGregorianCalendarConverter;
import ee.tuleva.onboarding.banking.xml.Iso20022Marshaller;
import ee.tuleva.onboarding.swedbank.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import ee.tuleva.onboarding.time.TestClockHolder;
import jakarta.xml.bind.JAXBElement;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class SwedbankGatewayClientTest {

  @Mock private RestTemplate restTemplate;
  @Mock private Iso20022Marshaller marshaller;
  @Mock private PaymentMessageGenerator paymentMessageGenerator;

  private SwedbankGatewayClient client;

  private final String baseUrl = "https://swedbank-gateway.test/";
  private final String clientId = "test-client";
  private final String agreementId = "agreement-id";

  private final UUID requestUuid = UUID.fromString("49171497-dc12-4f84-bc08-b03b13616399");
  private final String trackingId = "swedbank-tracking-id";

  @BeforeEach
  void setUp() {
    client =
        new SwedbankGatewayClient(
            baseUrl,
            clientId,
            agreementId,
            TestClockHolder.clock,
            marshaller,
            paymentMessageGenerator,
            new LocalDateToXmlGregorianCalendarConverter(),
            new ZonedDateTimeToXmlGregorianCalendarConverter(),
            restTemplate);
  }

  @Test
  void sendPaymentRequest() {
    var paymentRequest = PaymentRequest.builder().ourId("123").build();
    when(paymentMessageGenerator.generatePaymentMessage(any())).thenReturn("<payment-xml/>");

    client.sendPaymentRequest(paymentRequest, requestUuid);

    var expectedHeaders = new HttpHeaders();
    expectedHeaders.add("X-Request-ID", requestUuid.toString().replace("-", ""));
    expectedHeaders.add("X-Agreement-ID", agreementId);
    expectedHeaders.add("Date", "Wed, 1 Jan 2020 14:13:15 GMT");
    expectedHeaders.add("Content-Type", "application/xml; charset=utf-8");
    verify(restTemplate)
        .exchange(
            baseUrl + "payment-initiations?client_id=test-client",
            POST,
            new HttpEntity<>("<payment-xml/>", expectedHeaders),
            String.class);
  }

  @Test
  @DisplayName("send request – sends request and returns request id")
  void sendStatementRequest() {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Ping><Value>Test</Value></Ping>";
    String expectedUrl = baseUrl + "account-statements?client_id=" + clientId;

    when(marshaller.marshalToString(any())).thenReturn(xml);

    when(restTemplate.exchange(eq(expectedUrl), eq(POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("OK"));

    client.sendStatementRequest(mock(JAXBElement.class), requestUuid);

    verify(restTemplate).exchange(eq(expectedUrl), eq(POST), any(), eq(String.class));
  }

  @Test
  @DisplayName("get messages – returns response")
  void getResponse_shouldReturnResponse() {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><test></test>";
    String expectedUrl = baseUrl + "messages?client_id=" + clientId;

    var serializedUuid = requestUuid.toString().replace("-", "");
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Request-ID", serializedUuid);
    headers.add("X-Tracking-ID", trackingId);

    ResponseEntity<String> responseEntity = new ResponseEntity<>(xml, headers, HttpStatus.OK);

    when(restTemplate.exchange(
            eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(responseEntity);

    Optional<SwedbankGatewayResponseDto> response = client.getResponse();

    assertThat(response).isPresent();
    assertThat(response.get().rawResponse()).isEqualTo(xml);
    assertThat(response.get().requestTrackingId()).isEqualTo(serializedUuid);
    assertThat(response.get().responseTrackingId()).isEqualTo(trackingId);
  }

  @Test
  @DisplayName("get messages – returns empty response when 204")
  void getEmptyResponse() {
    String expectedUrl = baseUrl + "messages?client_id=" + clientId;

    ResponseEntity<String> responseEntity = new ResponseEntity<>(HttpStatus.NO_CONTENT);
    when(restTemplate.exchange(
            eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(responseEntity);

    Optional<SwedbankGatewayResponseDto> response = client.getResponse();

    assertThat(response).isEmpty();
  }

  @Test
  @DisplayName("acknowledges messages – calls delete endpoint")
  void acknowledgePong_shouldCallDeleteEndpoint() {

    SwedbankGatewayResponseDto response =
        new SwedbankGatewayResponseDto("req-abc", requestUuid.toString(), trackingId);

    URI expectedUri =
        URI.create(baseUrl + "messages?client_id=" + clientId + "&trackingId=" + trackingId);

    client.acknowledgeResponse(response);

    verify(restTemplate)
        .exchange(eq(expectedUri), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class));
  }
}
