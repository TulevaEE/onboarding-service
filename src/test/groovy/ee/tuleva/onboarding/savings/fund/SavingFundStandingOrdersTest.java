package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.ISSUED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.time.ClockConfig;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DataJpaTest
@Import({SavingFundStandingOrders.class, SavingFundPaymentRepository.class, ClockConfig.class})
class SavingFundStandingOrdersTest {

  private static final PartyId SAVER = new PartyId(PERSON, "38888888888");

  @Autowired SavingFundStandingOrders standingOrders;
  @Autowired SavingFundPaymentRepository repository;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void leavesOutOneOffPaymentsMadeThroughTheInAppLink() {
    issuedPayment("38888888888, 1781500000", "2026-06-15T10:00:00Z");
    issuedPayment("38888888888, 1784200000", "2026-07-15T10:00:00Z");
    issuedPayment("38888888888, 1786800000", "2026-08-15T10:00:00Z");

    assertThat(standingOrders.countMonthsSince(SAVER, LocalDate.parse("2026-06-01"))).isZero();
  }

  @Test
  void countsMonthsPaidWithTheSaversOwnReference() {
    issuedPayment("kogumisfond", "2026-06-15T10:00:00Z");
    issuedPayment("kogumisfond", "2026-07-15T10:00:00Z");
    issuedPayment("kogumisfond", "2026-08-15T10:00:00Z");

    assertThat(standingOrders.countMonthsSince(SAVER, LocalDate.parse("2026-06-01"))).isEqualTo(3);
  }

  @Test
  void countsAnInAppLinkReferenceCopiedIntoAStandingOrder() {
    issuedPayment("38888888888, 1781500000", "2026-06-15T10:00:00Z");
    issuedPayment("38888888888, 1781500000", "2026-07-15T10:00:00Z");
    issuedPayment("38888888888, 1781500000", "2026-08-15T10:00:00Z");

    assertThat(standingOrders.countMonthsSince(SAVER, LocalDate.parse("2026-06-01"))).isEqualTo(3);
  }

  private void issuedPayment(String description, String createdAt) {
    var id =
        repository.savePaymentData(
            SavingFundPayment.builder()
                .remitterName("John Doe")
                .remitterIdCode("12345")
                .remitterIban("IBAN-1")
                .beneficiaryName("Jane Smith")
                .beneficiaryIdCode("67890")
                .beneficiaryIban("IBAN-2")
                .amount(new BigDecimal("100.70"))
                .description(description)
                .externalId(UUID.randomUUID().toString())
                .build());
    repository.attachParty(id, SAVER);
    jdbcTemplate.update(
        "update saving_fund_payment set status=:status, created_at=:createdAt where id=:id",
        Map.of(
            "status", ISSUED.name(),
            "createdAt", Timestamp.from(Instant.parse(createdAt)),
            "id", id));
  }
}
