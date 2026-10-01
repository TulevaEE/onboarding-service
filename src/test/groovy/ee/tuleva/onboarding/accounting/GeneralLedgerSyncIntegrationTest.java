package ee.tuleva.onboarding.accounting;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.accounting.directo.DirectoStackConfiguration;
import ee.tuleva.onboarding.ledger.GeneralLedger;
import ee.tuleva.onboarding.ledger.GeneralLedgerRows;
import ee.tuleva.onboarding.ledger.GeneralLedgerRows.JournalEntryVersion;
import ee.tuleva.onboarding.ledger.GeneralLedgerStackConfiguration;
import ee.tuleva.onboarding.ledger.MirrorResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureRestClient
@AutoConfigureMockRestServiceServer
@Import({
  GeneralLedgerStackConfiguration.class,
  DirectoStackConfiguration.class,
  GeneralLedgerSync.class
})
@TestPropertySource(
    properties = {
      "directo.url=https://directo.test/apidirect",
      "directo.api-keys[TULEVA_FONDID]=integration-test-key",
      "directo.retry-delay=1ms",
      "accounting.max-deletion-share=0.05"
    })
@Transactional(propagation = NOT_SUPPORTED)
class GeneralLedgerSyncIntegrationTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String SOURCE = "DIRECTO";
  private static final String BASE_URL = "https://directo.test/apidirect/v1";
  private static final List<String> CHART =
      List.of("100100", "200100", "212301", "300100", "400100", "500100", "540100", "900000");

  @Autowired GeneralLedgerSync sync;
  @Autowired GeneralLedger generalLedger;
  @Autowired GeneralLedgerRows rows;
  @Autowired MockRestServiceServer server;
  @Autowired JdbcClient jdbcClient;

  @AfterEach
  void tearDown() {
    rows.deleteAll();
  }

  @Test
  void everyEntityWithAConfiguredKeyIsSynced() {
    assertThat(sync.entities()).containsExactly(ENTITY);
  }

  @Test
  void syncMakesEveryMonthEndBalanceEqualTheAccountingSystem() {
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS);

    var result = sync.sync(ENTITY);

    assertThat(result).isEqualTo(new MirrorResult(8, 0, 0, 0, 0, 0));
    assertThat(balancesAt("2026-01-31"))
        .isEqualTo(
            expected(
                "-1250.00", "0.00", "-2500.00", "-1000.00", "-250.00", "0.00", "5000.00", "0.00"));
    assertThat(balancesAt("2026-02-28"))
        .isEqualTo(
            expected(
                "-1342.50", "0.00", "-2500.00", "-1000.00", "-250.00", "92.50", "5000.00", "0.00"));
    assertThat(balancesAt("2026-03-31"))
        .isEqualTo(
            expected(
                "-1300.00",
                "0.00",
                "-2500.00",
                "-1000.00",
                "-250.00",
                "90.00",
                "5000.00",
                "-40.00"));
  }

  @Test
  void payrollDocumentsLandAsOneMonthlySummaryWithoutPerPersonLines() {
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS);

    sync.sync(ENTITY);

    assertThat(generalLedger.liveSourceKeys(ENTITY, SOURCE))
        .isEqualTo(
            Set.of(
                "FIN:1:2026-01-05",
                "ARVE:1:2026-01-31",
                "PAYROLL:2026-01",
                "OST:7:2026-02-01",
                "FIN:2:2026-02-27",
                "PEAKIRI:3:2026-02-27",
                "PEAKIRI:3:2026-03-02",
                "PEAKIRI:5:2026-03-31"));
    assertThat(rows.versionsOf(ENTITY, SOURCE, "PAYROLL:2026-01"))
        .extracting(GeneralLedgerSyncIntegrationTest::bookingDate, JournalEntryVersion::live)
        .containsExactly(tuple(LocalDate.parse("2026-01-31"), true));
    assertThat(storedTextsContaining("E00")).isZero();
    assertThat(storedTextsContaining("Synthetic Employee")).isZero();
    assertThat(storedTextsContaining("TEAM1")).isZero();
    assertThat(storedTextsContaining("40015")).isZero();
    assertThat(storedTextsContaining("INV-7")).isZero();
    assertThat(storedTextsContaining("Opening")).isZero();
  }

  @Test
  void aDocumentWithRowsInTwoMonthsLandsInBothMonths() {
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS);

    sync.sync(ENTITY);

    assertThat(rows.versionsOf(ENTITY, SOURCE, "PEAKIRI:3:2026-02-27"))
        .extracting(GeneralLedgerSyncIntegrationTest::bookingDate)
        .containsExactly(LocalDate.parse("2026-02-27"));
    assertThat(rows.versionsOf(ENTITY, SOURCE, "PEAKIRI:3:2026-03-02"))
        .extracting(GeneralLedgerSyncIntegrationTest::bookingDate)
        .containsExactly(LocalDate.parse("2026-03-02"));
    assertThat(balanceAt("500100", "2026-02-28")).isEqualTo(new BigDecimal("92.50"));
    assertThat(balanceAt("500100", "2026-03-31")).isEqualTo(new BigDecimal("90.00"));
  }

  @Test
  void anUnparseableDocumentStopsTheEntityWithNothingWritten() {
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS_WITH_A_ROW_WITHOUT_AN_ACCOUNT);

    assertThatThrownBy(() -> sync.sync(ENTITY)).isInstanceOf(IllegalArgumentException.class);

    assertThat(rows.count(JOURNAL_ENTRY)).isZero();
    assertThat(generalLedgerAccountCount()).isZero();
  }

  @Test
  void aPreviouslyPostedPartThatBecomesProtectedIsReversedAndSummarized() {
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS);
    sync.sync(ENTITY);
    server.reset();
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS_WITH_THE_PURCHASE_RECODED_TO_AN_EMPLOYEE);

    var result = sync.sync(ENTITY);

    assertThat(result).isEqualTo(new MirrorResult(1, 0, 1, 7, 0, 0));
    assertThat(rows.versionsOf(ENTITY, SOURCE, "OST:7:2026-02-01"))
        .extracting(JournalEntryVersion::live)
        .containsExactly(false);
    assertThat(generalLedger.liveSourceKeys(ENTITY, SOURCE)).contains("PAYROLL:2026-02");
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isEqualTo(1);
    assertThat(balancesAt("2026-02-28"))
        .isEqualTo(
            expected(
                "-1342.50", "0.00", "-2500.00", "-1000.00", "-250.00", "92.50", "5000.00", "0.00"));
    assertThat(storedTextsContaining("E00")).isZero();
  }

  @Test
  void aPartThatBecomesProtectedStaysLiveUntilItsMonthlySummaryCanPost() {
    respondWith(ACCOUNTS, OBJECTS, TRANSACTIONS);
    sync.sync(ENTITY);
    server.reset();
    respondWith(
        ACCOUNTS,
        OBJECTS,
        withDocument(
            TRANSACTIONS_WITH_THE_PURCHASE_RECODED_TO_AN_EMPLOYEE,
            A_FEBRUARY_PAYROLL_ON_AN_ACCOUNT_MISSING_FROM_THE_CHART));

    var whileTheSummaryIsQuarantined = sync.sync(ENTITY);

    assertThat(whileTheSummaryIsQuarantined).isEqualTo(new MirrorResult(0, 0, 0, 7, 1, 0));
    assertThat(rows.versionsOf(ENTITY, SOURCE, "OST:7:2026-02-01"))
        .extracting(JournalEntryVersion::live)
        .containsExactly(true);
    assertThat(balancesAt("2026-02-28"))
        .isEqualTo(
            expected(
                "-1342.50", "0.00", "-2500.00", "-1000.00", "-250.00", "92.50", "5000.00", "0.00"));

    server.reset();
    respondWith(
        ACCOUNTS,
        OBJECTS,
        withDocument(
            TRANSACTIONS_WITH_THE_PURCHASE_RECODED_TO_AN_EMPLOYEE,
            A_FEBRUARY_PAYROLL_ON_AN_ACCOUNT_MISSING_FROM_THE_CHART.replace("549999", "540100")));

    var onceTheSummaryPosts = sync.sync(ENTITY);

    assertThat(onceTheSummaryPosts).isEqualTo(new MirrorResult(1, 0, 1, 7, 0, 0));
    assertThat(rows.versionsOf(ENTITY, SOURCE, "OST:7:2026-02-01"))
        .extracting(JournalEntryVersion::live)
        .containsExactly(false);
    assertThat(balancesAt("2026-02-28"))
        .isEqualTo(
            expected(
                "-1342.50", "0.00", "-2600.00", "-1000.00", "-250.00", "92.50", "5100.00", "0.00"));
  }

  private void respondWith(String accounts, String objects, String transactions) {
    server
        .expect(requestTo(BASE_URL + "/accounts"))
        .andExpect(header("X-Directo-Key", "integration-test-key"))
        .andRespond(withSuccess(accounts, APPLICATION_JSON));
    server
        .expect(requestTo(BASE_URL + "/objects"))
        .andExpect(header("X-Directo-Key", "integration-test-key"))
        .andRespond(withSuccess(objects, APPLICATION_JSON));
    server
        .expect(requestTo(BASE_URL + "/transactions?date=%3E2015-12-31T23:59:59"))
        .andExpect(header("X-Directo-Key", "integration-test-key"))
        .andRespond(withSuccess(transactions, APPLICATION_JSON));
  }

  private static String withDocument(String transactions, String document) {
    return "[" + document + "," + transactions.strip().substring(1);
  }

  private static LocalDate bookingDate(JournalEntryVersion version) {
    return version.transactionDate().atZone(ZoneId.of("Europe/Tallinn")).toLocalDate();
  }

  private BigDecimal balanceAt(String code, String date) {
    return rows.balanceAtEndOf(ENTITY, code, LocalDate.parse(date));
  }

  private Map<String, BigDecimal> balancesAt(String date) {
    return CHART.stream().collect(toMap(code -> code, code -> balanceAt(code, date)));
  }

  private static Map<String, BigDecimal> expected(String... balancesInChartOrder) {
    return CHART.stream()
        .collect(
            toMap(code -> code, code -> new BigDecimal(balancesInChartOrder[CHART.indexOf(code)])));
  }

  private long storedTextsContaining(String text) {
    return jdbcClient
        .sql(
            """
            SELECT (SELECT count(*) FROM ledger.transaction
                    WHERE CAST(metadata AS VARCHAR) LIKE :pattern)
                 + (SELECT count(*) FROM ledger.account
                    WHERE name LIKE :pattern OR CAST(metadata AS VARCHAR) LIKE :pattern)
            """)
        .param("pattern", "%" + text + "%")
        .query(Long.class)
        .single();
  }

  private long generalLedgerAccountCount() {
    return jdbcClient
        .sql("SELECT count(*) FROM ledger.account WHERE name LIKE 'GENERAL\\_LEDGER:%' ESCAPE '\\'")
        .query(Long.class)
        .single();
  }

  private static final String ACCOUNTS =
      """
      [
        {"code": "100100", "name": "Bank account", "class": "0", "correspondancecode": "",
         "datafields": [{"code": "LISANIMI", "param": "ENG", "content": "Bank account"},
                        {"code": "RV_OTSE", "param": "", "content": "CASH"}]},
        {"code": "200100", "name": "Trade payables", "class": "1", "datafields": []},
        {"code": "212301", "name": "Võlad töötajatele", "class": "1", "datafields": []},
        {"code": "300100", "name": "Share capital", "class": "2", "datafields": []},
        {"code": "400100", "name": "Management fee income", "class": "3", "datafields": []},
        {"code": "500100", "name": "Office rent", "class": "4", "datafields": []},
        {"code": "540100", "name": "Salaries", "class": "4", "datafields": []},
        {"code": "900000", "name": "Offsetting", "class": "5", "datafields": []}
      ]
      """;

  private static final String OBJECTS =
      """
      [
        {"code": "TEAM1", "name": "Team One", "type": "Tiim", "level": 10, "hierarchy": "TEAM1"},
        {"code": "40015", "name": "Synthetic Supplier OÜ", "type": "Hankija", "level": 40},
        {"code": "E001", "name": "Synthetic Employee One", "type": "Töötaja", "level": 50},
        {"code": "E002", "name": "Synthetic Employee Two", "type": "Töötaja", "level": 50}
      ]
      """;

  private static final String TRANSACTIONS =
      """
      [
        {"number": 1, "date": "2026-01-05T00:00:00", "type": "FIN", "comment": "Opening",
         "rows": [
           {"rn": 1, "account": "100100", "debitamount": 1000.0000, "creditamount": null},
           {"rn": 2, "account": "300100", "debitamount": null, "creditamount": 1000.0000}]},
        {"number": 1, "date": "2026-01-31T00:00:00", "type": "ARVE",
         "rows": [
           {"rn": 1, "account": "100100", "debitamount": 250.0000, "date": "2026-01-31T00:00:00"},
           {"rn": 2, "account": "400100", "creditamount": 250.0000, "date": "2026-01-31T00:00:00"},
           {"rn": 3, "account": "500100", "debitamount": 0.0000, "creditamount": 0.0000}]},
        {"number": 4, "date": "2026-01-31T00:00:00", "type": "PALK",
         "rows": [
           {"rn": 1, "account": "540100", "debitamount": 3000.0000, "object": "TEAM1,E001",
            "description": "Synthetic Employee One"},
           {"rn": 2, "account": "212301", "creditamount": 3000.0000, "object": "E001"},
           {"rn": 3, "account": "540100", "debitamount": 2000.0000, "object": "TEAM1, E002"},
           {"rn": 4, "account": "212301", "creditamount": 2000.0000, "object": "E002"}]},
        {"number": 9, "date": "2026-01-15T00:00:00", "type": "FIN",
         "rows": [
           {"rn": 1, "account": "212301", "debitamount": 2500.0000, "object": "E001"},
           {"rn": 2, "account": "100100", "creditamount": 2500.0000}]},
        {"number": 7, "date": "2026-02-01T00:00:00", "type": "OST", "reference": "INV-7",
         "rows": [
           {"rn": 1, "account": "500100", "debitamount": 80.0000, "supplier": "40015"},
           {"rn": 2, "account": "200100", "creditamount": 80.0000, "supplier": "40015"}]},
        {"number": 2, "date": "2026-02-27T00:00:00", "type": "FIN",
         "rows": [
           {"rn": 1, "account": "200100", "debitamount": 80.0000, "supplier": "40015"},
           {"rn": 2, "account": "100100", "creditamount": 80.0000, "currency": "USD",
            "currencycredit": 88.0000, "currencyrate": 1.1}]},
        {"number": 3, "date": "2026-02-27T00:00:00", "type": "PEAKIRI",
         "rows": [
           {"rn": 1, "account": "500100", "debitamount": 12.5000, "date": "2026-02-27T00:00:00"},
           {"rn": 2, "account": "100100", "creditamount": 12.5000, "date": "2026-02-27T00:00:00"},
           {"rn": 3, "account": "500100", "debitamount": -2.5000, "date": "2026-03-02T00:00:00"},
           {"rn": 4, "account": "100100", "creditamount": -2.5000, "date": "2026-03-02T00:00:00"}]},
        {"number": 5, "date": "2026-03-31T00:00:00", "type": "PEAKIRI",
         "rows": [
           {"rn": 1, "account": "100100", "debitamount": 40.0000},
           {"rn": 2, "account": "900000", "creditamount": 40.0000}]}
      ]
      """;

  private static final String TRANSACTIONS_WITH_A_ROW_WITHOUT_AN_ACCOUNT =
      """
      [
        {"number": 1, "date": "2026-01-05T00:00:00", "type": "FIN",
         "rows": [
           {"rn": 1, "account": "100100", "debitamount": 1000.0000},
           {"rn": 2, "account": "300100", "creditamount": 1000.0000}]},
        {"number": 2, "date": "2026-01-06T00:00:00", "type": "FIN",
         "rows": [
           {"rn": 1, "account": "100100", "debitamount": 5.0000},
           {"rn": 2, "creditamount": 5.0000}]}
      ]
      """;

  private static final String TRANSACTIONS_WITH_THE_PURCHASE_RECODED_TO_AN_EMPLOYEE =
      TRANSACTIONS.replace(
          "\"account\": \"500100\", \"debitamount\": 80.0000, \"supplier\": \"40015\"",
          "\"account\": \"500100\", \"debitamount\": 80.0000, \"supplier\": \"40015\","
              + " \"object\": \"E002\"");

  private static final String A_FEBRUARY_PAYROLL_ON_AN_ACCOUNT_MISSING_FROM_THE_CHART =
      """
      {"number": 6, "date": "2026-02-27T00:00:00", "type": "PALK",
       "rows": [
         {"rn": 1, "account": "549999", "debitamount": 100.0000, "object": "E001"},
         {"rn": 2, "account": "212301", "creditamount": 100.0000, "object": "E001"}]}
      """;
}
