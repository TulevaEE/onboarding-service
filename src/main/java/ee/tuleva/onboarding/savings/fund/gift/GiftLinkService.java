package ee.tuleva.onboarding.savings.fund.gift;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class GiftLinkService {

  // Crockford's base32: no I, L, O or U, so 1/I and 0/O survive being read aloud.
  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int ENTROPY_BYTES = 16;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final GiftLinkRepository giftLinks;
  private final ParentChildLinkService parentChildLinks;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  public GiftLink openLinkFor(String parentPersonalCode, String childPersonalCode) {
    requireRepresentation(parentPersonalCode, childPersonalCode);
    return giftLinks
        .findByRecipientPersonalCodeAndClosedAtIsNull(childPersonalCode)
        .orElseGet(
            () -> mintUnlessAnotherRequestGotThereFirst(parentPersonalCode, childPersonalCode));
  }

  private GiftLink mintUnlessAnotherRequestGotThereFirst(
      String parentPersonalCode, String childPersonalCode) {
    try {
      return requireNonNull(
          transactionTemplate.execute(transaction -> mint(parentPersonalCode, childPersonalCode)));
    } catch (DataIntegrityViolationException anotherRequestMintedTheOpenLink) {
      return giftLinks
          .findByRecipientPersonalCodeAndClosedAtIsNull(childPersonalCode)
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Gift link neither minted nor open: childPersonalCode=" + childPersonalCode));
    }
  }

  @Transactional
  public GiftLink replaceLink(String parentPersonalCode, UUID id) {
    var link =
        giftLinks
            .findByIdAndClosedAtIsNull(id)
            .orElseThrow(() -> new NoSuchElementException("No such open gift link: id=" + id));
    requireRepresentation(parentPersonalCode, link.getRecipientPersonalCode());
    link.close(clock.instant());
    // Flushed before the replacement is minted: Hibernate runs inserts before updates, so the new
    // row would otherwise claim open_for_recipient while the old row still holds it.
    giftLinks.saveAndFlush(link);
    return mint(parentPersonalCode, link.getRecipientPersonalCode());
  }

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
    return giftLinks.saveAndFlush(
        GiftLink.builder()
            .token(mintToken())
            .recipientPersonalCode(childPersonalCode)
            .openForRecipient(childPersonalCode)
            .createdByPersonalCode(parentPersonalCode)
            .createdAt(clock.instant())
            .build());
  }
}
