package ee.tuleva.onboarding.savings.fund.nav;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface NavReportRepository extends JpaRepository<NavReportRow, Long> {

  List<NavReportRow> findByNavDateAndFundCodeOrderById(LocalDate navDate, String fundCode);

  @Transactional
  void deleteByNavDateAndFundCode(LocalDate navDate, String fundCode);

  @Transactional
  default void replaceByNavDateAndFundCode(
      LocalDate navDate, String fundCode, List<NavReportRow> rows) {
    deleteByNavDateAndFundCode(navDate, fundCode);
    saveAll(rows);
  }
}
