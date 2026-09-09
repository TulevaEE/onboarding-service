package ee.tuleva.onboarding.nudge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NudgeChainOrderTest {

  private static final Pattern BRANCH = Pattern.compile("\\*\\|(?:IF|ELSEIF):(\\w+)\\|\\*");

  @ParameterizedTest
  @ValueSource(strings = {"et", "en"})
  void theEmailChainRendersNudgesInTheSameOrderTheRulesDecideThem(String language)
      throws IOException {
    String partial =
        Files.readString(Path.of("emails/src/partials/suggest_chain_open_" + language + ".mjml"));

    Matcher matcher = BRANCH.matcher(partial);
    var branches = new java.util.ArrayList<String>();
    while (matcher.find()) {
      branches.add(matcher.group(1));
    }

    assertThat(branches)
        .containsExactly(
            "suggestSecondPillar",
            "suggestPaymentRate",
            "suggestThirdPillar",
            "suggestThirdPillarRecurringPayment",
            "suggestThirdPillarRaise",
            "suggestSavingsFund",
            "suggestSavingsFundRecurringPayment",
            "suggestMembership");
    assertThat(chainFlagsInRuleOrder()).isEqualTo(branches);
  }

  private static List<String> chainFlagsInRuleOrder() {
    return java.util.Arrays.stream(NudgeKey.values())
        .filter(key -> key != NudgeKey.ACCOUNT_RECURRING && key != NudgeKey.NONE)
        .map(key -> NudgeDecision.of(key).mergeVars(java.util.Locale.ENGLISH))
        .map(
            vars ->
                vars.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("suggest"))
                    .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                    .map(java.util.Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow())
        .distinct()
        .toList();
  }
}
