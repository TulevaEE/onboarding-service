package ee.tuleva.onboarding.capital;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static java.math.BigDecimal.ROUND_HALF_DOWN;

@Service
@RequiredArgsConstructor
public class CapitalService {

    private final MemberCapitalEventRepository memberCapitalEventRepository;
    private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;

    CapitalStatement getCapitalStatement(Long memberId) {
        List<MemberCapitalEvent> events = memberCapitalEventRepository.findAllByMemberId(memberId);

        return new CapitalStatement(
            getCapitalAmount(events, MEMBERSHIP_BONUS),
            getCapitalAmount(events, CAPITAL_PAYMENT),
            getCapitalAmount(events, UNVESTED_WORK_COMPENSATION),
            getCapitalAmount(events, WORK_COMPENSATION),
            getProfit(events)
        );
    }

    @NotNull
    private BigDecimal getCapitalAmount(List<MemberCapitalEvent> events, MemberCapitalEventType capitalPayment) {
        return events.stream().filter(event -> event.getType() == capitalPayment)
            .filter(event -> event.getAccountingDate().compareTo(LocalDate.now()) <= 0)
            .map(MemberCapitalEvent::getFiatValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, ROUND_HALF_DOWN);
    }

    private BigDecimal getProfit(List<MemberCapitalEvent> events) {

        BigDecimal totalFiatValue = events.stream()
            .filter(event -> event.getAccountingDate().compareTo(LocalDate.now()) <= 0)
            .map(MemberCapitalEvent::getFiatValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOwnershipUnitAmount = events.stream()
            .filter(event -> event.getAccountingDate().compareTo(LocalDate.now()) <= 0)
            .map(MemberCapitalEvent::getOwnershipUnitAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        AggregatedCapitalEvent latestAggregatedCapitalEvent =
            aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

        BigDecimal investmentFiatValue =
            latestAggregatedCapitalEvent.getOwnershipUnitPrice().multiply(totalOwnershipUnitAmount);

        return investmentFiatValue.subtract(totalFiatValue).setScale(2, ROUND_HALF_DOWN);
    }
}
