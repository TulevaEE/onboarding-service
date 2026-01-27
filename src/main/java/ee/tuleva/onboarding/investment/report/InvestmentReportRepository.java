package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentReportRepository extends JpaRepository<InvestmentReport, Long> {

  boolean existsByProviderAndReportTypeAndReportDate(
      ReportProvider provider, ReportType reportType, LocalDate reportDate);

  Optional<InvestmentReport> findByProviderAndReportTypeAndReportDate(
      ReportProvider provider, ReportType reportType, LocalDate reportDate);
}
