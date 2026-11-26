package ee.tuleva.onboarding.savings.fund.nav;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NavReportRepository extends JpaRepository<NavReportRow, Long> {

  @Query(
      value =
          """
          SELECT market_price FROM nav_report
          WHERE nav_date = :navDate AND fund_code = :fundCode
            AND account_type = :accountType
            AND sent_to_trustee_at IS NOT NULL
          ORDER BY sent_to_trustee_at DESC, id DESC LIMIT 1
          """,
      nativeQuery = true)
  Optional<BigDecimal> findSentNavPerUnit(
      @Param("navDate") LocalDate navDate,
      @Param("fundCode") String fundCode,
      @Param("accountType") String accountType);

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
          SELECT MAX(nav_date) FROM nav_report
          WHERE fund_code = :fundCode
            AND nav_date <= :asOfDate
            AND sent_to_trustee_at IS NOT NULL
          """,
      nativeQuery = true)
  Optional<LocalDate> findLatestSentNavDateByFundOnOrBefore(
      @Param("fundCode") String fundCode, @Param("asOfDate") LocalDate asOfDate);

  @Query(
      value =
          """
          SELECT * FROM nav_report
          WHERE nav_date = :navDate AND fund_code = :fundCode
            AND calculation_id = (
              SELECT calculation_id FROM nav_report
              WHERE nav_date = :navDate AND fund_code = :fundCode
                AND sent_to_trustee_at IS NOT NULL
              ORDER BY sent_to_trustee_at DESC, id DESC LIMIT 1)
          ORDER BY id
          """,
      nativeQuery = true)
  List<NavReportRow> findSentByNavDateAndFundCode(
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

  @Query(
      value =
          """
          SELECT CASE WHEN EXISTS (
            SELECT 1 FROM nav_report
            WHERE nav_date = :navDate AND fund_code = :fundCode
              AND sent_to_trustee_at IS NOT NULL
          ) THEN true ELSE false END
          """,
      nativeQuery = true)
  boolean existsSentByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "delete from NavReportRow r where r.navDate = :navDate and r.fundCode = :fundCode"
          + " and r.publishedAt is null")
  void deleteUnpublishedByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

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

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE nav_report SET published_at = COALESCE(published_at, CURRENT_TIMESTAMP),"
              + " sent_to_trustee_at = CURRENT_TIMESTAMP"
              + " WHERE calculation_id = :calculationId AND sent_to_trustee_at IS NULL",
      nativeQuery = true)
  void markAsSent(@Param("calculationId") UUID calculationId);

  List<NavReportRow> findByCalculationIdOrderById(UUID calculationId);

  @Lock(PESSIMISTIC_WRITE)
  @Query(
      "SELECT row FROM NavReportRow row WHERE row.calculationId = :calculationId ORDER BY row.id")
  List<NavReportRow> findCalculationRowsForUpdate(@Param("calculationId") UUID calculationId);

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
                AND sent_to_trustee_at IS NOT NULL
              ORDER BY sent_to_trustee_at DESC, id DESC LIMIT 1
            )
          """,
      nativeQuery = true)
  BigDecimal sumSentMarketValueByAccountType(
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
                AND sent_to_trustee_at IS NOT NULL
              ORDER BY sent_to_trustee_at DESC, id DESC LIMIT 1
            )
          """,
      nativeQuery = true)
  BigDecimal sumSentCalculationMarketValueByAccountTypes(
      @Param("fundCode") String fundCode,
      @Param("navDate") LocalDate navDate,
      @Param("accountTypes") List<String> accountTypes);

  Optional<NavReportRow> findFirstByFundCodeAndNavDateOrderByIdDesc(
      String fundCode, LocalDate navDate);

  Optional<NavReportRow>
      findFirstByFundCodeAndNavDateAndSentToTrusteeAtIsNotNullOrderBySentToTrusteeAtDescIdDesc(
          String fundCode, LocalDate navDate);

  default Optional<UUID> findSentCalculationId(String fundCode, LocalDate navDate) {
    return findFirstByFundCodeAndNavDateAndSentToTrusteeAtIsNotNullOrderBySentToTrusteeAtDescIdDesc(
            fundCode, navDate)
        .map(NavReportRow::getCalculationId);
  }

  default Optional<UUID> findLatestCalculationId(String fundCode, LocalDate navDate) {
    return findFirstByFundCodeAndNavDateOrderByIdDesc(fundCode, navDate)
        .map(NavReportRow::getCalculationId);
  }

  @Query(
      """
      SELECT DISTINCT row.navDate FROM NavReportRow row
      WHERE row.fundCode = :fundCode
        AND row.navDate BETWEEN :from AND :to
        AND row.sentToTrusteeAt IS NOT NULL
      ORDER BY row.navDate
      """)
  List<LocalDate> findSentNavDatesBetween(
      @Param("fundCode") String fundCode, @Param("from") LocalDate from, @Param("to") LocalDate to);

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
}
