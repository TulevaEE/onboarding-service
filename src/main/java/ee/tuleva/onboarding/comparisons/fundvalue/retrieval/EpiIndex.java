package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum EpiIndex {
  EPI("EPI", "EPI-II"),
  EPI_3("EPI_3", "EPI-III");

  private final String key;
  private final String value;
}
