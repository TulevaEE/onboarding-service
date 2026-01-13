package ee.tuleva.onboarding.banking.message;

import ee.tuleva.onboarding.banking.BankType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "banking_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankingMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BankType bankType;

  private String requestId;
  private String trackingId;
  private String rawResponse;

  @Column(columnDefinition = "TIMESTAMPTZ")
  private Instant failedAt;

  @Column(columnDefinition = "TIMESTAMPTZ")
  private Instant processedAt;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant receivedAt;
}
