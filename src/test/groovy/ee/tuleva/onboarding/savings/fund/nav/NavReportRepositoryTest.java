package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class NavReportRepositoryTest {

  @Autowired private NavReportRepository navReportRepository;

  @Test
  void savesRowWithoutExplicitCreatedAt() {
    var row =
        NavReportRow.builder()
            .navDate(LocalDate.of(2026, 3, 17))
            .fundCode("TKF100")
            .accountType("CASH")
            .accountName("Cash account in SEB Pank")
            .accountId("EE0000003283")
            .quantity(new BigDecimal("100.00"))
            .marketPrice(new BigDecimal("1.00"))
            .marketValue(new BigDecimal("100.00"))
            .calculationId(UUID.randomUUID())
            .build();

    var saved = navReportRepository.save(row);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCurrency()).isEqualTo(EUR);
  }

  @Test
  void replaceByNavDateAndFundCode_overwritesUnpublishedRows() {
    var navDate = LocalDate.of(2026, 4, 22);
    var calcId1 = UUID.randomUUID();
    var first =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode("TUK75")
            .accountType("SECURITY")
            .accountName("iShares Developed World Screened Index Fund")
            .accountId("IE00BFG1TM61")
            .quantity(new BigDecimal("8132235.65"))
            .marketPrice(new BigDecimal("35.15467"))
            .marketValue(new BigDecimal("285886060.64"))
            .calculationId(calcId1)
            .build();

    navReportRepository.replaceByNavDateAndFundCode(navDate, "TUK75", List.of(first));

    var calcId2 = UUID.randomUUID();
    var second =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode("TUK75")
            .accountType("SECURITY")
            .accountName("iShares Developed World Screened Index Fund")
            .accountId("IE00BFG1TM61")
            .quantity(new BigDecimal("8132235.65"))
            .marketPrice(new BigDecimal("35.47140"))
            .marketValue(new BigDecimal("288461783.64"))
            .calculationId(calcId2)
            .build();

    navReportRepository.replaceByNavDateAndFundCode(navDate, "TUK75", List.of(second));

    var rows = navReportRepository.findLatestByNavDateAndFundCode(navDate, "TUK75");
    assertThat(rows)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.getMarketPrice()).isEqualByComparingTo("35.47140");
              assertThat(r.getMarketValue()).isEqualByComparingTo("288461783.64");
              assertThat(r.getCalculationId()).isEqualTo(calcId2);
            });
  }

  @Test
  void replaceByNavDateAndFundCode_preservesPublishedRows() {
    var navDate = LocalDate.of(2026, 4, 22);
    var calcId1 = UUID.randomUUID();
    var published =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode("TKF100")
            .accountType("SECURITY")
            .accountName("iShares Fund")
            .accountId("IE00BFG1TM61")
            .quantity(new BigDecimal("100.00"))
            .marketPrice(new BigDecimal("10.00"))
            .marketValue(new BigDecimal("1000.00"))
            .calculationId(calcId1)
            .build();

    navReportRepository.replaceByNavDateAndFundCode(navDate, "TKF100", List.of(published));
    navReportRepository.markAsPublished(calcId1);

    var calcId2 = UUID.randomUUID();
    var recalculated =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode("TKF100")
            .accountType("SECURITY")
            .accountName("iShares Fund")
            .accountId("IE00BFG1TM61")
            .quantity(new BigDecimal("100.00"))
            .marketPrice(new BigDecimal("11.00"))
            .marketValue(new BigDecimal("1100.00"))
            .calculationId(calcId2)
            .build();

    navReportRepository.replaceByNavDateAndFundCode(navDate, "TKF100", List.of(recalculated));

    var latestRows = navReportRepository.findLatestByNavDateAndFundCode(navDate, "TKF100");
    assertThat(latestRows)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.getCalculationId()).isEqualTo(calcId2);
              assertThat(r.getMarketPrice()).isEqualByComparingTo("11.00");
            });

    var allRows = navReportRepository.findAll();
    var tkf100Rows =
        allRows.stream()
            .filter(r -> "TKF100".equals(r.getFundCode()) && navDate.equals(r.getNavDate()))
            .toList();
    assertThat(tkf100Rows).hasSize(2);
  }

  @Test
  void markAsPublished_setsPublishedAt() {
    var navDate = LocalDate.of(2026, 4, 22);
    var calcId = UUID.randomUUID();
    var row =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode("TKF100")
            .accountType("CASH")
            .accountName("Cash")
            .quantity(new BigDecimal("100.00"))
            .marketPrice(new BigDecimal("1.00"))
            .marketValue(new BigDecimal("100.00"))
            .calculationId(calcId)
            .build();

    navReportRepository.save(row);
    assertThat(row.getPublishedAt()).isNull();

    navReportRepository.markAsPublished(calcId);

    var found = navReportRepository.findLatestByNavDateAndFundCode(navDate, "TKF100");
    assertThat(found).singleElement().satisfies(r -> assertThat(r.getPublishedAt()).isNotNull());
  }
}
