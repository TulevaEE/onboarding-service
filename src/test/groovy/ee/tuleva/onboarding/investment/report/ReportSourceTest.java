package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class ReportSourceTest {

  @Mock private S3Client s3Client;

  @Test
  void sebSource_fetch_returnsInputStream() {
    SebReportSource source = new SebReportSource(s3Client);
    LocalDate date = LocalDate.of(2026, 1, 15);
    String content = "test,data";
    var response =
        new ResponseInputStream<>(
            GetObjectResponse.builder().build(),
            AbortableInputStream.create(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

    Optional<InputStream> result = source.fetch(POSITIONS, date);

    assertThat(result).isPresent();
  }

  @Test
  void sebSource_fetch_returnsEmptyWhenNotFound() {
    SebReportSource source = new SebReportSource(s3Client);
    LocalDate date = LocalDate.of(2026, 1, 15);

    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("Not found").build());

    Optional<InputStream> result = source.fetch(POSITIONS, date);

    assertThat(result).isEmpty();
  }

  @Test
  void sebSource_fetch_throwsRuntimeExceptionOnS3Error() {
    SebReportSource source = new SebReportSource(s3Client);
    LocalDate date = LocalDate.of(2026, 1, 15);
    S3Exception exception =
        (S3Exception) S3Exception.builder().statusCode(500).message("Server error").build();
    when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(exception);

    assertThatThrownBy(() -> source.fetch(POSITIONS, date)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void sebSource_getProvider_returnsSeb() {
    SebReportSource source = new SebReportSource(s3Client);
    assertThat(source.getProvider()).isEqualTo(SEB);
  }

  @Test
  void sebSource_getSupportedReportTypes_returnsPositionsAndPendingTransactions() {
    SebReportSource source = new SebReportSource(s3Client);
    assertThat(source.getSupportedReportTypes()).containsExactly(POSITIONS, PENDING_TRANSACTIONS);
  }

  @Test
  void sebSource_getKey_returnsCorrectPathForPositions() {
    SebReportSource source = new SebReportSource(s3Client);
    LocalDate date = LocalDate.of(2026, 1, 15);

    assertThat(source.getKey(POSITIONS, date)).isEqualTo("seb/2026-01-15_positions.csv");
  }

  @Test
  void sebSource_getKey_returnsCorrectPathForPendingTransactions() {
    SebReportSource source = new SebReportSource(s3Client);
    LocalDate date = LocalDate.of(2026, 1, 15);

    assertThat(source.getKey(PENDING_TRANSACTIONS, date))
        .isEqualTo("seb/2026-01-15_pending_transactions.csv");
  }

  @Test
  void abstractSource_getBucket_returnsBucketName() {
    SebReportSource source = new SebReportSource(s3Client);
    assertThat(source.getBucket()).isEqualTo("tuleva-investment-reports");
  }

  @Test
  void sebSource_extractCsvMetadata_extractsSentAndAsOfDates() {
    SebReportSource source = new SebReportSource(s3Client);
    String csv =
        "Sent:;2026-01-26;;;;\n"
            + "As of:;2026-01-25;;;;\n"
            + "Fund name:;TKF100;;;;\n"
            + "Fund Management Company:;Tuleva Fondid AS;;;;\n"
            + "\n"
            + "Client name;Account;ISIN;Name;Quantity\n"
            + "TKF100;EE123;;Cash;1000\n";

    Map<String, Object> metadata = source.extractCsvMetadata(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(metadata).isEqualTo(Map.of("sentDate", "2026-01-26", "asOfDate", "2026-01-25"));
  }

  @Test
  void sebSource_extractCsvMetadata_returnsEmptyMapWhenNoDatesFound() {
    SebReportSource source = new SebReportSource(s3Client);
    String csv = "Client name;Account\nTKF100;EE123\n";

    Map<String, Object> metadata = source.extractCsvMetadata(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(metadata).isEmpty();
  }

  @Test
  void reportSourceComponents_requestOnlySebKeys() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(ReportSource.class));

    var keys =
        scanner.findCandidateComponents("ee.tuleva.onboarding").stream()
            .map(this::instantiate)
            .flatMap(
                source ->
                    source.getSupportedReportTypes().stream()
                        .map(reportType -> source.getKey(reportType, LocalDate.of(2026, 1, 15))))
            .toList();

    assertThat(keys)
        .containsExactlyInAnyOrder(
            "seb/2026-01-15_positions.csv", "seb/2026-01-15_pending_transactions.csv");
  }

  private ReportSource instantiate(BeanDefinition beanDefinition) {
    try {
      Class<?> type = Class.forName(requireNonNull(beanDefinition.getBeanClassName()));
      return (ReportSource) type.getConstructor(S3Client.class).newInstance(s3Client);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot instantiate report source: " + beanDefinition, e);
    }
  }
}
