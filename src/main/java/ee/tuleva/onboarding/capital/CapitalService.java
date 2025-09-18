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
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapitalService {
  private final MemberCapitalEventRepository memberCapitalEventRepository;

  private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;

  private static final BigDecimal CONCENTRATION_LIMIT_COEFFICIENT = new BigDecimal("0.1");

  public List<ApiCapitalEvent> getCapitalEvents(Long memberId) {
    return memberCapitalEventRepository.findAllByMemberId(memberId).stream()
        .map(
            event ->
                new ApiCapitalEvent(
                    event.getAccountingDate(),
                    event.getType(),
                    event.getFiatValue().setScale(2, HALF_DOWN),
                    EUR))
        .toList();
  }

  public List<CapitalRow> getCapitalRows(Long memberId) {
    List<MemberCapitalEvent> events = memberCapitalEventRepository.findAllByMemberId(memberId);

    var latestUnitPrice = getLatestOwnershipUnitPrice();

    Map<MemberCapitalEventType, CapitalRow> grouped =
        events.stream()
            .filter(pastEvents())
            .collect(
                groupingBy(
                    MemberCapitalEvent::getType,
                    reducing(
                        CapitalRow.empty(),
                        event ->
                            CapitalRow.from(event, latestUnitPrice.orElseThrow(), getProfit(event)),
                        CapitalRow::sum)));

    return grouped.values().stream().map(CapitalRow::rounded).toList();
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

  public BigDecimal getCapitalConcentrationUnitLimit() {
    var latestEvent =
        Optional.ofNullable(aggregatedCapitalEventRepository.findTopByOrderByDateDesc());

    if (latestEvent.isEmpty()) {
      return BigDecimal.valueOf(1e7); // TODO ?
    }

    return latestEvent
        .get()
        .getTotalOwnershipUnitAmount()
        .multiply(CONCENTRATION_LIMIT_COEFFICIENT); // TODO no subtract membrship capitl?
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

  private BigDecimal getProfit(MemberCapitalEvent event) {
    return getProfit(List.of(event));
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

    Optional<BigDecimal> latestUnitPrice = getLatestOwnershipUnitPrice();

    if (latestUnitPrice.isEmpty()) {
      return ZERO;
    }

    BigDecimal investmentFiatValue = latestUnitPrice.get().multiply(totalOwnershipUnitAmount);

    return investmentFiatValue.subtract(totalFiatValue).setScale(2, HALF_DOWN);
  }

  private Optional<BigDecimal> getLatestOwnershipUnitPrice() {
    return this.getLatestAggregatedCapitalEvent()
        .map(AggregatedCapitalEvent::getOwnershipUnitPrice);
  }

  public Optional<AggregatedCapitalEvent> getLatestAggregatedCapitalEvent() {
    AggregatedCapitalEvent latestAggregatedCapitalEvent =
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

    if (latestAggregatedCapitalEvent == null) {
      return Optional.empty();
    }

    return Optional.of(latestAggregatedCapitalEvent);
  }

  private Predicate<MemberCapitalEvent> pastEvents() {
    return event -> {
      LocalDate date = event.getAccountingDate();
      LocalDate now = LocalDate.now();
      return date.isBefore(now) || date.isEqual(now);
    };
  }
}
