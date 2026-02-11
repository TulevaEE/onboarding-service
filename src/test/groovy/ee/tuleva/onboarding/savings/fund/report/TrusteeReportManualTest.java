package ee.tuleva.onboarding.savings.fund.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Manual test - requires local PostgreSQL database")
class TrusteeReportManualTest {

  @Autowired private TrusteeReportRepository repository;
  @Autowired private TrusteeReportCsvGenerator generator;

  @Test
  void generateCsvFromLocalDatabase() throws Exception {
    var rows = repository.findAll();
    assertThat(rows).isNotEmpty();

    var csvBytes = generator.generate(rows);
    var outputPath =
        Path.of(
            System.getProperty("user.home"),
            "Desktop",
            "TKF100_osakute_registri_valjavote_2026-02-05.csv");
    Files.write(outputPath, csvBytes);

    System.out.println("Rows: " + rows.size());
    System.out.println("Saved to: " + outputPath);
    rows.forEach(
        row ->
            System.out.println(
                row.reportDate()
                    + " | NAV="
                    + row.nav()
                    + " | issued="
                    + row.issuedUnits()
                    + " | redeemed="
                    + row.redeemedUnits()
                    + " | total="
                    + row.totalOutstandingUnits()));
  }
}
