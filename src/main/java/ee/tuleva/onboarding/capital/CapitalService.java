package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CapitalService {

    private final MemberCapitalEventRepository memberCapitalEventRepository;
    private final OrganisationCapitalEventRepository organisationCapitalEventRepository;
    private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;

    CapitalStatement getCapitalStatement(Long memberId) {
        List<MemberCapitalEvent> events = memberCapitalEventRepository.findAllByMemberId(memberId);

        BigDecimal capitalPaymentAmount =
            getCapitalAmount(events, MemberCapitalEventType.CAPITAL_PAYMENT);

        BigDecimal membershipBonusAmount =
            getCapitalAmount(events, MemberCapitalEventType.MEMBERSHIP_BONUS);

        BigDecimal unvestedWorkCompensationAmount =
            getCapitalAmount(events, MemberCapitalEventType.UNVESTED_WORK_COMPENSATION);

        BigDecimal workCompensationAmount =
            getCapitalAmount(events, MemberCapitalEventType.WORK_COMPENSATION);


       return new CapitalStatement(
           membershipBonusAmount, capitalPaymentAmount,
           unvestedWorkCompensationAmount, workCompensationAmount,
           getProfit(events)
       );
    }

    @NotNull
    private BigDecimal getCapitalAmount(List<MemberCapitalEvent> events, MemberCapitalEventType capitalPayment) {
        return events.stream().filter(event -> event.getType() == capitalPayment)
            .map(MemberCapitalEvent::getFiatValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add).round(new MathContext(2));
    }

    private BigDecimal getProfit(List<MemberCapitalEvent> events) {

        BigDecimal totalFiatValue = events.stream()
            .filter(event -> event.getEffectiveDate().compareTo(LocalDate.now()) < 1)
            .map(MemberCapitalEvent::getFiatValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOwnershipUnitAmount = events.stream()
            .filter(event -> event.getEffectiveDate().compareTo(LocalDate.now()) < 1)
            .map(MemberCapitalEvent::getOwnershipUnitAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        AggregatedCapitalEvent latestAggregatedCapitalEvent =
            aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

        BigDecimal investmentFiatValue =
            latestAggregatedCapitalEvent.getOwnershipUnitPrice().multiply(totalOwnershipUnitAmount);

        return investmentFiatValue.subtract(totalFiatValue).round(new MathContext(2));
    }
}
