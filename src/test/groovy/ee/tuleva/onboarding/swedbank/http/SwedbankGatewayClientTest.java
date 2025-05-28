package ee.tuleva.onboarding.swedbank.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.swedbank.gateway.response.B4B;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class SwedbankGatewayClientTest {

  @Mock private RestTemplate restTemplate;

  @Mock private SwedbankGatewayMarshaller marshaller;

  @InjectMocks private SwedbankGatewayClient client;

  private final String baseUrl = "https://swedbank-gateway.test/";
  private final String clientId = "test-client";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    client = new SwedbankGatewayClient(TestClockHolder.clock, marshaller, restTemplate);
    ReflectionTestUtils.setField(client, "baseUrl", baseUrl);
    ReflectionTestUtils.setField(client, "clientId", clientId);
  }

  @Test
  @DisplayName("send request – sends request and returns request id")
  void sendRequest() {
    Object request = new Object();
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Ping><Value>Test</Value></Ping>";
    String expectedUrl = baseUrl + "communication-tests?client_id=" + clientId;

    when(marshaller.marshalToString(request)).thenReturn(xml);
    when(restTemplate.exchange(
            eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("OK"));

    String requestId = client.sendRequest(request);

    assertThat(requestId).isNotNull();
    verify(restTemplate)
        .exchange(eq(expectedUrl), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  @DisplayName("get messages – returns response")
  void getResponse_shouldReturnUnmarshalledObject() {
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <B4B xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"http://swedbankgateway.net/valid/hgw-response.xsd\"> <Pong from=\"EE\"> <Value>Test</Value> </Pong> </B4B>";
    String expectedUrl = baseUrl + "messages?client_id=" + clientId;

    B4B mockB4B = new B4B();
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Request-ID", "req-123");
    headers.add("X-Tracking-ID", "track-456");

    ResponseEntity<String> responseEntity = new ResponseEntity<>(xml, headers, HttpStatus.OK);

    when(restTemplate.exchange(
            eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(responseEntity);
    when(marshaller.unMarshal(xml, B4B.class)).thenReturn(mockB4B);

    Optional<SwedbankGatewayResponse> response = client.getResponse();

    assertThat(response).isPresent();
    assertThat(response.get().response()).isEqualTo(mockB4B);
    assertThat(response.get().messageTrackingId()).isEqualTo("req-123");
    assertThat(response.get().responseTrackingId()).isEqualTo("track-456");
  }

  @Test
  @DisplayName("get messages – returns empty response when 204")
  void getEmptyResponse() {
    String expectedUrl = baseUrl + "messages?client_id=" + clientId;

    ResponseEntity<String> responseEntity = new ResponseEntity<>(HttpStatus.NO_CONTENT);
    when(restTemplate.exchange(
            eq(expectedUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(responseEntity);

    Optional<SwedbankGatewayResponse> response = client.getResponse();

    assertThat(response).isEmpty();
  }

  @Test
  @DisplayName("acknowledges messages – calls delete endpoint")
  void acknowledgePong_shouldCallDeleteEndpoint() {
    SwedbankGatewayResponse response =
        new SwedbankGatewayResponse(new B4B(), "req-abc", "track-xyz");

    URI expectedUri =
        URI.create(baseUrl + "messages?client_id=" + clientId + "&trackingId=track-xyz");

    when(restTemplate.exchange(
            eq(expectedUri), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok(""));

    client.acknowledgePong(response);

    verify(restTemplate)
        .exchange(eq(expectedUri), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class));
  }
}
