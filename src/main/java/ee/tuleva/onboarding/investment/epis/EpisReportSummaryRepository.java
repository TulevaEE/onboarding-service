package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.report.ReportType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EpisReportSummaryRepository extends JpaRepository<EpisReportSummary, Long> {

  Optional<EpisReportSummary> findTopByReportTypeAndFundOrderByReportDateDescIdDesc(
      ReportType reportType, TulevaFund fund);

  Optional<EpisReportSummary> findByReportIdAndFund(Long reportId, TulevaFund fund);

  List<EpisReportSummary> findByReportId(Long reportId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from EpisReportSummary s where s.reportId = :reportId")
  void deleteByReportId(@Param("reportId") Long reportId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from EpisReportSummary s where s.reportType = :reportType and s.reportDate = :reportDate")
  void deleteByReportTypeAndReportDate(
      @Param("reportType") ReportType reportType, @Param("reportDate") LocalDate reportDate);
}
