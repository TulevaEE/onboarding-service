package ee.tuleva.onboarding.capital.transfer.execution;

import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "capital_transfer_event_link")
@AllArgsConstructor
@NoArgsConstructor
public class CapitalTransferEventLink {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "capital_transfer_contract_id", nullable = false)
  private CapitalTransferContract capitalTransferContract;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_capital_event_id", nullable = false, unique = true)
  private MemberCapitalEvent memberCapitalEvent;

  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now(ClockHolder.getClock());
  }
}
