package ee.tuleva.onboarding.banking.seb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest
@Import(SebGatewayClientTest.TestConfig.class)
class SebGatewayClientTest {

  @Autowired private SebGatewayClient client;
  @Autowired private MockRestServiceServer server;

  private static final String IBAN = "EE123456789012345678";

  @Test
  void getTransactions_includesSizeParameter() {
    LocalDate dateFrom = LocalDate.of(2026, 1, 1);
    LocalDate dateTo = LocalDate.of(2026, 1, 31);

    server
        .expect(
            requestTo(
                "/v1/accounts/"
                    + IBAN
                    + "/transactions?from=2026-01-01&to=2026-01-31&page=1&size=3000"))
        .andRespond(withSuccess("<xml/>", MediaType.APPLICATION_XML));

    String result = client.getTransactions(IBAN, dateFrom, dateTo);

    assertThat(result).isEqualTo("<xml/>");
    server.verify();
  }

  @Test
  void getEodTransactions_callsCorrectEndpoint() {
    server
        .expect(requestTo("/v1/accounts/" + IBAN + "/eod-transactions"))
        .andRespond(withSuccess("<eod/>", MediaType.APPLICATION_XML));

    String result = client.getEodTransactions(IBAN);

    assertThat(result).isEqualTo("<eod/>");
    server.verify();
  }

  @Test
  void getCurrentTransactions_callsCorrectEndpoint() {
    server
        .expect(requestTo("/v1/accounts/" + IBAN + "/current-transactions?page=1&size=3000"))
        .andRespond(withSuccess("<current/>", MediaType.APPLICATION_XML));

    String result = client.getCurrentTransactions(IBAN);

    assertThat(result).isEqualTo("<current/>");
    server.verify();
  }

  @Test
  void getBalances_callsCorrectEndpoint() {
    server
        .expect(requestTo("/v1/accounts/" + IBAN + "/balances"))
        .andRespond(withSuccess("<balances/>", MediaType.APPLICATION_XML));

    String result = client.getBalances(IBAN);

    assertThat(result).isEqualTo("<balances/>");
    server.verify();
  }

  @Configuration
  static class TestConfig {
    @Bean
    SebGatewayClient sebGatewayClient(RestClient.Builder restClientBuilder) {
      return new SebGatewayClient(restClientBuilder.build(), null);
    }
  }
}
