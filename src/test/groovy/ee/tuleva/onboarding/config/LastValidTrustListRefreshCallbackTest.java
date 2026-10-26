package ee.tuleva.onboarding.config;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.digidoc4j.Configuration.Mode.PROD;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import org.digidoc4j.Configuration;
import org.digidoc4j.TSLCertificateSource;
import org.digidoc4j.exceptions.TslRefreshException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class LastValidTrustListRefreshCallbackTest {

  private static final Instant NOW = Instant.now();

  private final TrustListFixture lists;
  private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

  LastValidTrustListRefreshCallbackTest(@TempDir Path fixtureDirectory) {
    lists = TrustListFixture.publishedUntil(NOW.plus(30, DAYS), fixtureDirectory);
  }

  @BeforeEach
  void collectCallbackLogs() {
    logged.start();
    callbackLogger().addAppender(logged);
  }

  @AfterEach
  void stopCollectingCallbackLogs() {
    callbackLogger().detachAppender(logged);
    logged.stop();
  }

  @Test
  void keepsTrustingTheLastValidlySignedListsWhenARefreshCannotDownload() {
    TSLCertificateSource tsl = trustListCheckedAt(NOW);
    tsl.refresh();
    lists.endpointGoesDown();

    tsl.refresh();
    int downloadAttemptsAfterRefresh = lists.downloadAttempts();

    assertThat(tsl.getCertificates()).contains(lists.qualifiedCa());
    assertThat(lists.downloadAttempts()).isEqualTo(downloadAttemptsAfterRefresh);
  }

  @Test
  void raisesAnErrorEachTimeItKeepsTheCachedListsSoTheOutageIsSeenBeforeTheyExpire() {
    TSLCertificateSource tsl = trustListCheckedAt(NOW);
    tsl.refresh();
    lists.endpointGoesDown();

    tsl.refresh();

    assertThat(logged.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
  }

  @Test
  void raisesNothingWhileTheListsDownload() {
    TSLCertificateSource tsl = trustListCheckedAt(NOW);

    tsl.refresh();

    assertThat(logged.list).isEmpty();
  }

  @Test
  void failsTheFirstLoadWhenNothingCouldBeDownloaded() {
    TSLCertificateSource tsl = trustListCheckedAt(NOW);
    lists.endpointGoesDown();

    assertThatThrownBy(tsl::refresh).isInstanceOf(TslRefreshException.class);
  }

  @Test
  void stopsKeepingTheCachedListsOnceTheirNextUpdateHasPassed() {
    TSLCertificateSource tsl = trustListCheckedAt(NOW.plus(31, DAYS));
    tsl.refresh();
    lists.endpointGoesDown();

    assertThatThrownBy(tsl::refresh).isInstanceOf(TslRefreshException.class);
  }

  @Test
  void trustsTheListsAgainOnceTheyCanBeDownloaded() {
    TSLCertificateSource tsl = trustListCheckedAt(NOW);
    lists.endpointGoesDown();
    assertThatThrownBy(tsl::refresh).isInstanceOf(TslRefreshException.class);
    lists.endpointComesBack();

    tsl.refresh();

    assertThat(tsl.getCertificates()).contains(lists.qualifiedCa());
  }

  private TSLCertificateSource trustListCheckedAt(Instant instant) {
    Configuration configuration = lists.servedTo(new Configuration(PROD));
    configuration.setTslRefreshCallback(
        new LastValidTrustListRefreshCallback(configuration, Clock.fixed(instant, UTC)));
    return configuration.getTSL();
  }

  private static Logger callbackLogger() {
    return (Logger) LoggerFactory.getLogger(LastValidTrustListRefreshCallback.class);
  }
}
