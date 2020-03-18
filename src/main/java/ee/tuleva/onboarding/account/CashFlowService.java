package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CashFlowService {

  private static final LocalDate BEGINNING_OF_TIME = LocalDate.parse("1900-01-01");

  private final EpisService episService;

  public CashFlowStatement getCashFlowStatement(Person person) {
    return getCashFlowStatement(person, BEGINNING_OF_TIME, LocalDate.now());
  }

  public CashFlowStatement getCashFlowStatement(
      Person person, LocalDate fromDate, LocalDate toDate) {
    return episService.getCashFlowStatement(person, fromDate, toDate);
  }
}
