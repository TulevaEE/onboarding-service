package ee.tuleva.onboarding.savings.fund.gift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({GiftLinkService.class, GiftLinkServiceDatabaseTest.FixedClockConfig.class})
class GiftLinkServiceDatabaseTest {

  private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
  private static final String PARENT = "38888888888";
  private static final String CHILD = "61001010000";

  @Autowired private GiftLinkService service;
  @MockitoSpyBean private GiftLinkRepository giftLinks;
  @MockitoBean private ParentChildLinkService parentChildLinks;

  @BeforeEach
  void setUp() {
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(true);
  }

  @AfterEach
  void tearDown() {
    giftLinks.deleteAll();
  }

  @Test
  void replacingALinkClosesTheOldRowAndLeavesExactlyOneOpen() {
    var original = service.openLinkFor(PARENT, CHILD);

    var replacement = service.replaceLink(PARENT, original.getId());

    assertThat(replacement.getToken()).isNotEqualTo(original.getToken());
    assertThat(giftLinks.findByTokenAndClosedAtIsNull(original.getToken())).isEmpty();
    assertThat(giftLinks.findByTokenAndClosedAtIsNull(replacement.getToken())).isPresent();
    assertThat(giftLinks.findByRecipientPersonalCodeAndClosedAtIsNull(CHILD)).contains(replacement);
  }

  @Test
  void askingTwiceHandsBackTheSameLink() {
    var first = service.openLinkFor(PARENT, CHILD);
    var second = service.openLinkFor(PARENT, CHILD);

    assertThat(second.getToken()).isEqualTo(first.getToken());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void theAskThatLosesTheRaceHandsBackTheLinkTheWinnerMinted() {
    var winners = service.openLinkFor(PARENT, CHILD);
    givenTheOpenLinkIsMissedOnce();

    var losers = service.openLinkFor(PARENT, CHILD);

    assertThat(losers.getId()).isEqualTo(winners.getId());
    assertThat(giftLinks.findAll().stream().filter(GiftLink::isOpen).map(GiftLink::getId).toList())
        .containsExactly(winners.getId());
  }

  private void givenTheOpenLinkIsMissedOnce() {
    var missedIt = new AtomicBoolean(false);
    willAnswer(
            invocation ->
                missedIt.compareAndSet(false, true) ? Optional.empty() : theOpenLinkOnDisk())
        .given(giftLinks)
        .findByRecipientPersonalCodeAndClosedAtIsNull(CHILD);
  }

  private Optional<GiftLink> theOpenLinkOnDisk() {
    return giftLinks.findAll().stream().filter(GiftLink::isOpen).findFirst();
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
