package ee.tuleva.onboarding.savings.fund.nav;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface NavReportRepository extends JpaRepository<NavReportRow, Long> {

  List<NavReportRow> findByNavDateAndFundCodeOrderById(LocalDate navDate, String fundCode);
}
