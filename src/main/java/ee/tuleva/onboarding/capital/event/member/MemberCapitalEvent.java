package ee.tuleva.onboarding.capital.event.member;

import ee.tuleva.onboarding.user.member.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

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
    private MemberCapitalEventType type;

    @NotNull
    private BigDecimal fiatValue;

    @NotNull
    private BigDecimal ownershipUnitAmount;

}
