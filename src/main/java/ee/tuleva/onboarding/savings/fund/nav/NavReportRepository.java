package ee.tuleva.onboarding.savings.fund.nav;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface NavReportRepository extends JpaRepository<NavReportRow, Long> {

  Optional<NavReportRow> findFirstByFundCodeAndNavDateAndAccountType(
      String fundCode, LocalDate navDate, String accountType);

  @Query(
      value =
          """
          SELECT MAX(nav_date) FROM nav_report
          WHERE fund_code = :fundCode
            AND account_type = :accountType
            AND nav_date <= :asOfDate
          """,
      nativeQuery = true)
  Optional<LocalDate> findLatestNavDateByFundAndAccountTypeOnOrBefore(
      @Param("fundCode") String fundCode,
      @Param("accountType") String accountType,
      @Param("asOfDate") LocalDate asOfDate);

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
  List<NavReportRow> findLatestByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

  @Query(
      value =
          """
          SELECT CASE WHEN EXISTS (
            SELECT 1 FROM nav_report
            WHERE nav_date = :navDate AND fund_code = :fundCode
              AND published_at IS NOT NULL
          ) THEN true ELSE false END
          """,
      nativeQuery = true)
  boolean existsPublishedByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "delete from NavReportRow r where r.navDate = :navDate and r.fundCode = :fundCode"
          + " and r.publishedAt is null")
  void deleteUnpublishedByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

  // Delete + saveAll commit independently. If saveAll fails, partial rows share the new
  // calculationId and remain unpublished, so the next NAV run replaces them.
  default void replaceByNavDateAndFundCode(
      LocalDate navDate, String fundCode, List<NavReportRow> rows) {
    deleteUnpublishedByNavDateAndFundCode(navDate, fundCode);
    saveAll(rows);
  }

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE nav_report SET published_at = CURRENT_TIMESTAMP"
              + " WHERE calculation_id = :calculationId AND published_at IS NULL",
      nativeQuery = true)
  void markAsPublished(@Param("calculationId") UUID calculationId);
}
