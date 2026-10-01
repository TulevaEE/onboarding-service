package ee.tuleva.onboarding.accounting.directo;

import static ee.tuleva.onboarding.accounting.directo.DirectoFields.require;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.UNICODE_CASE;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

final class PayrollProtection {

  private static final Pattern NAME_OF_A_PAYROLL_LIABILITY =
      Pattern.compile(
          "pal[gk]|töövõtja|töötaja|puhkus|erisoodus|päevaraha|lähetus|haigus|aruandv",
          CASE_INSENSITIVE | UNICODE_CASE);

  private static final Set<String> PAYROLL_LIABILITY_ACCOUNTS_OF_THE_COMPANY_CHART =
      Set.of("212344", "212401", "212411", "212421", "212422", "212641", "212647", "212648");

  private final Set<String> payrollLiabilityAccounts;
  private final Set<String> knownObjects;
  private final Set<String> employeeObjects;

  PayrollProtection(List<DirectoAccount> accounts, List<DirectoObject> objects) {
    final String EMPLOYEE_OBJECT_LEVEL = "50";
    payrollLiabilityAccounts =
        Stream.concat(
                PAYROLL_LIABILITY_ACCOUNTS_OF_THE_COMPANY_CHART.stream(),
                accounts.stream()
                    .filter(PayrollProtection::isPayrollLiability)
                    .map(account -> require(account.code(), "accounts.code")))
            .collect(toSet());
    knownObjects = objects.stream().map(PayrollProtection::code).collect(toSet());
    employeeObjects =
        objects.stream()
            .filter(object -> EMPLOYEE_OBJECT_LEVEL.equals(trimmed(object.level())))
            .map(PayrollProtection::code)
            .collect(toSet());
  }

  boolean protects(Document document) {
    final String PAYROLL_DOCUMENT_TYPE = "PALK";
    return PAYROLL_DOCUMENT_TYPE.equals(document.type())
        || document.rows().stream().anyMatch(this::protects);
  }

  private boolean protects(DocumentRow row) {
    final String PAYROLL_ACCOUNT_PREFIX = "54";
    return row.accountCode().startsWith(PAYROLL_ACCOUNT_PREFIX)
        || payrollLiabilityAccounts.contains(row.accountCode())
        || row.objectCodes().stream()
            .anyMatch(code -> employeeObjects.contains(code) || !knownObjects.contains(code));
  }

  private static boolean isPayrollLiability(DirectoAccount account) {
    final String LIABILITY_CLASS = "1";
    var name = account.name();
    return LIABILITY_CLASS.equals(trimmed(account.accountClass()))
        && name != null
        && NAME_OF_A_PAYROLL_LIABILITY.matcher(name).find();
  }

  private static String code(DirectoObject object) {
    return require(object.code(), "objects.code").trim();
  }

  private static @Nullable String trimmed(@Nullable String value) {
    return value == null ? null : value.trim();
  }
}
