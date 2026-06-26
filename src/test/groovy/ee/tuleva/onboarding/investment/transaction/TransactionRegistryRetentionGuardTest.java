package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.util.ClassUtils;

/**
 * Codified retention guard for the transaction registry (R5).
 *
 * <p><b>Regulatory basis:</b> transaction registry records must be retained for at least 5 years
 * (Art 16 record-keeping; Tuleva sisekord 04/11). Retention is currently met only by the
 * <i>absence</i> of any purge/erasure path against the registry tables:
 *
 * <ul>
 *   <li>{@code investment_transaction_batch} ({@link TransactionBatch})
 *   <li>{@code investment_transaction_order} ({@link TransactionOrder})
 *   <li>{@code investment_transaction_execution} ({@link TransactionExecution})
 *   <li>{@code investment_transaction_command} ({@link TransactionCommand})
 *   <li>{@code investment_transaction_audit_event} ({@link TransactionAuditEvent})
 *   <li>{@code transaction_settlement} ({@link TransactionSettlement})
 *   <li>{@code investment_portfolio_cost_basis} (portfolio cost-basis ledger)
 * </ul>
 *
 * <p>This guard pins that absence. It scans every Spring Data {@link Repository} declared in the
 * transaction module and fails if one introduces a deletion path:
 *
 * <ul>
 *   <li>a derived delete-style method ({@code deleteBy*} / {@code removeBy*}), or
 *   <li>a {@code @Query}/{@code @Modifying} carrying a {@code DELETE} against a registry table.
 * </ul>
 *
 * <p>The {@code delete*} methods inherited from {@code JpaRepository} are intentionally out of
 * scope: they exist on every repository but are not called against these tables (no production code
 * references them today). A regression that adds a purge would have to declare one of the patterns
 * above (which fails this test) or wire an inherited {@code delete*} call (which review must
 * catch); either way the retention obligation stops being silent and becomes self-documenting here.
 */
class TransactionRegistryRetentionGuardTest {

  private static final String TRANSACTION_PACKAGE = "ee.tuleva.onboarding.investment.transaction";

  private static final Pattern DELETE_AGAINST_REGISTRY =
      Pattern.compile(
          "delete\\s+(from\\s+)?(investment_transaction\\w*|transaction_settlement"
              + "|investment_portfolio_cost_basis|transactionbatch|transactionorder"
              + "|transactionexecution|transactioncommand|transactionauditevent"
              + "|transactionsettlement|portfoliocostbasis|portfoliobaseline)",
          Pattern.CASE_INSENSITIVE);

  private final List<Class<?>> transactionRepositories = findTransactionRepositories();

  @Test
  void transactionRepositoriesAreDiscovered() {
    assertThat(transactionRepositories)
        .as("transaction module Spring Data repositories")
        .extracting(Class::getSimpleName)
        .contains(
            "TransactionOrderRepository",
            "TransactionExecutionRepository",
            "TransactionBatchRepository",
            "TransactionCommandRepository",
            "TransactionAuditEventRepository",
            "TransactionSettlementRepository",
            "PortfolioCostBasisRepository");
  }

  @Test
  void noTransactionRepositoryDeclaresADerivedDeleteOrRemoveMethod() {
    List<String> offenders =
        transactionRepositories.stream()
            .flatMap(repository -> List.of(repository.getDeclaredMethods()).stream())
            .filter(method -> isDeleteOrRemoveDerivedMethod(method.getName()))
            .map(TransactionRegistryRetentionGuardTest::describe)
            .toList();

    assertThat(offenders)
        .as(
            "transaction registry repositories must not declare derived delete/remove methods "
                + "(Art 16 / sisekord 04/11 >=5-year retention)")
        .isEmpty();
  }

  @Test
  void noTransactionRepositoryDeclaresADeleteQueryAgainstRegistryTables() {
    List<String> offenders =
        transactionRepositories.stream()
            .flatMap(repository -> List.of(repository.getDeclaredMethods()).stream())
            .filter(TransactionRegistryRetentionGuardTest::isDeletingQuery)
            .map(TransactionRegistryRetentionGuardTest::describe)
            .toList();

    assertThat(offenders)
        .as(
            "no @Query/@Modifying may target a registry table with DELETE "
                + "(Art 16 / sisekord 04/11 >=5-year retention)")
        .isEmpty();
  }

  private static List<Class<?>> findTransactionRepositories() {
    var scanner =
        new ClassPathScanningCandidateComponentProvider(false) {
          @Override
          protected boolean isCandidateComponent(
              org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
            return definition.getMetadata().isInterface();
          }
        };
    scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
    return scanner.findCandidateComponents(TRANSACTION_PACKAGE).stream()
        .map(BeanDefinition::getBeanClassName)
        .<Class<?>>map(
            name ->
                ClassUtils.resolveClassName(
                    name, TransactionRegistryRetentionGuardTest.class.getClassLoader()))
        .filter(Class::isInterface)
        .filter(Repository.class::isAssignableFrom)
        .toList();
  }

  private static boolean isDeleteOrRemoveDerivedMethod(String methodName) {
    String lower = methodName.toLowerCase(Locale.ROOT);
    return lower.startsWith("deleteby") || lower.startsWith("removeby");
  }

  private static boolean isDeletingQuery(Method method) {
    Query query = method.getAnnotation(Query.class);
    if (query != null && DELETE_AGAINST_REGISTRY.matcher(query.value()).find()) {
      return true;
    }
    return method.isAnnotationPresent(Modifying.class)
        && isDeleteOrRemoveDerivedMethod(method.getName());
  }

  private static String describe(Method method) {
    return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
  }
}
