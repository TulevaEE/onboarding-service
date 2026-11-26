package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.FT_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.GENERIC_ORDERS;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_FUND;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.UUID_WORKBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExportFileTest {

  private static final Instant FIVE_PAST_MIDNIGHT_IN_TALLINN =
      Instant.parse("2026-08-10T21:05:03Z");

  @Test
  void theSebFundFileIsServedAsCsv() {
    assertThat(SEB_FUND.mimeType()).isEqualTo("text/csv");
    assertThat(SEB_FUND.downloadFileName(42L)).isEqualTo("batch-42-sebFundXlsx.csv");
    assertThat(SEB_FUND.driveLabel()).isEqualTo("SEB indeksfondid");
  }

  @Test
  void theBrokerFilesCarryTheGasFileNames_datedInTallinnTime() {
    assertThat(SEB_FUND.fileName(FIVE_PAST_MIDNIGHT_IN_TALLINN))
        .isEqualTo("SEB_FUND_ORDERS_11082026.csv");
    assertThat(SEB_ETF.fileName(FIVE_PAST_MIDNIGHT_IN_TALLINN))
        .isEqualTo("SEB_ETF_tehingud_11082026.xlsx");
    assertThat(FT_ETF.fileName(FIVE_PAST_MIDNIGHT_IN_TALLINN))
        .isEqualTo("FT_ETF_orders_11082026.xlsx");
    assertThat(UUID_WORKBOOK.fileName(FIVE_PAST_MIDNIGHT_IN_TALLINN))
        .isEqualTo("Tehingud_UUID_20260811_0005.xlsx");
  }

  @Test
  void onlyTheFilesThatGoToABrokerAreListedAsBrokerFiles() {
    assertThat(ExportFile.brokerFiles()).doesNotContain(GENERIC_ORDERS);
    assertThat(ExportFile.byMetadataKey("sebFundXlsx")).contains(SEB_FUND);
    assertThat(ExportFile.byMetadataKey("nothingLikeThis")).isEmpty();
  }

  @Test
  void theInternalWorkbookRefusesToInventABrokerFileNameOrADriveLabel() {
    assertThatThrownBy(() -> GENERIC_ORDERS.fileName(FIVE_PAST_MIDNIGHT_IN_TALLINN))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(GENERIC_ORDERS::driveLabel).isInstanceOf(IllegalStateException.class);
  }
}
