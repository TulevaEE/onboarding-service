package ee.tuleva.onboarding.taxwin;

import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class TaxIncomeStore {

  private final TaxIncomeMonthRepository incomeMonthRepository;
  private final TaxIncomeCorrectionRepository correctionRepository;

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
    LocalDate incomeMonth = month.atDay(1);
    correctionRepository
        .findByUserIdAndIncomeMonth(userId, incomeMonth)
        .ifPresentOrElse(
            correction -> correction.correctTo(amount),
            () -> correctionRepository.save(new TaxIncomeCorrection(userId, month, amount)));
  }

  @Transactional
  void correctAll(Long userId, Map<YearMonth, BigDecimal> amounts) {
    amounts.forEach((month, amount) -> correct(userId, month, amount));
  }

  @Transactional
  void removeCorrection(Long userId, YearMonth month) {
    correctionRepository
        .findByUserIdAndIncomeMonth(userId, month.atDay(1))
        .ifPresent(correctionRepository::delete);
  }

  @Transactional
  void deleteAll(Long userId) {
    incomeMonthRepository.deleteAllByUserId(userId);
    correctionRepository.deleteAllByUserId(userId);
  }
}
