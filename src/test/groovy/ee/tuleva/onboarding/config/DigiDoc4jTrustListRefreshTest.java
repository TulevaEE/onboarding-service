package ee.tuleva.onboarding.config;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.digidoc4j.Configuration.Mode.PROD;

import java.nio.file.Path;
import java.time.Instant;
import org.digidoc4j.Configuration;
import org.digidoc4j.TSLCertificateSource;
import org.digidoc4j.exceptions.TslRefreshException;
import org.digidoc4j.impl.asic.tsl.LazyTslCertificateSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DigiDoc4jTrustListRefreshTest {

  @TempDir Path directory;

  @Test
  void aFailedDownloadDropsTheCachedListSoTheNextTrustLookupDownloadsAgainAndFails() {
    TrustListFixture lists =
        TrustListFixture.publishedUntil(Instant.now().plus(30, DAYS), directory);
    TSLCertificateSource tsl = lists.servedTo(new Configuration(PROD)).getTSL();
    tsl.refresh();
    assertThat(tsl.getCertificates()).contains(lists.qualifiedCa());
    lists.endpointGoesDown();

    assertThatThrownBy(tsl::refresh).isInstanceOf(TslRefreshException.class);
    int downloadAttemptsAfterFailedRefresh = lists.downloadAttempts();

    assertThat(((LazyTslCertificateSource) tsl).getLastCacheReloadingTime()).isNull();
    assertThat(
            ((LazyTslCertificateSource) tsl)
                .getTslLoader()
                .getTslCertificateSource()
                .getCertificates())
        .contains(lists.qualifiedCa());
    assertThatThrownBy(tsl::getCertificates).isInstanceOf(TslRefreshException.class);
    assertThat(lists.downloadAttempts()).isGreaterThan(downloadAttemptsAfterFailedRefresh);
  }
}
