package ee.tuleva.onboarding.savings.fund.gift;

import static jakarta.persistence.GenerationType.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * A capability that lets whoever holds the token pay into one child's savings fund account without
 * logging in.
 *
 * <p>The link says who the money is for and nothing else. It does not decide whether the deposit is
 * allowed: that is settled when the payment arrives, by the same rule that governs every other
 * third-party deposit. A link for a child who has since turned 18 therefore still opens, and the
 * gift is returned instead.
 */
@Entity
@Table(name = "savings_fund_gift_link")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// The token is the whole secret, so it has no business appearing in a log line.
@ToString(exclude = "token")
public class GiftLink {

  // Crockford's base32 alphabet: no I, L, O or U, so 1/I and 0/O cannot be transposed when someone
  // reads a link aloud, and nothing accidentally spells a word.
  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int ENTROPY_BYTES = 16;
  private static final SecureRandom RANDOM = new SecureRandom();

  @Id
  @GeneratedValue(strategy = UUID)
  private java.util.UUID id;

  @NotNull
  @Column(nullable = false)
  private String token;

  @NotNull
  @Column(nullable = false)
  private String recipientPersonalCode;

  @NotNull
  @Column(nullable = false)
  private String createdByPersonalCode;

  @Column(nullable = false, updatable = false)
  private @Nullable Instant createdAt;

  private @Nullable Instant closedAt;

  public boolean isOpen() {
    return closedAt == null;
  }

  void close(Instant at) {
    this.closedAt = at;
  }

  /**
   * 128 bits, because the token is the only thing standing between a stranger and somebody's gift
   * page. Short and memorable would be guessable, and there is nothing here worth guessing for
   * except a child's first name.
   */
  static String mintToken() {
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
}
