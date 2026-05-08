package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Profile({"!dev & !staging"})
public class FundNavRetrieverFactory {

  // Tuleva calculates its own NAV for these funds and writes provider=TULEVA to index_values
  // at the plain ISIN key. Route the parallel PENSIONIKESKUS feed to a suffixed key so the
  // official-source data still lands (for audit/reconciliation) without being shadowed.
  static final String TULEVA_PENSIONIKESKUS_SUFFIX = ":PENSIONIKESKUS";
  private static final Set<String> TULEVA_ISINS =
      Arrays.stream(TulevaFund.values()).map(TulevaFund::getIsin).collect(toUnmodifiableSet());

  private final FundRepository fundRepository;
  private final EpisService episService;

  public List<ComparisonIndexRetriever> createAll() {
    return fundRepository.findAllByStatus(ACTIVE).stream()
        .map(Fund::getIsin)
        .peek(isin -> log.info("Creating Fund NAV retriever for {}", isin))
        .map(isin -> new FundNavRetriever(episService, isin, storageKeyFor(isin)))
        .collect(toList());
  }

  private static String storageKeyFor(String isin) {
    return TULEVA_ISINS.contains(isin) ? isin + TULEVA_PENSIONIKESKUS_SUFFIX : isin;
  }
}
