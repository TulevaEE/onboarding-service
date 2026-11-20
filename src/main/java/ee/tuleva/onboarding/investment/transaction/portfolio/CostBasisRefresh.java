package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh.Outcome.FAILED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh.Outcome.LOCK_NOT_TAKEN;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh.Outcome.NO_BASELINE;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh.Outcome.REFRESHED;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record CostBasisRefresh(Outcome outcome, @Nullable String failure) {

  private static final String THE_STORED_LEDGER_WAS_COMPARED =
      " Võrdluses on pearaamatu salvestatud seis, kust võivad puududa pärast viimast"
          + " ümberarvutust saabunud tehingud.";

  public CostBasisRefresh {
    if ((outcome == FAILED) != (failure != null)) {
      throw new IllegalArgumentException(
          "Only a failed refresh carries a failure: outcome=" + outcome + ", failure=" + failure);
    }
  }

  public enum Outcome {
    REFRESHED,
    NO_BASELINE,
    LOCK_NOT_TAKEN,
    FAILED
  }

  public static CostBasisRefresh refreshed() {
    return new CostBasisRefresh(REFRESHED, null);
  }

  public static CostBasisRefresh noBaseline() {
    return new CostBasisRefresh(NO_BASELINE, null);
  }

  public static CostBasisRefresh lockNotTaken() {
    return new CostBasisRefresh(LOCK_NOT_TAKEN, null);
  }

  public static CostBasisRefresh failed(String failure) {
    return new CostBasisRefresh(FAILED, failure);
  }

  public Optional<String> staleLedgerNote() {
    return switch (outcome) {
      case REFRESHED, NO_BASELINE -> Optional.empty();
      case LOCK_NOT_TAKEN ->
          Optional.of(
              "Cost-basis pearaamatut ei arvutatud enne võrdlust ümber, sest teine cost-basis töö"
                  + " jooksis samal ajal."
                  + THE_STORED_LEDGER_WAS_COMPARED);
      case FAILED ->
          Optional.of(
              "Cost-basis pearaamatu ümberarvutus enne võrdlust ebaõnnestus: "
                  + failure
                  + "."
                  + THE_STORED_LEDGER_WAS_COMPARED);
    };
  }
}
