package ee.tuleva.onboarding.savings.fund.gift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GiftLinkServiceTest {

  private static final String PARENT = "38888888888";
  private static final String CHILD = "61001010000";
  private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

  @Mock GiftLinkRepository giftLinks;
  @Mock ParentChildLinkService parentChildLinks;

  GiftLinkService service;

  @BeforeEach
  void setUp() {
    service = new GiftLinkService(giftLinks, parentChildLinks, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void aStrangerCannotMintALinkForSomebodyElsesChild() {
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(false);

    assertThatThrownBy(() -> service.openLinkFor(PARENT, CHILD))
        .isInstanceOf(NotAllowedToGiftForException.class);

    verify(giftLinks, never()).save(any());
  }

  @Test
  void askingTwiceHandsBackTheSameLink() {
    var existing = aLink("EXISTINGTOKEN");
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(true);
    given(giftLinks.findByRecipientPersonalCodeAndClosedAtIsNull(CHILD))
        .willReturn(Optional.of(existing));

    assertThat(service.openLinkFor(PARENT, CHILD)).isSameAs(existing);

    // Two live links for one child would mean a grandparent could be holding the abandoned one.
    verify(giftLinks, never()).save(any());
  }

  @Test
  void theFirstAskMintsALinkNobodyCouldGuess() {
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(true);
    given(giftLinks.findByRecipientPersonalCodeAndClosedAtIsNull(CHILD))
        .willReturn(Optional.empty());
    given(giftLinks.save(any())).willAnswer(saved -> saved.getArgument(0));

    var minted = service.openLinkFor(PARENT, CHILD);

    assertThat(minted.getRecipientPersonalCode()).isEqualTo(CHILD);
    assertThat(minted.getCreatedByPersonalCode()).isEqualTo(PARENT);
    assertThat(minted.getCreatedAt()).isEqualTo(NOW);
    assertThat(minted.isOpen()).isTrue();
    // 128 bits over a 32 character alphabet: long enough that polling for one is hopeless.
    assertThat(minted.getToken()).hasSizeGreaterThanOrEqualTo(25).matches("[0-9A-HJKMNP-TV-Z]+");
  }

  @Test
  void replacingClosesTheOldLinkAndHandsOutADifferentToken() {
    var existing = aLink("OLDTOKEN");
    given(giftLinks.findById(existing.getId())).willReturn(Optional.of(existing));
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(true);
    given(giftLinks.save(any())).willAnswer(saved -> saved.getArgument(0));

    var replacement = service.replaceLink(PARENT, existing.getId());

    var saved = ArgumentCaptor.forClass(GiftLink.class);
    verify(giftLinks, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().getFirst().getClosedAt()).isEqualTo(NOW);
    assertThat(replacement.getToken()).isNotEqualTo("OLDTOKEN");
    assertThat(replacement.isOpen()).isTrue();
  }

  @Test
  void aParentWhoLostRepresentationCannotReplaceTheLinkTheyOnceMade() {
    var existing = aLink("OLDTOKEN");
    given(giftLinks.findById(existing.getId())).willReturn(Optional.of(existing));
    given(parentChildLinks.isActiveRepresentation(PARENT, CHILD)).willReturn(false);

    assertThatThrownBy(() -> service.replaceLink(PARENT, existing.getId()))
        .isInstanceOf(NotAllowedToGiftForException.class);
  }

  @Test
  void aClosedLinkAndAnUnknownTokenFailTheSameWay() {
    given(giftLinks.findByTokenAndClosedAtIsNull("ANYTHING")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.findOpenLink("ANYTHING"))
        .isInstanceOf(NoSuchElementException.class)
        // The message must not say which of the two it was.
        .hasMessage("No such gift link");
  }

  private static GiftLink aLink(String token) {
    return GiftLink.builder()
        .id(UUID.randomUUID())
        .token(token)
        .recipientPersonalCode(CHILD)
        .createdByPersonalCode(PARENT)
        .createdAt(NOW)
        .build();
  }
}
