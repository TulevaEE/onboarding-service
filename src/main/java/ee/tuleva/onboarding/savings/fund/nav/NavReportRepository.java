package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NavReportRepository extends JpaRepository<NavReportRow, Long> {

  // The NAV that actually went out. nav_report keeps every calculation for a date, so a
  // recalculation that is still unpublished must never be served as the official price.
  // Order by published_at first: a backdated recalculation has a lower id but a later publication.
  @Query(
      value =
          """
          SELECT market_price FROM nav_report
          WHERE nav_date = :navDate AND fund_code = :fundCode
            AND account_type = :accountType
            AND published_at IS NOT NULL
          ORDER BY published_at DESC, id DESC LIMIT 1
          """,
      nativeQuery = true)
  Optional<BigDecimal> findPublishedNavPerUnit(
      @Param("navDate") LocalDate navDate,
      @Param("fundCode") String fundCode,
      @Param("accountType") String accountType);

  // The newest calculation whether or not it is published, for the gates that run between
  // persisting a NAV calculation and publishing it.
  @Query(
      value =
          """
          SELECT market_price FROM nav_report
          WHERE nav_date = :navDate AND fund_code = :fundCode
            AND account_type = :accountType
          ORDER BY id DESC LIMIT 1
          """,
      nativeQuery = true)
  Optional<BigDecimal> findLatestNavPerUnit(
      @Param("navDate") LocalDate navDate,
      @Param("fundCode") String fundCode,
      @Param("accountType") String accountType);

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

  // Pick the most recently published calculation. Order by published_at first so a backdated
  // calc (lower id but later published_at) doesn't get masked by an earlier-published one.
  @Query(
      value =
          """
          SELECT COALESCE(SUM(nr.market_value), 0)
          FROM nav_report nr
          WHERE nr.fund_code = :fundCode AND nr.nav_date = :navDate
            AND nr.account_type = :accountType
            AND nr.calculation_id = (
              SELECT calculation_id FROM nav_report
              WHERE nav_date = :navDate AND fund_code = :fundCode
                AND published_at IS NOT NULL
              ORDER BY published_at DESC, id DESC LIMIT 1
            )
          """,
      nativeQuery = true)
  BigDecimal sumPublishedMarketValueByAccountType(
      @Param("fundCode") String fundCode,
      @Param("navDate") LocalDate navDate,
      @Param("accountType") String accountType);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(nr.market_value), 0)
          FROM nav_report nr
          WHERE nr.fund_code = :fundCode AND nr.nav_date = :navDate
            AND nr.account_type IN (:accountTypes)
            AND nr.calculation_id = (
              SELECT calculation_id FROM nav_report
              WHERE nav_date = :navDate AND fund_code = :fundCode
              ORDER BY id DESC LIMIT 1
            )
          """,
      nativeQuery = true)
  BigDecimal sumLatestCalculationMarketValueByAccountTypes(
      @Param("fundCode") String fundCode,
      @Param("navDate") LocalDate navDate,
      @Param("accountTypes") List<String> accountTypes);

  Optional<NavReportRow> findFirstByFundCodeAndNavDateOrderByIdDesc(
      String fundCode, LocalDate navDate);

  @Query(
      """
      SELECT new ee.tuleva.onboarding.savings.fund.nav.NavAccountLine(
        row.accountType, row.accountName, row.accountId, row.quantity, row.marketPrice, row.marketValue)
      FROM NavReportRow row
      WHERE row.fundCode = :fundCode AND row.navDate = :navDate
        AND row.calculationId = :calculationId
      """)
  List<NavAccountLine> findLinesByCalculationId(
      @Param("fundCode") String fundCode,
      @Param("navDate") LocalDate navDate,
      @Param("calculationId") UUID calculationId);

  @Query(
      """
      SELECT MAX(row.createdAt) FROM NavReportRow row
      WHERE row.fundCode = :fundCode AND row.navDate = :navDate
        AND row.calculationId = :calculationId
      """)
  Optional<Instant> findLastWrittenAtByCalculationId(
      @Param("fundCode") String fundCode,
      @Param("navDate") LocalDate navDate,
      @Param("calculationId") UUID calculationId);

  boolean existsByFundCodeAndNavDate(String fundCode, LocalDate navDate);
}
