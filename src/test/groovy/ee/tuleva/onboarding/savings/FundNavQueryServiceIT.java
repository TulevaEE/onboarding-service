package ee.tuleva.onboarding.savings;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;

import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(FundNavQueryService.class)
class FundNavQueryServiceIT {

  private static final LocalDate PREVIOUS_DAY = LocalDate.of(2026, 11, 20);
  private static final LocalDate HELD_DAY = LocalDate.of(2026, 11, 23);

  @Autowired private NavReportRepository navReportRepository;
  @Autowired private FundNavQueryService service;

  @Test
  void aNavHeldForReviewIsNotTheFundsNavUntilItIsReleased() {
    navReportRepository.markAsSent(calculation(PREVIOUS_DAY, "1.20000", "900000000.00"));
    var held = calculation(HELD_DAY, "1.23000", "922500000.00");
    navReportRepository.markAsPublished(held);

    assertThat(service.hasFinishedNavRun("TUK75", HELD_DAY)).isTrue();
    assertThat(service.findPublishedNavPerUnit("TUK75", HELD_DAY)).isEmpty();
    assertThat(service.findLatestPublishedNavDateOnOrBefore("TUK75", HELD_DAY))
        .hasValue(PREVIOUS_DAY);
    assertThat(service.findAum("TUK75", HELD_DAY)).isEqualByComparingTo("0");
    assertThat(service.findFeeBaseComponentTotal("TUK75", HELD_DAY)).isEmpty();
    assertThat(service.findPublishedCalculation("TUK75", HELD_DAY)).isEmpty();
    assertThat(service.findPublishedNavDatesBetween("TUK75", PREVIOUS_DAY, HELD_DAY))
        .containsExactly(PREVIOUS_DAY);

    navReportRepository.markAsSent(held);

    assertThat(service.findPublishedNavPerUnit("TUK75", HELD_DAY))
        .get(as(BIG_DECIMAL))
        .isEqualByComparingTo("1.23000");
    assertThat(service.findLatestPublishedNavDateOnOrBefore("TUK75", HELD_DAY)).hasValue(HELD_DAY);
    assertThat(service.findAum("TUK75", HELD_DAY)).isEqualByComparingTo("922500000.00");
    assertThat(service.findPublishedCalculation("TUK75", HELD_DAY))
        .hasValueSatisfying(calculation -> assertThat(calculation.id()).isEqualTo(held));
  }

  @Test
  void anUnreleasedRevisionLeavesTheNavTheTrusteeHasAsTheFundsNav() {
    var sent = calculation(HELD_DAY, "1.23000", "922500000.00");
    navReportRepository.markAsSent(sent);
    var revision = calculation(HELD_DAY, "1.24000", "930000000.00");
    navReportRepository.markAsPublished(revision);

    assertThat(service.findPublishedNavPerUnit("TUK75", HELD_DAY))
        .get(as(BIG_DECIMAL))
        .isEqualByComparingTo("1.23000");
    assertThat(service.findAum("TUK75", HELD_DAY)).isEqualByComparingTo("922500000.00");
    assertThat(service.findPublishedCalculation("TUK75", HELD_DAY))
        .hasValueSatisfying(calculation -> assertThat(calculation.id()).isEqualTo(sent));

    navReportRepository.markAsSent(revision);

    assertThat(service.findPublishedNavPerUnit("TUK75", HELD_DAY))
        .get(as(BIG_DECIMAL))
        .isEqualByComparingTo("1.24000");
    assertThat(service.findAum("TUK75", HELD_DAY)).isEqualByComparingTo("930000000.00");
  }

  private UUID calculation(LocalDate navDate, String navPerUnit, String aum) {
    var calculationId = UUID.randomUUID();
    navReportRepository.saveAll(
        List.of(
            row(navDate, calculationId, "NAV", "Net Asset Value", navPerUnit, navPerUnit),
            row(navDate, calculationId, "UNITS", "Total outstanding units:", navPerUnit, aum),
            row(navDate, calculationId, "SECURITY", "World fund", "1.00", aum)));
    return calculationId;
  }

  private static NavReportRow row(
      LocalDate navDate,
      UUID calculationId,
      String accountType,
      String accountName,
      String marketPrice,
      String marketValue) {
    return NavReportRow.builder()
        .navDate(navDate)
        .fundCode("TUK75")
        .accountType(accountType)
        .accountName(accountName)
        .quantity(BigDecimal.ONE)
        .marketPrice(new BigDecimal(marketPrice))
        .marketValue(new BigDecimal(marketValue))
        .calculationId(calculationId)
        .build();
  }
}
