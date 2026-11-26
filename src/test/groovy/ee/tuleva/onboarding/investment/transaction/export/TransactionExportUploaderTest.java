package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.FT_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.SEB_FUND;
import static ee.tuleva.onboarding.investment.transaction.export.ExportFile.UUID_WORKBOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionExportUploaderTest {

  private static final LocalDate TRADE_DATE = LocalDate.parse("2026-02-16");
  private static final Instant HALF_PAST_FOUR_IN_TALLINN = Instant.parse("2026-02-16T14:30:05Z");

  @Mock private GoogleDriveClient driveClient;

  @InjectMocks private TransactionExportUploader uploader;

  @Test
  void uploadExports_putsTheDaysFilesUnderTheirGasNamesInTheTradeMonthsFolder() {
    var files =
        Map.of(
            SEB_FUND, new byte[] {1, 2},
            SEB_ETF, new byte[] {3, 4},
            FT_ETF, new byte[] {5, 6},
            UUID_WORKBOOK, new byte[] {7, 8});
    given(driveClient.getOrCreateFolder("root-id", "2026")).willReturn("year-folder-id");
    given(driveClient.getOrCreateFolder("year-folder-id", "02")).willReturn("month-folder-id");
    given(
            driveClient.uploadFile(
                "month-folder-id", "SEB_FUND_ORDERS_16022026.csv", new byte[] {1, 2}))
        .willReturn("https://drive.google.com/seb-fund");
    given(
            driveClient.uploadFile(
                "month-folder-id", "SEB_ETF_tehingud_16022026.xlsx", new byte[] {3, 4}))
        .willReturn("https://drive.google.com/seb-etf");
    given(
            driveClient.uploadFile(
                "month-folder-id", "FT_ETF_orders_16022026.xlsx", new byte[] {5, 6}))
        .willReturn("https://drive.google.com/ft-etf");
    given(
            driveClient.uploadFile(
                "month-folder-id", "Tehingud_UUID_20260216_1630.xlsx", new byte[] {7, 8}))
        .willReturn("https://drive.google.com/uuid-workbook");

    var result = uploader.uploadExports("root-id", TRADE_DATE, HALF_PAST_FOUR_IN_TALLINN, files);

    assertThat(result)
        .isEqualTo(
            Map.of(
                "sebFundXlsx", "https://drive.google.com/seb-fund",
                "sebEtfXlsx", "https://drive.google.com/seb-etf",
                "ftEtfXlsx", "https://drive.google.com/ft-etf",
                "uuidWorkbookXlsx", "https://drive.google.com/uuid-workbook"));
  }

  @Test
  void uploadExports_uploadsOnlyTheFilesOfTheVenuesWithOrders() {
    given(driveClient.getOrCreateFolder("root-id", "2026")).willReturn("year-id");
    given(driveClient.getOrCreateFolder("year-id", "02")).willReturn("month-id");
    given(driveClient.uploadFile("month-id", "SEB_FUND_ORDERS_16022026.csv", new byte[] {1, 2}))
        .willReturn("https://drive.google.com/fund-only");

    var result =
        uploader.uploadExports(
            "root-id", TRADE_DATE, HALF_PAST_FOUR_IN_TALLINN, Map.of(SEB_FUND, new byte[] {1, 2}));

    assertThat(result).isEqualTo(Map.of("sebFundXlsx", "https://drive.google.com/fund-only"));
    then(driveClient).should(times(1)).uploadFile(any(), any(), any());
  }

  @Test
  void uploadExports_returnsEmptyMapWhenNoFiles() {
    var result = uploader.uploadExports("root-id", TRADE_DATE, HALF_PAST_FOUR_IN_TALLINN, Map.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(driveClient);
  }
}
