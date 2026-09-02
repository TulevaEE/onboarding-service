package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four rows describe one run of the check over one fund and date, so they are replaced as one.
 * Written a row at a time, a failure partway leaves the date carrying two check types from this run
 * and two from the last one, with nothing recording that they disagree about when they were taken.
 *
 * <p>It is a component of its own rather than a method on the service because Spring's transaction
 * proxy only wraps calls that arrive from outside the bean — annotating a method the service calls
 * on itself would read as transactional and do nothing.
 */
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
