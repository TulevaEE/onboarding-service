package ee.tuleva.onboarding.accounting.directo;

import java.util.List;

public record DirectoBook(
    List<DirectoAccount> accounts,
    List<DirectoObject> objects,
    List<DirectoTransaction> transactions) {}
