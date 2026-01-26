package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AumRepositoryTest {

  @Autowired private AumRepository aumRepository;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM index_values WHERE key LIKE 'AUM_%'").update();
  }

  @Test
  void getAum_returnsValueForExactDate() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    BigDecimal expectedValue = new BigDecimal("1000000000");
    insertAumValue("AUM_EE3600109435", date, expectedValue);

    var result = aumRepository.getAum(TUK75, date);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo(expectedValue);
  }

  @Test
  void getAum_returnsMostRecentValueWhenExactDateMissing() {
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    BigDecimal fridayValue = new BigDecimal("500000000");
    insertAumValue("AUM_EE3600109435", friday, fridayValue);

    var result = aumRepository.getAum(TUK75, saturday);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo(fridayValue);
  }

  @Test
  void getAum_returnsEmptyWhenNoData() {
    var result = aumRepository.getAum(TUK75, LocalDate.of(2025, 1, 15));

    assertThat(result).isEmpty();
  }

  @Test
  void getTotalAum_sumAllFunds() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    insertAumValue("AUM_EE3600109435", date, new BigDecimal("1000000000")); // TUK75
    insertAumValue("AUM_EE3600109443", date, new BigDecimal("200000000")); // TUK00
    insertAumValue("AUM_EE3600001707", date, new BigDecimal("300000000")); // TUV100

    BigDecimal result = aumRepository.getTotalAum(date);

    assertThat(result).isEqualByComparingTo(new BigDecimal("1500000000"));
  }

  @Test
  void getTotalAum_treatsEmptyAsZero() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    insertAumValue("AUM_EE3600109435", date, new BigDecimal("1000000000")); // TUK75 only

    BigDecimal result = aumRepository.getTotalAum(date);

    assertThat(result).isEqualByComparingTo(new BigDecimal("1000000000"));
  }

  @Test
  void getLastAumDateInMonth_returnsLastDateWithData() {
    LocalDate monthStart = LocalDate.of(2025, 1, 1);
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 15), BigDecimal.ONE);
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 30), BigDecimal.ONE);
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 31), BigDecimal.ONE);

    LocalDate result = aumRepository.getLastAumDateInMonth(monthStart);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 31));
  }

  @Test
  void getLastAumDateInMonth_returnsNullWhenNoData() {
    LocalDate monthStart = LocalDate.of(2025, 1, 1);

    LocalDate result = aumRepository.getLastAumDateInMonth(monthStart);

    assertThat(result).isNull();
  }

  @Test
  void getLastAumDateInMonth_excludesDataFromOtherMonths() {
    LocalDate januaryStart = LocalDate.of(2025, 1, 1);
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 15), BigDecimal.ONE);
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 2, 1), BigDecimal.ONE);

    LocalDate result = aumRepository.getLastAumDateInMonth(januaryStart);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 15));
  }

  @Test
  void getAumReferenceDate_returnsExactDate() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    insertAumValue("AUM_EE3600109435", date, BigDecimal.ONE);

    LocalDate result = aumRepository.getAumReferenceDate(TUK75, date);

    assertThat(result).isEqualTo(date);
  }

  @Test
  void getAumReferenceDate_returnsPreviousDateForWeekend() {
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    insertAumValue("AUM_EE3600109435", friday, BigDecimal.ONE);

    LocalDate result = aumRepository.getAumReferenceDate(TUK75, saturday);

    assertThat(result).isEqualTo(friday);
  }

  @Test
  void getAumReferenceDate_returnsNullWhenNoData() {
    LocalDate result = aumRepository.getAumReferenceDate(TUK75, LocalDate.of(2025, 1, 15));

    assertThat(result).isNull();
  }

  @Test
  void getHistoricalMaxTotalAum_returnsMaxAcrossAllDates() {
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 10), new BigDecimal("1000000000"));
    insertAumValue("AUM_EE3600109443", LocalDate.of(2025, 1, 10), new BigDecimal("100000000"));
    insertAumValue("AUM_EE3600001707", LocalDate.of(2025, 1, 10), new BigDecimal("300000000"));

    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 15), new BigDecimal("1100000000"));
    insertAumValue("AUM_EE3600109443", LocalDate.of(2025, 1, 15), new BigDecimal("110000000"));
    insertAumValue("AUM_EE3600001707", LocalDate.of(2025, 1, 15), new BigDecimal("350000000"));

    BigDecimal result = aumRepository.getHistoricalMaxTotalAum(LocalDate.of(2025, 1, 31));

    assertThat(result).isEqualByComparingTo(new BigDecimal("1560000000"));
  }

  @Test
  void getHistoricalMaxTotalAum_returnsHistoricalMaxEvenWhenCurrentAumDrops() {
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 10), new BigDecimal("1500000000"));
    insertAumValue("AUM_EE3600109443", LocalDate.of(2025, 1, 10), new BigDecimal("200000000"));
    insertAumValue("AUM_EE3600001707", LocalDate.of(2025, 1, 10), new BigDecimal("400000000"));

    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 20), new BigDecimal("1000000000"));
    insertAumValue("AUM_EE3600109443", LocalDate.of(2025, 1, 20), new BigDecimal("100000000"));
    insertAumValue("AUM_EE3600001707", LocalDate.of(2025, 1, 20), new BigDecimal("200000000"));

    BigDecimal result = aumRepository.getHistoricalMaxTotalAum(LocalDate.of(2025, 1, 31));

    assertThat(result).isEqualByComparingTo(new BigDecimal("2100000000"));
  }

  @Test
  void getHistoricalMaxTotalAum_excludesFutureDates() {
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 1, 15), new BigDecimal("1000000000"));
    insertAumValue("AUM_EE3600109435", LocalDate.of(2025, 2, 15), new BigDecimal("2000000000"));

    BigDecimal result = aumRepository.getHistoricalMaxTotalAum(LocalDate.of(2025, 1, 31));

    assertThat(result).isEqualByComparingTo(new BigDecimal("1000000000"));
  }

  @Test
  void getHistoricalMaxTotalAum_returnsZeroWhenNoData() {
    BigDecimal result = aumRepository.getHistoricalMaxTotalAum(LocalDate.of(2025, 1, 31));

    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private void insertAumValue(String key, LocalDate date, BigDecimal value) {
    jdbcClient
        .sql(
            """
            MERGE INTO index_values (key, date, value, provider, updated_at)
            KEY (key, date)
            VALUES (:key, :date, :value, 'TEST', now())
            """)
        .param("key", key)
        .param("date", date)
        .param("value", value)
        .update();
  }
}
