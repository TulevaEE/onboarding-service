package ee.tuleva.onboarding.investment.report.publishing.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

class NavReportViewFixture {

  static NavReportView create(
      LocalDate navDate,
      String fundCode,
      String accountType,
      String accountName,
      String accountId,
      BigDecimal quantity,
      BigDecimal marketPrice,
      BigDecimal marketValue) {
    var row = new NavReportView();
    ReflectionTestUtils.setField(row, "navDate", navDate);
    ReflectionTestUtils.setField(row, "fundCode", fundCode);
    ReflectionTestUtils.setField(row, "accountType", accountType);
    ReflectionTestUtils.setField(row, "accountName", accountName);
    ReflectionTestUtils.setField(row, "accountId", accountId);
    ReflectionTestUtils.setField(row, "quantity", quantity);
    ReflectionTestUtils.setField(row, "marketPrice", marketPrice);
    ReflectionTestUtils.setField(row, "marketValue", marketValue);
    ReflectionTestUtils.setField(row, "currency", "EUR");
    ReflectionTestUtils.setField(row, "calculationId", UUID.randomUUID());
    return row;
  }
}
