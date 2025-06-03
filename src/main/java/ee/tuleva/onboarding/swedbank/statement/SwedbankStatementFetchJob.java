package ee.tuleva.onboarding.swedbank.statement;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "swedbank_statement_fetch_job")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwedbankStatementFetchJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @Column
  private String trackingId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus jobStatus;

  @Column(columnDefinition = "TIMESTAMPTZ")
  private Instant lastCheckAt;

  @Column
  private String rawResponse;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

  public enum JobStatus {
    SCHEDULED,
    WAITING_FOR_REPLY,
    RESPONSE_RECEIVED,
    DONE,
    FAILED
  }
}
