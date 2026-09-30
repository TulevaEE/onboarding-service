package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailSender;
import ee.tuleva.onboarding.investment.transaction.export.GoogleDriveProperties;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportService;
import ee.tuleva.onboarding.investment.transaction.export.TransactionExportUploader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  TransactionBatchFinalizer.class,
  TransactionBatchFinalizerTransactionTest.DriveEnabledConfig.class
})
class TransactionBatchFinalizerTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");
  private static final String ROOT_FOLDER_ID = "root-folder-id";
  private static final Map<String, String> DRIVE_FILE_URLS =
      Map.of("sebFundXlsx", "https://drive.google.com/file-1");

  @Autowired private TransactionBatchFinalizer finalizer;
  @Autowired private TransactionBatchRepository batchRepository;
  @Autowired private TransactionAuditEventRepository auditEventRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private SettlementDateCalculator settlementDateCalculator;
  @MockitoBean private TransactionExportService exportService;
  @MockitoBean private TransactionExportUploader exportUploader;
  @MockitoBean private CustodianOrderEmailSender custodianOrderEmailSender;
  @MockitoBean private TransactionOrderFactory orderFactory;

  @AfterEach
  void cleanUp() {
    auditEventRepository.deleteAll();
    batchRepository.deleteAll();
  }

  @Test
  void driveFileUrlsOfABatchFinalizedInsideTheCallersTransaction_areStoredOnTheBatch() {
    var confirmedId = saveConfirmedBatch();
    givenExportsUploadedToDrive();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                finalizer.finalizeConfirmedBatch(
                    batchRepository.findById(confirmedId).orElseThrow()));

    assertThat(batchRepository.findById(confirmedId))
        .get()
        .usingRecursiveComparison()
        .ignoringFields("version")
        .isEqualTo(sentBatchWithDriveFileUrls(confirmedId));
  }

  @Test
  void driveFileUrlsOfABatchLoadedOutsideATransaction_areStoredOnTheBatch() {
    var confirmedId = saveConfirmedBatch();
    givenExportsUploadedToDrive();

    finalizer.finalizeConfirmedBatch(batchRepository.findById(confirmedId).orElseThrow());

    assertThat(batchRepository.findById(confirmedId))
        .get()
        .usingRecursiveComparison()
        .ignoringFields("version")
        .isEqualTo(sentBatchWithDriveFileUrls(confirmedId));
  }

  private Long saveConfirmedBatch() {
    return batchRepository
        .save(
            TransactionBatch.builder()
                .fund(TUV100)
                .status(CONFIRMED)
                .createdBy("system")
                .createdAt(NOW)
                .build())
        .getId();
  }

  private void givenExportsUploadedToDrive() {
    given(exportService.generateOrdersExport(any())).willReturn(new byte[] {1});
    given(exportService.generateSebFundExport(any(), any())).willReturn(new byte[] {2});
    given(exportService.generateSebEtfExport(any(), any())).willReturn(new byte[] {3});
    given(exportService.generateFtEtfExport(any(), any(), any(), any())).willReturn(new byte[] {4});
    given(exportService.generateUuidWorkbook(any())).willReturn(new byte[] {5});
    given(exportUploader.uploadExports(eq(ROOT_FOLDER_ID), eq(TUV100), eq(NOW), any()))
        .willReturn(DRIVE_FILE_URLS);
  }

  private static TransactionBatch sentBatchWithDriveFileUrls(Long id) {
    return TransactionBatch.builder()
        .id(id)
        .fund(TUV100)
        .status(SENT)
        .createdBy("system")
        .createdAt(NOW)
        .metadata(
            Map.of(
                "xlsxExport", "AQ==",
                "sebFundXlsx", "Ag==",
                "sebEtfXlsx", "Aw==",
                "ftEtfXlsx", "BA==",
                "uuidWorkbookXlsx", "BQ==",
                "driveFileUrls", DRIVE_FILE_URLS))
        .build();
  }

  @TestConfiguration
  static class DriveEnabledConfig {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    GoogleDriveProperties googleDriveProperties() {
      return new GoogleDriveProperties(true, "unused-service-account", ROOT_FOLDER_ID);
    }
  }
}
