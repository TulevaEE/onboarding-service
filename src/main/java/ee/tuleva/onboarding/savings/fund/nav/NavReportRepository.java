package ee.tuleva.onboarding.savings.fund.nav;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface NavReportRepository extends JpaRepository<NavReportRow, Long> {

  List<NavReportRow> findByNavDateAndFundCodeOrderById(LocalDate navDate, String fundCode);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query("delete from NavReportRow r where r.navDate = :navDate and r.fundCode = :fundCode")
  void deleteByNavDateAndFundCode(
      @Param("navDate") LocalDate navDate, @Param("fundCode") String fundCode);

  @Transactional
  default void replaceByNavDateAndFundCode(
      LocalDate navDate, String fundCode, List<NavReportRow> rows) {
    deleteByNavDateAndFundCode(navDate, fundCode);
    saveAll(rows);
  }
}
