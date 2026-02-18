package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionExportUploaderTest {

  @Mock private GoogleDriveClient driveClient;

  @InjectMocks private TransactionExportUploader uploader;

  @Test
  void uploadExports_createsYearAndMonthFoldersAndUploadsFiles() {
    var rootFolderId = "root-id";
    var timestamp = Instant.parse("2026-02-16T14:30:05Z");
    var exports =
        Map.of(
            "sebFundXlsx", new byte[] {1, 2},
            "sebEtfXlsx", new byte[] {3, 4},
            "ftEtfXlsx", new byte[] {5, 6});

    when(driveClient.getOrCreateFolder("root-id", "2026_tehingud")).thenReturn("year-folder-id");
    when(driveClient.getOrCreateFolder("year-folder-id", "02.2026")).thenReturn("month-folder-id");

    when(driveClient.uploadFile(
            "month-folder-id",
            "SEB_TKF100_indeksfondid_2026-02-16T14_30_05.xlsx",
            new byte[] {1, 2}))
        .thenReturn("https://drive.google.com/seb-fund");
    when(driveClient.uploadFile(
            "month-folder-id",
            "SEB_TKF100_ETF_tehingud_2026-02-16T14_30_05.xlsx",
            new byte[] {3, 4}))
        .thenReturn("https://drive.google.com/seb-etf");
    when(driveClient.uploadFile(
            "month-folder-id", "FT_TKF100_ETF_orders_2026-02-16T14_30_05.xlsx", new byte[] {5, 6}))
        .thenReturn("https://drive.google.com/ft-etf");

    var result = uploader.uploadExports(rootFolderId, TKF100, timestamp, exports);

    assertThat(result)
        .isEqualTo(
            Map.of(
                "sebFundXlsx", "https://drive.google.com/seb-fund",
                "sebEtfXlsx", "https://drive.google.com/seb-etf",
                "ftEtfXlsx", "https://drive.google.com/ft-etf"));
  }

  @Test
  void uploadExports_skipsEmptyExports() {
    var rootFolderId = "root-id";
    var timestamp = Instant.parse("2026-03-05T09:15:00Z");
    var exports = Map.of("sebFundXlsx", new byte[] {1, 2});

    when(driveClient.getOrCreateFolder("root-id", "2026_tehingud")).thenReturn("year-id");
    when(driveClient.getOrCreateFolder("year-id", "03.2026")).thenReturn("month-id");
    when(driveClient.uploadFile(
            "month-id", "SEB_TKF100_indeksfondid_2026-03-05T09_15_00.xlsx", new byte[] {1, 2}))
        .thenReturn("https://drive.google.com/fund-only");

    var result = uploader.uploadExports(rootFolderId, TKF100, timestamp, exports);

    assertThat(result).isEqualTo(Map.of("sebFundXlsx", "https://drive.google.com/fund-only"));
    verify(driveClient, times(1)).uploadFile(any(), any(), any());
  }

  @Test
  void uploadExports_returnsEmptyMapWhenNoExports() {
    var result =
        uploader.uploadExports("root-id", TKF100, Instant.parse("2026-01-10T12:00:00Z"), Map.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(driveClient);
  }
}
