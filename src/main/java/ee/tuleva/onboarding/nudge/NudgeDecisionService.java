package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NudgeDecisionService {

  private final NudgeInputsAssembler inputsAssembler;
  private final SecurityContextRunner securityContextRunner;

  public NudgeDecision decide(User user, NudgeContext context) {
    return decide(user, NudgeAccount.self(user), context);
  }

  public NudgeDecision decide(User user, NudgeAccount actingParty, NudgeContext context) {
    NudgeInputs inputs =
        securityContextRunner.callAs(user, () -> inputsAssembler.assemble(user, actingParty));
    NudgeDecision decision = NudgeRules.decide(inputs, context);
    log.info(
        "Nudge decided: userId={}, context={}, nudge={}", user.getId(), context, decision.key());
    return decision;
  }
}
