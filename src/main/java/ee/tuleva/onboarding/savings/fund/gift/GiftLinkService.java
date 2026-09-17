package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GiftLinkService {

  // Crockford's base32 alphabet: no I, L, O or U, so 1/I and 0/O cannot be transposed when someone
  // reads a link aloud, and nothing accidentally spells a word.
  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int ENTROPY_BYTES = 16;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final GiftLinkRepository giftLinks;
  private final ParentChildLinkService parentChildLinks;
  private final Clock clock;

  /**
   * Hands the parent the link for one of their children, minting it the first time.
   *
   * <p>Idempotent: a parent who opens the page twice gets the same link back rather than a second
   * one, because two live links for the same child would mean a grandparent could be holding the
   * one that was quietly abandoned.
   */
  @Transactional
  public GiftLink openLinkFor(String parentPersonalCode, String childPersonalCode) {
    requireRepresentation(parentPersonalCode, childPersonalCode);
    return giftLinks
        .findByRecipientPersonalCodeAndClosedAtIsNull(childPersonalCode)
        .orElseGet(() -> mint(parentPersonalCode, childPersonalCode));
  }

  /**
   * Closes a link so its token stops working, and mints a replacement.
   *
   * <p>This is the parent's emergency brake for a link that went somewhere it should not have. It
   * is not how links expire, because they do not: a grandparent who saved one should still be able
   * to use it next year.
   */
  @Transactional
  public GiftLink replaceLink(String parentPersonalCode, UUID id) {
    var link =
        giftLinks
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("No such gift link: id=" + id));
    // Re-checked per request rather than trusted from when the link was made, so a parent who has
    // since lost representation cannot keep steering the child's link.
    requireRepresentation(parentPersonalCode, link.getRecipientPersonalCode());
    link.close(clock.instant());
    giftLinks.save(link);
    return mint(parentPersonalCode, link.getRecipientPersonalCode());
  }

  /**
   * Resolves a token handed over by an anonymous visitor.
   *
   * <p>A closed link and a token that never existed fail the same way, so nobody can use the
   * difference to learn that a link once existed.
   */
  public GiftLink findOpenLink(String token) {
    return giftLinks
        .findByTokenAndClosedAtIsNull(token)
        .orElseThrow(() -> new NoSuchElementException("No such gift link"));
  }

  private void requireRepresentation(String parentPersonalCode, String childPersonalCode) {
    if (!parentChildLinks.isActiveRepresentation(parentPersonalCode, childPersonalCode)) {
      throw new NotAllowedToGiftForException(childPersonalCode);
    }
  }

  /**
   * 128 bits, because the token is the only thing standing between a stranger and somebody's gift
   * page. Short and memorable would be guessable, and there is nothing to guess for here except a
   * child's first name.
   */
  private String mintToken() {
    byte[] entropy = new byte[ENTROPY_BYTES];
    RANDOM.nextBytes(entropy);
    var token = new StringBuilder();
    int buffer = 0;
    int bitsInBuffer = 0;
    for (byte b : entropy) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsInBuffer += 8;
      while (bitsInBuffer >= 5) {
        token.append(ALPHABET[(buffer >> (bitsInBuffer - 5)) & 0x1F]);
        bitsInBuffer -= 5;
      }
    }
    if (bitsInBuffer > 0) {
      token.append(ALPHABET[(buffer << (5 - bitsInBuffer)) & 0x1F]);
    }
    return token.toString();
  }

  private GiftLink mint(String parentPersonalCode, String childPersonalCode) {
    return giftLinks.save(
        GiftLink.builder()
            .token(mintToken())
            .recipientPersonalCode(childPersonalCode)
            // Claims the one open slot this child has; the unique constraint refuses a second.
            .openForRecipient(childPersonalCode)
            .createdByPersonalCode(parentPersonalCode)
            .createdAt(clock.instant())
            .build());
  }
}
