package ee.tuleva.onboarding.banking.seb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@SpringJUnitConfig(classes = SebGatewayClientRetryTest.TestConfig.class)
class SebGatewayClientRetryTest {

  private static final String URL = "http://seb-gateway.local/v1/imported-payment-files";
  private static final String IDEMPOTENCY_KEY = "e2ee2ee2ee2ee2ee2ee2ee2ee2ee2ee2";

  @Autowired SebGatewayClient sebGatewayClient;
  @Autowired MockRestServiceServer mockServer;

  @BeforeEach
  void resetMockServer() {
    mockServer.reset();
  }

  @Test
  void submitPaymentFile_retriesOn502AndSucceeds() {
    expectCall().andRespond(withServerError());
    expectCall().andRespond(withServerError());
    expectCall().andRespond(withSuccess("<ok/>", null));

    String result = sebGatewayClient.submitPaymentFile("<xml/>", IDEMPOTENCY_KEY);

    assertThat(result).isEqualTo("<ok/>");
    mockServer.verify();
  }

  @Test
  void submitPaymentFile_exhaustsEightAttemptsOnPersistent502() {
    for (int i = 0; i < 8; i++) {
      expectCall().andRespond(withServerError());
    }

    assertThatThrownBy(() -> sebGatewayClient.submitPaymentFile("<xml/>", IDEMPOTENCY_KEY))
        .isInstanceOf(HttpServerErrorException.class);
    mockServer.verify();
  }

  @Test
  void submitPaymentFile_doesNotRetryOn400() {
    expectCall().andRespond(withBadRequest());

    assertThatThrownBy(() -> sebGatewayClient.submitPaymentFile("<xml/>", IDEMPOTENCY_KEY))
        .isInstanceOf(HttpClientErrorException.class);
    mockServer.verify();
  }

  @Test
  void submitPaymentFile_retriesOnConnectionError() {
    mockServer
        .expect(times(2), requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new java.net.ConnectException("simulated connection reset");
            });
    expectCall().andRespond(withSuccess("<ok/>", null));

    String result = sebGatewayClient.submitPaymentFile("<xml/>", IDEMPOTENCY_KEY);

    assertThat(result).isEqualTo("<ok/>");
    mockServer.verify();
  }

  private org.springframework.test.web.client.ResponseActions expectCall() {
    return mockServer
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY));
  }

  @Configuration
  static class TestConfig {

    @Bean
    RestClient.Builder sebGatewayRestClientBuilder() {
      return RestClient.builder().baseUrl("http://seb-gateway.local");
    }

    @Bean
    MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
      return MockRestServiceServer.bindTo(builder).build();
    }

    @Bean
    RestClient sebGatewayRestClient(RestClient.Builder builder, MockRestServiceServer mockServer) {
      return builder.build();
    }

    @Bean
    SebHttpSignature sebHttpSignature() {
      var signature = mock(SebHttpSignature.class);
      given(signature.createDigest(any())).willReturn("SHA-256=stub-digest");
      given(signature.createSignature(any())).willReturn("stub-signature");
      return signature;
    }

    @Bean
    RetryTemplate sebGatewayRetryTemplate() {
      var policy =
          RetryPolicy.builder()
              .includes(HttpServerErrorException.class, ResourceAccessException.class)
              .excludes(HttpClientErrorException.class)
              .maxRetries(7)
              .delay(Duration.ofMillis(1))
              .multiplier(1)
              .maxDelay(Duration.ofMillis(1))
              .build();
      return new RetryTemplate(policy);
    }

    @Bean
    SebGatewayClient sebGatewayClient(
        RestClient sebGatewayRestClient,
        SebHttpSignature signature,
        RetryTemplate sebGatewayRetryTemplate) {
      return new SebGatewayClient(sebGatewayRestClient, signature, sebGatewayRetryTemplate);
    }
  }
}
