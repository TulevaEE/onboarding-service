package ee.tuleva.onboarding.taxwin;

import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class TaxIncomeStore {

  private final TaxIncomeMonthRepository incomeMonthRepository;
  private final TaxIncomeCorrectionRepository correctionRepository;
  private final TaxExpectedYearlyIncomeRepository yearlyIncomeRepository;

  Map<YearMonth, DeclaredMonth> declared(Long userId) {
    return incomeMonthRepository.findAllByUserId(userId).stream()
        .map(TaxIncomeMonth::asEntry)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  Map<YearMonth, BigDecimal> corrections(Long userId) {
    return correctionRepository.findAllByUserId(userId).stream()
        .map(TaxIncomeCorrection::asEntry)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  Optional<YearlyIncome> yearlyIncome(Long userId) {
    return yearlyIncomeRepository.findByUserId(userId).map(TaxExpectedYearlyIncome::asYearlyIncome);
  }

  @Transactional
  void replaceDeclared(Long userId, Map<YearMonth, DeclaredMonth> declared) {
    incomeMonthRepository.deleteAllByUserId(userId);
    incomeMonthRepository.saveAll(
        declared.entrySet().stream()
            .map(month -> new TaxIncomeMonth(userId, month.getKey(), month.getValue()))
            .toList());
    if (!declared.isEmpty()) {
      correctionRepository.deleteByUserIdAndIncomeMonthIn(
          userId, declared.keySet().stream().map(month -> month.atDay(1)).toList());
    }
  }

  @Transactional
  void correct(Long userId, YearMonth month, BigDecimal amount) {
    yearlyIncomeRepository.deleteByUserId(userId);
    saveCorrection(userId, month, amount);
  }

  @Transactional
  void expectYearlyIncome(
      Long userId, YearlyIncome yearlyIncome, Map<YearMonth, BigDecimal> monthlyIncome) {
    yearlyIncomeRepository
        .findByUserId(userId)
        .ifPresentOrElse(
            expected -> expected.expect(yearlyIncome),
            () -> yearlyIncomeRepository.save(new TaxExpectedYearlyIncome(userId, yearlyIncome)));
    monthlyIncome.forEach((month, amount) -> saveCorrection(userId, month, amount));
  }

  @Transactional
  void forgetYearlyIncome(Long userId) {
    yearlyIncomeRepository.deleteByUserId(userId);
    correctionRepository.deleteAllByUserId(userId);
  }

  @Transactional
  void removeCorrection(Long userId, YearMonth month) {
    yearlyIncomeRepository.deleteByUserId(userId);
    correctionRepository
        .findByUserIdAndIncomeMonth(userId, month.atDay(1))
        .ifPresent(correctionRepository::delete);
  }

  @Transactional
  void deleteAll(Long userId) {
    incomeMonthRepository.deleteAllByUserId(userId);
    correctionRepository.deleteAllByUserId(userId);
    yearlyIncomeRepository.deleteByUserId(userId);
  }

  private void saveCorrection(Long userId, YearMonth month, BigDecimal amount) {
    correctionRepository
        .findByUserIdAndIncomeMonth(userId, month.atDay(1))
        .ifPresentOrElse(
            correction -> correction.correctTo(amount),
            () -> correctionRepository.save(new TaxIncomeCorrection(userId, month, amount)));
  }
}
