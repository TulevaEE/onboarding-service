package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
            .build();

    var saved = navReportRepository.save(row);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCurrency()).isEqualTo(EUR);
  }

  @Test
  void replaceByNavDateAndFundCode_overwritesExistingRowsWithLatestValues() {
    var navDate = LocalDate.of(2026, 4, 22);
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
            .build();

    navReportRepository.replaceByNavDateAndFundCode(navDate, "TUK75", List.of(first));

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
            .build();

    navReportRepository.replaceByNavDateAndFundCode(navDate, "TUK75", List.of(second));

    var rows = navReportRepository.findByNavDateAndFundCodeOrderById(navDate, "TUK75");
    assertThat(rows)
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.getMarketPrice()).isEqualByComparingTo("35.47140");
              assertThat(r.getMarketValue()).isEqualByComparingTo("288461783.64");
            });
  }
}
