package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.QUARTERLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(PeriodicTdAttributionWriter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PeriodicTdAttributionWriterTest {

  private static final LocalDate JUNE_START = LocalDate.of(2026, 6, 1);
  private static final LocalDate JUNE_END = LocalDate.of(2026, 6, 30);
  private static final LocalDate SECOND_QUARTER_START = LocalDate.of(2026, 4, 1);
  private static final String ISIN = "IE00BFG1TM61";

  @Autowired PeriodicTdAttributionWriter writer;
  @Autowired PeriodicTdAttributionRepository repository;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void clearPreviousRows() {
    repository.deleteAll();
  }

  @Test
  void replacingAnAlreadyStoredPeriodCommitsOnlyTheNewAttribution() {
    writer.replace(attribution(TUK75, JUNE_START, MONTHLY, "0.00010"));

    writer.replace(attribution(TUK75, JUNE_START, MONTHLY, "0.00020"));

    assertThat(storedAttributions())
        .extracting(
            PeriodicTdAttribution::getFund,
            PeriodicTdAttribution::getPeriodStart,
            PeriodicTdAttribution::getResidual,
            attribution -> attribution.getDetails().size())
        .containsExactly(tuple(TUK75, JUNE_START, new BigDecimal("0.00020000"), 1));
  }

  @Test
  void replacingOnePeriodLeavesOtherFundsAndPeriodTypesAlone() {
    writer.replace(attribution(TUK75, JUNE_START, MONTHLY, "0.00010"));
    writer.replace(attribution(TUK00, JUNE_START, MONTHLY, "0.00030"));
    writer.replace(attribution(TUK75, SECOND_QUARTER_START, QUARTERLY, "0.00040"));

    writer.replace(attribution(TUK75, JUNE_START, MONTHLY, "0.00020"));

    assertThat(storedAttributions())
        .extracting(
            PeriodicTdAttribution::getFund,
            PeriodicTdAttribution::getPeriodType,
            PeriodicTdAttribution::getResidual)
        .containsExactlyInAnyOrder(
            tuple(TUK75, MONTHLY, new BigDecimal("0.00020000")),
            tuple(TUK00, MONTHLY, new BigDecimal("0.00030000")),
            tuple(TUK75, QUARTERLY, new BigDecimal("0.00040000")));
  }

  private List<PeriodicTdAttribution> storedAttributions() {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              var attributions = repository.findAll();
              attributions.forEach(attribution -> attribution.getDetails().size());
              return attributions;
            });
  }

  private PeriodicTdAttribution attribution(
      TulevaFund fund, LocalDate periodStart, PeriodType periodType, String residual) {
    var periodEnd = periodType == MONTHLY ? JUNE_END : periodStart.plusMonths(3).minusDays(1);
    var attribution =
        PeriodicTdAttribution.builder()
            .fund(fund)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .periodType(periodType)
            .fundReturn(BigDecimal.ZERO)
            .modelReturn(BigDecimal.ZERO)
            .tdGeometric(BigDecimal.ZERO)
            .residual(new BigDecimal(residual))
            .build();
    attribution.addDetail(TdAttributionDetail.builder().isin(ISIN).build());
    return attribution;
  }
}
