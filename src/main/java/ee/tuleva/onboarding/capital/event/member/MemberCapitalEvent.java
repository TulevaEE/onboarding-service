package ee.tuleva.onboarding.capital.event.member;

import static jakarta.persistence.EnumType.STRING;

import ee.tuleva.onboarding.user.member.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@Table(name = "member_capital_event")
@AllArgsConstructor
@NoArgsConstructor
public class MemberCapitalEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @NotNull
  @Enumerated(STRING)
  private MemberCapitalEventType type;

  @NotNull private BigDecimal fiatValue;

  @NotNull private BigDecimal ownershipUnitAmount;

  @NotNull private LocalDate accountingDate;

  @NotNull private LocalDate effectiveDate;
}
