package ee.tuleva.onboarding.accounting.directo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest
@Import(DirectoConfiguration.class)
@TestPropertySource(
    properties = {
      "directo.url=https://directo.test/apidirect",
      "directo.api-keys[TULEVA_FONDID]=client-test-key",
      "directo.retry-delay=1ms"
    })
class DirectoClientTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String KEY = "client-test-key";
  private static final String ACCOUNTS_URL = "https://directo.test/apidirect/v1/accounts";
  private static final String OBJECTS_URL = "https://directo.test/apidirect/v1/objects";
  private static final String TRANSACTIONS_URL =
      "https://directo.test/apidirect/v1/transactions?date=%3E2015-12-31T23:59:59";

  @Autowired DirectoClient client;
  @Autowired MockRestServiceServer server;

  @Test
  void sendsTheKeyInTheHeaderNotTheUrl() {
    expectSuccess(ACCOUNTS_URL, "[]");
    expectSuccess(OBJECTS_URL, "[]");
    expectSuccess(TRANSACTIONS_URL, "[]");

    var book = client.fetchBook(ENTITY);

    assertThat(book).isEqualTo(new DirectoBook(List.of(), List.of(), List.of()));
    server.verify();
  }

  @Test
  void fetchesTheWholeHistoryInOneRequest() {
    expectSuccess(ACCOUNTS_URL, ACCOUNTS);
    expectSuccess(OBJECTS_URL, OBJECTS);
    server
        .expect(once(), requestTo(TRANSACTIONS_URL))
        .andExpect(header("X-Directo-Key", KEY))
        .andRespond(withSuccess(TRANSACTIONS, APPLICATION_JSON));

    var book = client.fetchBook(ENTITY);

    assertThat(book)
        .isEqualTo(
            new DirectoBook(
                List.of(
                    new DirectoAccount(
                        "100100",
                        "Bank account",
                        "0",
                        "",
                        List.of(
                            new DirectoDatafield("LISANIMI", "ENG", "Bank account"),
                            new DirectoDatafield("RV_OTSE", null, "CASH"))),
                    new DirectoAccount("212301", "Võlad töötajatele", "1", null, null)),
                List.of(new DirectoObject("40015", "40"), new DirectoObject("E001", "50")),
                List.of(
                    new DirectoTransaction(
                        "OST",
                        "7",
                        "2026-02-01T00:00:00",
                        List.of(
                            new DirectoRow(
                                "500100",
                                new BigDecimal("80.0000"),
                                null,
                                "TEAM1,E001",
                                "PRJ1",
                                "40015",
                                null,
                                "2026-02-01T00:00:00"),
                            new DirectoRow(
                                "212301",
                                null,
                                new BigDecimal("80.0000"),
                                null,
                                null,
                                null,
                                "C9",
                                null))))));
    server.verify();
  }

  @Test
  void retriesServerErrorsAndTimeouts() {
    server
        .expect(requestTo(ACCOUNTS_URL))
        .andRespond(withStatus(SERVICE_UNAVAILABLE).body("{\"message\":\"try later\"}"));
    server
        .expect(requestTo(ACCOUNTS_URL))
        .andRespond(withException(new IOException("connection reset")));
    expectSuccess(ACCOUNTS_URL, "[]");
    expectSuccess(OBJECTS_URL, "[]");
    expectSuccess(TRANSACTIONS_URL, "[]");

    var book = client.fetchBook(ENTITY);

    assertThat(book).isEqualTo(new DirectoBook(List.of(), List.of(), List.of()));
    server.verify();
  }

  @Test
  void everyClientFailureRaisesStatusAndResourceOnly() {
    server
        .expect(once(), requestTo(ACCOUNTS_URL))
        .andRespond(withStatus(BAD_REQUEST).body("{\"message\":\"secret account detail\"}"));
    assertFailure("accounts", 400, null);

    server.reset();
    expectSuccess(ACCOUNTS_URL, "[]");
    server
        .expect(times(3), requestTo(OBJECTS_URL))
        .andRespond(withStatus(INTERNAL_SERVER_ERROR).body("secret object detail"));
    assertFailure("objects", 500, null);

    server.reset();
    expectSuccess(ACCOUNTS_URL, "[]");
    expectSuccess(OBJECTS_URL, "[]");
    expectSuccess(TRANSACTIONS_URL, "[{\"number\": \"secret row detail\", \"rows\": {");
    assertFailure("transactions", null, "MismatchedInputException");

    server.reset();
    server
        .expect(times(3), requestTo(ACCOUNTS_URL))
        .andRespond(withException(new IOException("secret socket detail")));
    assertFailure("accounts", null, "IOException");
  }

  private void assertFailure(
      String resource, @Nullable Integer status, @Nullable String errorType) {
    var failure =
        catchThrowableOfType(DirectoRequestException.class, () -> client.fetchBook(ENTITY));

    assertThat(failure.getFailure())
        .isEqualTo(new DirectoRequestException.Failure(resource, status, errorType));
    assertThat(failure.getCause()).isNull();
    assertThat(failure.getSuppressed()).isEmpty();
    server.verify();
  }

  private void expectSuccess(String url, String body) {
    server
        .expect(requestTo(url))
        .andExpect(header("X-Directo-Key", KEY))
        .andRespond(withSuccess(body, APPLICATION_JSON));
  }

  private static final String ACCOUNTS =
      """
      [
        {"code": "100100", "name": "Bank account", "class": 0, "correspondancecode": "",
         "objects": "TEAM",
         "datafields": [{"code": "LISANIMI", "param": "ENG", "content": "Bank account"},
                        {"code": "RV_OTSE", "content": "CASH"}]},
        {"code": "212301", "name": "Võlad töötajatele", "class": "1"}
      ]
      """;

  private static final String OBJECTS =
      """
      [
        {"code": "40015", "name": "Synthetic Supplier OÜ", "type": "Hankija", "level": 40,
         "hierarchy": "40015"},
        {"code": "E001", "name": "Synthetic Employee One", "type": "Töötaja", "level": "50"}
      ]
      """;

  private static final String TRANSACTIONS =
      """
      [
        {"number": 7, "date": "2026-02-01T00:00:00", "type": "OST", "reference": "INV-7",
         "comment": "Synthetic Employee One expense", "ts": "2026-02-02 10:00:00", "cu": "U1",
         "rows": [
           {"rn": 1, "account": "500100", "debitamount": 80.0000, "creditamount": null,
            "object": "TEAM1,E001", "project": "PRJ1", "supplier": "40015",
            "description": "Synthetic free text", "vatcode": "22", "quantity": 1,
            "date": "2026-02-01T00:00:00"},
           {"rn": 2, "account": "212301", "creditamount": 80.0000, "customer": "C9",
            "currency": "USD", "currencycredit": 88.0000, "currencyrate": 1.1}]}
      ]
      """;
}
