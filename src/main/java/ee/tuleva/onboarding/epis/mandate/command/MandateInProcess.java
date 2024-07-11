package ee.tuleva.onboarding.epis.mandate.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Getter
public abstract class MandateInProcess {
  private final String processId;
}
