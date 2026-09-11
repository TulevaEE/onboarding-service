package ee.tuleva.onboarding.savings.fund.report;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SnapshotExtension.class)
class TrusteeReportCsvGeneratorGoldenMasterTest {

  private final TrusteeReportCsvGenerator generator = new TrusteeReportCsvGenerator();

  private Expect expect;

  @Test
  void generatesCsvAcrossFundLifecycleFromInceptionThroughRoundingBoundaries() {
    var rows =
        List.of(
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2026, 2, 6))
                .nav(new BigDecimal("0.9985"))
                .issuedUnits(new BigDecimal("12345.678"))
                .issuedAmount(new BigDecimal("12328.99"))
                .redeemedUnits(new BigDecimal("200.500"))
                .redeemedAmount(new BigDecimal("199.70"))
                .totalOutstandingUnits(new BigDecimal("6100368.885"))
                .build(),
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2026, 2, 5))
                .nav(new BigDecimal("1.00000"))
                .issuedUnits(new BigDecimal("70981.8295"))
                .issuedAmount(new BigDecimal("70705.005"))
                .redeemedUnits(new BigDecimal("500.0004"))
                .redeemedAmount(new BigDecimal("498.004"))
                .totalOutstandingUnits(new BigDecimal("6088223.707"))
                .build(),
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2026, 2, 4))
                .nav(new BigDecimal("0.996"))
                .issuedUnits(ZERO)
                .issuedAmount(ZERO)
                .redeemedUnits(ZERO)
                .redeemedAmount(ZERO)
                .totalOutstandingUnits(new BigDecimal("6017741.878"))
                .build(),
            TrusteeReportRow.builder()
                .reportDate(TKF100.getInceptionDate())
                .nav(ONE)
                .issuedUnits(new BigDecimal("6017741.878"))
                .issuedAmount(new BigDecimal("6017741.88"))
                .redeemedUnits(ZERO)
                .redeemedAmount(ZERO)
                .totalOutstandingUnits(new BigDecimal("6017741.878"))
                .build());

    var csv = generator.generate(rows);

    expect.toMatchSnapshot(new String(csv, UTF_8).replace("\r\n", "\n"));
  }

  @Test
  void generatesHeaderOnlyCsvWhenNoRowsExist() {
    var csv = generator.generate(List.of());

    expect.toMatchSnapshot(new String(csv, UTF_8).replace("\r\n", "\n"));
  }
}
