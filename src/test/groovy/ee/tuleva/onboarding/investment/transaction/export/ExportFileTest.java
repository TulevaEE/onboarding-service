package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.GENERIC_ORDERS;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExportFileTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-08-10T09:05:03Z");

  @Test
  void theSebFundFileIsServedAsCsv() {
    assertThat(SEB_FUND.mimeType()).isEqualTo("text/csv");
    assertThat(SEB_FUND.downloadFileName(42L)).isEqualTo("batch-42-sebFundXlsx.csv");
    assertThat(SEB_FUND.fileName(TKF100, TIMESTAMP))
        .isEqualTo("SEB_TKF100_indeksfondid_2026-08-10T09_05_03.csv");
    assertThat(SEB_FUND.driveLabel()).isEqualTo("SEB indeksfondid");
  }

  @Test
  void onlyTheFilesThatGoToABrokerAreListedAsBrokerFiles() {
    assertThat(ExportFile.brokerFiles()).doesNotContain(GENERIC_ORDERS);
    assertThat(ExportFile.byMetadataKey("sebFundXlsx")).contains(SEB_FUND);
    assertThat(ExportFile.byMetadataKey("nothingLikeThis")).isEmpty();
  }

  // The internal workbook is never uploaded anywhere, so asking it for a broker name or a Drive
  // folder is a programming error rather than a missing value to paper over.
  @Test
  void theInternalWorkbookRefusesToInventABrokerFileNameOrADriveLabel() {
    assertThatThrownBy(() -> GENERIC_ORDERS.fileName(TKF100, TIMESTAMP))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(GENERIC_ORDERS::driveLabel).isInstanceOf(IllegalStateException.class);
  }
}
