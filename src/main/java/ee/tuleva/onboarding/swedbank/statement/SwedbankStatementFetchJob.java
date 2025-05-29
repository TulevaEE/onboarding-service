package ee.tuleva.onboarding.swedbank.statement;


import ee.tuleva.onboarding.ledger.LedgerTransaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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

  @Column()
  private UUID trackingId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private JobStatus jobStatus;


  @Column(columnDefinition = "TIMESTAMPTZ")
  private Instant lastCheckAt;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

  public enum JobStatus {
    SCHEDULED,
    WAITING_FOR_REPLY,
    DONE,
    FAILED
  }



}
