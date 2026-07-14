package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class FundFeeUpdater {

  private static final BigDecimal MIN_FEE = BigDecimal.ZERO;
  private static final BigDecimal MAX_FEE = new BigDecimal("0.02");
  private static final char NON_BREAKING_SPACE = (char) 0xA0;

  private final FundRepository fundRepository;

  FundFeeUpdater(FundRepository fundRepository) {
    this.fundRepository = fundRepository;
  }

  void update(int pillar, List<PensionikeskusFeeRow> rows, FeeField field) {
    Map<String, List<Fund>> fundsByName =
        fundRepository.findAllByPillarAndStatus(pillar, ACTIVE).stream()
            .collect(groupingBy(fund -> normalize(fund.getNameEstonian())));
    Map<String, List<PensionikeskusFeeRow>> rowsByName =
        rows.stream().collect(groupingBy(row -> normalize(row.fundName())));

    fundsByName.forEach(
        (name, funds) -> {
          try {
            updateFund(pillar, name, funds, rowsByName, field);
          } catch (Exception e) {
            log.error("Failed to update fund fee: pillar={}, name={}", pillar, name, e);
          }
        });

    rowsByName.keySet().stream()
        .filter(name -> !fundsByName.containsKey(name))
        .forEach(
            name -> log.error("Fee row matches no active fund: pillar={}, name={}", pillar, name));
  }

  private void updateFund(
      int pillar,
      String name,
      List<Fund> funds,
      Map<String, List<PensionikeskusFeeRow>> rowsByName,
      FeeField field) {
    if (funds.size() > 1) {
      log.error("Ambiguous fund name, skipping: pillar={}, name={}", pillar, name);
      return;
    }
    Fund fund = funds.getFirst();
    List<PensionikeskusFeeRow> matchingRows = rowsByName.getOrDefault(name, List.of());
    if (matchingRows.isEmpty()) {
      log.error(
          "No fee row for active fund: field={}, isin={}, name={}", field, fund.getIsin(), name);
      return;
    }
    if (matchingRows.size() > 1) {
      log.error("Ambiguous fee row, skipping: pillar={}, name={}", pillar, name);
      return;
    }
    applyUpdate(fund, matchingRows.getFirst().rate(), field);
  }

  private void applyUpdate(Fund fund, BigDecimal newValue, FeeField field) {
    if (newValue.compareTo(MIN_FEE) <= 0 || newValue.compareTo(MAX_FEE) > 0) {
      log.error(
          "Fee value out of bounds, skipping: field={}, isin={}, value={}",
          field,
          fund.getIsin(),
          newValue);
      return;
    }
    BigDecimal current = field.get(fund);
    if (current.compareTo(newValue) == 0) {
      return;
    }
    field.set(fund, newValue);
    fundRepository.save(fund);
    log.info(
        "Fund fee updated: field={}, isin={}, old={}, new={}",
        field,
        fund.getIsin(),
        current,
        newValue);
  }

  private static String normalize(String name) {
    return name.replace(NON_BREAKING_SPACE, ' ')
        .strip()
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  enum FeeField {
    MANAGEMENT_FEE(Fund::getManagementFeeRate, Fund::setManagementFeeRate),
    ONGOING_CHARGES(Fund::getOngoingChargesFigure, Fund::setOngoingChargesFigure);

    private final Function<Fund, BigDecimal> getter;
    private final BiConsumer<Fund, BigDecimal> setter;

    FeeField(Function<Fund, BigDecimal> getter, BiConsumer<Fund, BigDecimal> setter) {
      this.getter = getter;
      this.setter = setter;
    }

    BigDecimal get(Fund fund) {
      return getter.apply(fund);
    }

    void set(Fund fund, BigDecimal value) {
      setter.accept(fund, value);
    }
  }
}
