package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentReportRepository extends JpaRepository<InvestmentReport, Long> {

  boolean existsByProviderAndReportTypeAndReportDate(
      ReportProvider provider, ReportType reportType, LocalDate reportDate);

  Optional<InvestmentReport> findByProviderAndReportTypeAndReportDate(
      ReportProvider provider, ReportType reportType, LocalDate reportDate);

  List<InvestmentReport> findAllByProviderAndReportType(
      ReportProvider provider, ReportType reportType);

  Optional<InvestmentReport> findTopByProviderAndReportTypeOrderByReportDateDesc(
      ReportProvider provider, ReportType reportType);

  @Query(
      """
      SELECT MAX(r.reportDate) FROM InvestmentReport r
      WHERE r.provider = :provider AND r.reportType = :reportType AND r.reportDate < :date
      """)
  Optional<LocalDate> findLatestReportDateBefore(
      @Param("provider") ReportProvider provider,
      @Param("reportType") ReportType reportType,
      @Param("date") LocalDate date);
}
