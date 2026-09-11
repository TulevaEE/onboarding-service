package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class LimitCheckEventWriter {

  private final LimitCheckEventRepository limitCheckEventRepository;

  @Transactional
  void replaceEvents(TulevaFund fund, LocalDate checkDate, List<LimitCheckEvent> events) {
    events.forEach(
        event ->
            limitCheckEventRepository.deleteByFundAndCheckDateAndCheckType(
                fund, checkDate, event.getCheckType()));
    limitCheckEventRepository.saveAll(events);
  }
}
