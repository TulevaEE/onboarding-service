package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_PAYMENT_RATE;
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_FEES;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RAISE;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_RECURRING;
import static ee.tuleva.onboarding.nudge.NudgeKey.THIRD_PILLAR_START;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NudgeDecisionSerializationTest {

  private static final Path CONTRACT = Path.of("src/test/resources/nudge/nudge-decisions.json");

  private final JsonMapper mapper = JsonMapper.builder().build();

  private static List<NudgeDecision> everyDecisionShape() {
    var decisions = new ArrayList<NudgeDecision>();
    decisions.add(
        NudgeDecision.secondPillarTransfer(new FeeComparison(new BigDecimal("0.65"), 130, 56, 74)));
    decisions.add(NudgeDecision.secondPillarTransfer(null));
    decisions.add(NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE));
    decisions.add(NudgeDecision.of(NudgeKey.THIRD_PILLAR_START));
    decisions.add(NudgeDecision.of(NudgeKey.THIRD_PILLAR_FEES));
    decisions.add(NudgeDecision.of(NudgeKey.THIRD_PILLAR_RECURRING));
    decisions.add(NudgeDecision.of(NudgeKey.THIRD_PILLAR_RAISE));
    decisions.add(NudgeDecision.savingsFund(new BigDecimal("0.28")));
    decisions.add(NudgeDecision.of(NudgeKey.SAVINGS_FUND_RECURRING));
    decisions.add(NudgeDecision.of(NudgeKey.MEMBERSHIP));
    decisions.add(NudgeDecision.of(NudgeKey.NONE));
    decisions.add(
        NudgeDecision.of(NudgeKey.SECOND_PILLAR_PAYMENT_RATE)
            .withPaymentRateSeason(
                new PaymentRateSeason(
                    LocalDate.of(2026, 11, 30),
                    LocalDate.of(2027, 1, 1),
                    PaymentRateSeason.Mode.SEASON)));
    decisions.add(
        NudgeDecision.of(NudgeKey.THIRD_PILLAR_START)
            .withPaymentRateSeason(
                new PaymentRateSeason(
                    LocalDate.of(2026, 11, 30),
                    LocalDate.of(2027, 1, 1),
                    PaymentRateSeason.Mode.LAST_DAYS)));
    return decisions;
  }

  @Test
  void everyDecisionShapeSerializesExactlyAsTheContractFixtureTheClientDecodes()
      throws IOException {
    JsonNode contract = mapper.readTree(Files.readString(CONTRACT));

    List<String> serialized =
        everyDecisionShape().stream()
            .map(decision -> mapper.readTree(mapper.writeValueAsString(decision)).toString())
            .toList();
    List<String> expected = contract.valueStream().map(JsonNode::toString).toList();

    assertThat(serialized).containsExactlyElementsOf(expected);
  }

  @Test
  void theContractCoversEveryNudgeKey() throws IOException {
    JsonNode contract = mapper.readTree(Files.readString(CONTRACT));

    List<String> keysInContract =
        contract.valueStream().map(node -> node.get("key").asString()).distinct().toList();

    assertThat(keysInContract)
        .containsExactlyInAnyOrderElementsOf(stream(NudgeKey.values()).map(Enum::name).toList());
  }

  @Test
  void mergeVarsRaiseExactlyOneChainFlag() {
    Map<String, Object> vars =
        NudgeDecision.of(NudgeKey.THIRD_PILLAR_RECURRING).mergeVars(Locale.ENGLISH);

    assertThat(vars)
        .containsEntry("suggestSecondPillar", false)
        .containsEntry("suggestPaymentRate", false)
        .containsEntry("suggestThirdPillar", false)
        .containsEntry("thirdPillarActive", false)
        .containsEntry("suggestThirdPillarRecurringPayment", true)
        .containsEntry("suggestThirdPillarRaise", false)
        .containsEntry("suggestSavingsFund", false)
        .containsEntry("suggestSavingsFundRecurringPayment", false)
        .containsEntry("suggestMembership", false)
        .containsEntry("hasFeeComparison", false)
        .containsEntry("anyPillarSuggestion", true)
        .doesNotContainKeys("savingsFundFee", "secondPillarFeePercent");
  }

  @Test
  void anyPillarSuggestionIsRaisedForPensionPillarNudgesOnly() {
    List<NudgeKey> flagged =
        stream(NudgeKey.values())
            .filter(
                key ->
                    Boolean.TRUE.equals(
                        NudgeDecision.of(key).mergeVars(Locale.ENGLISH).get("anyPillarSuggestion")))
            .toList();

    assertThat(flagged)
        .containsExactly(
            SECOND_PILLAR_TRANSFER,
            SECOND_PILLAR_PAYMENT_RATE,
            THIRD_PILLAR_START,
            THIRD_PILLAR_FEES,
            THIRD_PILLAR_RECURRING,
            THIRD_PILLAR_RAISE);
  }

  @Test
  void anyPillarSuggestionStaysRaisedWhenTheSecondPillarNudgeCarriesAFeeComparison() {
    NudgeDecision transfer =
        NudgeDecision.secondPillarTransfer(new FeeComparison(new BigDecimal("0.65"), 130, 56, 74));

    assertThat(transfer.mergeVars(Locale.ENGLISH)).containsEntry("anyPillarSuggestion", true);
  }

  @Test
  void thirdPillarFeesRenderTheActiveBranchOfTheThirdPillarPartial() {
    Map<String, Object> vars =
        NudgeDecision.of(NudgeKey.THIRD_PILLAR_FEES).mergeVars(Locale.ENGLISH);

    assertThat(vars)
        .containsEntry("suggestThirdPillar", true)
        .containsEntry("thirdPillarActive", true);
  }

  @Test
  void feeComparisonAndSavingsFundFeeAreFormattedForTheLocale() {
    NudgeDecision transfer =
        NudgeDecision.secondPillarTransfer(new FeeComparison(new BigDecimal("0.65"), 130, 56, 74));
    NudgeDecision savingsFund = NudgeDecision.savingsFund(new BigDecimal("0.28"));

    assertThat(transfer.mergeVars(Locale.of("et")))
        .containsEntry("hasFeeComparison", true)
        .containsEntry("secondPillarFeePercent", "0,65")
        .containsEntry("secondPillarFeeAmount", 130L)
        .containsEntry("tulevaFeeAmount", 56L)
        .containsEntry("secondPillarSavingsAmount", 74L);
    assertThat(transfer.mergeVars(Locale.ENGLISH)).containsEntry("secondPillarFeePercent", "0.65");
    assertThat(savingsFund.mergeVars(Locale.of("et"))).containsEntry("savingsFundFee", "0,28");
    assertThat(savingsFund.mergeVars(Locale.ENGLISH)).containsEntry("savingsFundFee", "0.28");
  }
}
