package ee.tuleva.onboarding.swedbank.fetcher;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "swedbank_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwedbankMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @Column @Nullable private String requestId;
  @Column @Nullable private String trackingId;

  @Column @Nullable private String rawResponse;

  @Column(columnDefinition = "TIMESTAMPTZ")
  private Instant failedAt;

  @Column(columnDefinition = "TIMESTAMPTZ")
  private Instant processedAt;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant receivedAt;
}
