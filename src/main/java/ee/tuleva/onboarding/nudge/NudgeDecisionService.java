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
  private final OfflineNudgeInputs offlineInputs;
  private final SecurityContextRunner securityContextRunner;

  public NudgeDecision decide(User user, NudgeContext context) {
    return decide(user, NudgeAccount.self(user), context);
  }

  public NudgeDecision decide(User user, NudgeAccount actingParty, NudgeContext context) {
    return decided(user, context, inputsFor(user, actingParty, context));
  }

  NudgeInputs inputsFor(User user) {
    return inputsFor(user, NudgeAccount.self(user), NudgeContext.ACCOUNT);
  }

  private NudgeInputs inputsFor(User user, NudgeAccount actingParty, NudgeContext context) {
    return securityContextRunner.callAs(
        user, () -> inputsAssembler.assemble(user, actingParty, context));
  }

  public NudgeDecision decideOffline(User user, NudgeContext context) {
    return decided(user, context, offlineInputs.assemble(user, context));
  }

  private NudgeDecision decided(User user, NudgeContext context, NudgeInputs inputs) {
    NudgeDecision decision = NudgeRules.decide(inputs, context);
    log.info(
        "Nudge decided: userId={}, context={}, nudge={}", user.getId(), context, decision.key());
    return decision;
  }
}
