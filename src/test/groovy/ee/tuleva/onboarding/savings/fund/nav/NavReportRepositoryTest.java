package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
