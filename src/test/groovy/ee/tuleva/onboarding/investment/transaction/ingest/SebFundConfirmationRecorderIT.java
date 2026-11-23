package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationAuditRecorder.REPLACED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class SebFundConfirmationRecorderIT {

  private static final String REFERENCE = "DLA0000001";
  private static final String ISIN = "IE00BKPTWY98";
  private static final String FIRST_KEY = "seb-confirmations/DLA0000001-001.pdf";
  private static final String RESENT_KEY = "seb-confirmations/DLA0000001-002.pdf";
  private static final Instant RECEIVED_AT = Instant.parse("2026-01-05T09:48:17Z");
  private static final Instant FIRST_READ = Instant.parse("2026-01-08T10:00:00Z");
  private static final Instant RESENT_READ = Instant.parse("2026-01-09T10:00:00Z");
  private static final LocalDate PRICING_DATE = LocalDate.of(2026, 1, 6);

  @Autowired private JdbcClient jdbcClient;
  @Autowired private TransactionAuditEventRepository auditEventRepository;

  @Test
  void record_keepsTheConfirmationsPricingDateUnderItsTradeReference() {
    recorderAt(FIRST_READ).record(upload(FIRST_KEY, PRICING_DATE));

    assertThat(repository().findByTradeReference(REFERENCE))
        .contains(
            new RecordedSebFundConfirmation(
                REFERENCE, TUK75, ISIN, BUY, RECEIVED_AT, PRICING_DATE, FIRST_KEY));
    assertThat(recordedAndChangedAt()).containsExactly(FIRST_READ, FIRST_READ);
  }

  @Test
  void record_leavesTheRowAsItIsWhenSebResendsTheSameConfirmation() {
    recorderAt(FIRST_READ).record(upload(FIRST_KEY, PRICING_DATE));

    recorderAt(RESENT_READ).record(upload(RESENT_KEY, PRICING_DATE));

    assertThat(repository().findByTradeReference(REFERENCE))
        .hasValueSatisfying(recorded -> assertThat(recorded.sourceFileKey()).isEqualTo(FIRST_KEY));
    assertThat(recordedAndChangedAt()).containsExactly(FIRST_READ, FIRST_READ);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(REPLACED, REFERENCE)).isEmpty();
  }

  @Test
  void record_keepsTheLatestConfirmationAndRecordsTheOneItReplaced() {
    LocalDate correctedPricingDate = PRICING_DATE.plusDays(1);
    recorderAt(FIRST_READ).record(upload(FIRST_KEY, PRICING_DATE));

    recorderAt(RESENT_READ).record(upload(RESENT_KEY, correctedPricingDate));

    assertThat(repository().findByTradeReference(REFERENCE))
        .contains(
            new RecordedSebFundConfirmation(
                REFERENCE, TUK75, ISIN, BUY, RECEIVED_AT, correctedPricingDate, RESENT_KEY));
    assertThat(recordedAndChangedAt()).containsExactly(FIRST_READ, RESENT_READ);
    assertThat(auditEventRepository.findByEventTypeAndDedupKey(REPLACED, REFERENCE))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getCreatedAt()).isEqualTo(RESENT_READ);
              assertThat(event.getPayload())
                  .containsEntry("pricingDate", "2026-01-07")
                  .containsEntry("sourceFileKey", RESENT_KEY)
                  .containsEntry(
                      "previous",
                      Map.of(
                          "fund", "TUK75",
                          "isin", ISIN,
                          "side", "BUY",
                          "orderReceivedAt", "2026-01-05T09:48:17Z",
                          "pricingDate", "2026-01-06",
                          "sourceFileKey", FIRST_KEY));
            });
  }

  private SebFundConfirmationRecorder recorderAt(Instant now) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return new SebFundConfirmationRecorder(
        repository(), new SebFundConfirmationAuditRecorder(auditEventRepository, clock), clock);
  }

  private SebFundConfirmationRepository repository() {
    return new SebFundConfirmationRepository(jdbcClient);
  }

  private List<Instant> recordedAndChangedAt() {
    return jdbcClient
        .sql(
            """
            SELECT created_at, updated_at FROM investment_seb_fund_confirmation
            WHERE trade_reference = :reference
            """)
        .param("reference", REFERENCE)
        .query(
            (rs, rowNum) ->
                List.of(
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()))
        .single();
  }

  private static SebFundConfirmationUpload upload(String key, LocalDate pricingDate) {
    return new SebFundConfirmationUpload(
        key,
        new SebFundConfirmation(
            TUK75,
            REFERENCE,
            RECEIVED_AT,
            pricingDate,
            LocalDate.of(2026, 1, 9),
            ISIN,
            BUY,
            new BigDecimal("1000.00"),
            new BigDecimal("12.4"),
            new BigDecimal("12400.00"),
            new BigDecimal("0.00"),
            new BigDecimal("12400.00")));
  }
}
