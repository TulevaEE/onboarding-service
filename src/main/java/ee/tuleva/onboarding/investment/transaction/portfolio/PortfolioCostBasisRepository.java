package ee.tuleva.onboarding.investment.transaction.portfolio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PortfolioCostBasisRepository extends JpaRepository<PortfolioCostBasis, Long> {

  Optional<PortfolioCostBasis> findByFundIsinAndInstrumentIsinAndAsOfDate(
      String fundIsin, String instrumentIsin, LocalDate asOfDate);

  List<PortfolioCostBasis> findByFundIsinAndAsOfDate(String fundIsin, LocalDate asOfDate);

  @Query(
      """
      SELECT c FROM PortfolioCostBasis c
      WHERE c.fundIsin = :fundIsin
        AND c.instrumentIsin = :instrumentIsin
        AND c.asOfDate < :asOfDate
      ORDER BY c.asOfDate DESC
      """)
  List<PortfolioCostBasis> findPriorRows(
      String fundIsin, String instrumentIsin, LocalDate asOfDate);

  default Optional<PortfolioCostBasis> findLatestByFundIsinAndInstrumentIsinBefore(
      String fundIsin, String instrumentIsin, LocalDate asOfDate) {
    List<PortfolioCostBasis> rows = findPriorRows(fundIsin, instrumentIsin, asOfDate);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  List<PortfolioCostBasis> findByFundIsinAndAsOfDateGreaterThanEqual(
      String fundIsin, LocalDate asOfDate);
}
