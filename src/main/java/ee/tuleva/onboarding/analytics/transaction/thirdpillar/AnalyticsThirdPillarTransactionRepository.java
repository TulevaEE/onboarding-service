package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransaction.EMPLOYER_CONTRIBUTION_SOURCE;
import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransaction.OWN_CONTRIBUTION_SOURCE;

import ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.ThirdPillarContribution;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsThirdPillarTransactionRepository
    extends JpaRepository<AnalyticsThirdPillarTransaction, Long> {

  default int countContributionMonthsSince(String personalCode, LocalDate fromDate) {
    return countMonthsWithTransactionsFromSourcesSince(
        personalCode, List.of(OWN_CONTRIBUTION_SOURCE, EMPLOYER_CONTRIBUTION_SOURCE), fromDate);
  }

  @Query(
      """
      SELECT COUNT(DISTINCT FUNCTION('to_char', t.reportingDate, 'YYYY-MM'))
      FROM AnalyticsThirdPillarTransaction t
      WHERE t.personalId = :personalCode
        AND t.transactionSource IN :sources
        AND t.reportingDate >= :fromDate
      """)
  int countMonthsWithTransactionsFromSourcesSince(
      String personalCode, Collection<String> sources, LocalDate fromDate);

  default List<ThirdPillarContribution> findOwnContributionsSince(
      String personalCode, LocalDate fromDate) {
    return findContributionsFromSourceSince(personalCode, OWN_CONTRIBUTION_SOURCE, fromDate);
  }

  @Query(
      """
      SELECT new ee.tuleva.onboarding.analytics.ThirdPillarContribution(
        t.reportingDate, t.transactionValue)
      FROM AnalyticsThirdPillarTransaction t
      WHERE t.personalId = :personalCode
        AND t.transactionSource = :source
        AND t.reportingDate >= :fromDate
      ORDER BY t.reportingDate
      """)
  List<ThirdPillarContribution> findContributionsFromSourceSince(
      String personalCode, String source, LocalDate fromDate);

  @Query("SELECT MAX(t.reportingDate) FROM AnalyticsThirdPillarTransaction t")
  Optional<LocalDate> findLatestReportingDate();

  List<AnalyticsThirdPillarTransaction> findByReportingDateGreaterThanEqual(
      LocalDate reportingDate);

  @Modifying
  @Query(
      "DELETE FROM AnalyticsThirdPillarTransaction t WHERE t.reportingDate BETWEEN :startDate AND :endDate")
  int deleteByReportingDateBetween(
      @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
