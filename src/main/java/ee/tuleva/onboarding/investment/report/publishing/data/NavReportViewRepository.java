package ee.tuleva.onboarding.investment.report.publishing.data;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NavReportViewRepository extends JpaRepository<NavReportView, Long> {

  @Query(
      value =
          """
          SELECT * FROM nav_report
          WHERE nav_date = :navDate AND fund_code = :fundCode
            AND calculation_id = (
              SELECT calculation_id FROM nav_report
              WHERE nav_date = :navDate AND fund_code = :fundCode
                AND published_at IS NOT NULL
              ORDER BY id DESC LIMIT 1)
          ORDER BY id
          """,
      nativeQuery = true)
  List<NavReportView> findPublishedByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

  @Query(
      value =
          """
          SELECT DISTINCT nav_date FROM nav_report
          WHERE fund_code = :fundCode
            AND nav_date BETWEEN :startDate AND :endDate
            AND published_at IS NOT NULL
          ORDER BY nav_date DESC
          LIMIT 1
          """,
      nativeQuery = true)
  LocalDate findLatestPublishedNavDate(
      @Param("fundCode") String fundCode,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}
