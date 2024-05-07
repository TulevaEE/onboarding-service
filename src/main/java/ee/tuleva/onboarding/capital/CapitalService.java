package ee.tuleva.onboarding.capital;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_DOWN;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEvent;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapitalService {

  private final MemberCapitalEventRepository memberCapitalEventRepository;
  private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;

  List<CapitalRow> getCapitalRows(Long memberId) {
    List<MemberCapitalEvent> events = memberCapitalEventRepository.findAllByMemberId(memberId);

    Map<MemberCapitalEventType, CapitalRow> grouped =
        events.stream()
            .filter(pastEvents())
            .collect(
                groupingBy(
                    MemberCapitalEvent::getType,
                    reducing(
                        new CapitalRow(null, ZERO, ZERO, EUR),
                        event ->
                            new CapitalRow(
                                event.getType(),
                                event.getFiatValue(),
                                getProfit(List.of(event)),
                                EUR),
                        (CapitalRow a, CapitalRow b) ->
                            new CapitalRow(
                                a.type() != null ? a.type() : b.type(),
                                a.contributions().add(b.contributions()),
                                a.profit().add(b.profit()),
                                EUR))));

    return grouped.values().stream()
        .map(
            row ->
                new CapitalRow(
                    row.type(),
                    row.contributions().setScale(2, HALF_DOWN),
                    row.profit().setScale(2, HALF_DOWN),
                    row.currency()))
        .toList();
  }

  CapitalStatement getCapitalStatement(Long memberId) {
    List<MemberCapitalEvent> events = memberCapitalEventRepository.findAllByMemberId(memberId);

    return new CapitalStatement(
        getCapitalAmount(events, List.of(MEMBERSHIP_BONUS)),
        getCapitalAmount(events, List.of(CAPITAL_PAYMENT)),
        getCapitalAmount(events, List.of(UNVESTED_WORK_COMPENSATION)),
        getCapitalAmount(events, List.of(WORK_COMPENSATION)),
        getProfit(events),
        EUR);
  }

  @NotNull
  private BigDecimal getCapitalAmount(
      List<MemberCapitalEvent> events, List<MemberCapitalEventType> eventTypes) {
    return events.stream()
        .filter(event -> eventTypes.contains(event.getType()))
        .filter(pastEvents())
        .map(MemberCapitalEvent::getFiatValue)
        .reduce(ZERO, BigDecimal::add)
        .setScale(2, HALF_DOWN);
  }

  private BigDecimal getProfit(List<MemberCapitalEvent> events) {

    BigDecimal totalFiatValue =
        events.stream()
            .filter(pastEvents())
            .map(MemberCapitalEvent::getFiatValue)
            .reduce(ZERO, BigDecimal::add);

    BigDecimal totalOwnershipUnitAmount =
        events.stream()
            .filter(pastEvents())
            .map(MemberCapitalEvent::getOwnershipUnitAmount)
            .reduce(ZERO, BigDecimal::add);

    AggregatedCapitalEvent latestAggregatedCapitalEvent =
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

    if (latestAggregatedCapitalEvent == null) {
      return ZERO;
    }

    BigDecimal investmentFiatValue =
        latestAggregatedCapitalEvent.getOwnershipUnitPrice().multiply(totalOwnershipUnitAmount);

    return investmentFiatValue.subtract(totalFiatValue).setScale(2, HALF_DOWN);
  }

  private Predicate<MemberCapitalEvent> pastEvents() {
    return event -> {
      LocalDate date = event.getAccountingDate();
      LocalDate now = LocalDate.now();
      return date.isBefore(now) || date.isEqual(now);
    };
  }
}
