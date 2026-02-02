package ee.tuleva.onboarding.banking.seb;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class SebGatewayClient {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final MediaType APPLICATION_XML_UTF8 = new MediaType("application", "xml", UTF_8);

  private final RestClient sebGatewayRestClient;
  private final SebHttpSignature sebHttpSignature;

  public String getEodTransactions(String iban) {
    log.info("Fetching EOD transactions: iban={}", iban);
    return sebGatewayRestClient
        .get()
        .uri("/v1/accounts/{iban}/eod-transactions", iban)
        .retrieve()
        .body(String.class);
  }

  public String getCurrentTransactions(String iban) {
    log.info("Fetching current day transactions: iban={}", iban);
    return sebGatewayRestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/v1/accounts/{iban}/current-transactions")
                    .queryParam("page", 1)
                    .queryParam("size", 3000)
                    .build(iban))
        .retrieve()
        .body(String.class);
  }

  public String getTransactions(String iban, LocalDate dateFrom, LocalDate dateTo) {
    log.info("Fetching transactions: iban={}, dateFrom={}, dateTo={}", iban, dateFrom, dateTo);
    return sebGatewayRestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/v1/accounts/{iban}/transactions")
                    .queryParam("from", dateFrom.format(DATE_FORMAT))
                    .queryParam("to", dateTo.format(DATE_FORMAT))
                    .queryParam("page", 1)
                    .queryParam("size", 3000)
                    .build(iban))
        .retrieve()
        .body(String.class);
  }

  public String getBalances(String iban) {
    log.info("Fetching balances: iban={}", iban);
    return sebGatewayRestClient
        .get()
        .uri("/v1/accounts/{iban}/balances", iban)
        .retrieve()
        .body(String.class);
  }

  public String submitPaymentFile(String paymentXml, String idempotencyKey) {
    log.info("Submitting payment file: idempotencyKey={}", idempotencyKey);
    byte[] body = paymentXml.getBytes(UTF_8);
    String digest = sebHttpSignature.createDigest(body);
    String signature = sebHttpSignature.createSignature(digest);

    log.info("Payment request: digest={}", digest);
    log.debug("Payment body ({} bytes): {}", body.length, paymentXml);

    return sebGatewayRestClient
        .post()
        .uri("/v1/imported-payment-files")
        .contentType(APPLICATION_XML_UTF8)
        .header("Idempotency-Key", idempotencyKey)
        .header("Digest", digest)
        .header("Signature", signature)
        .header("Name-Check-Preferred", "true")
        .body(body)
        .retrieve()
        .body(String.class);
  }
}
