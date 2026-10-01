package ee.tuleva.onboarding.accounting.directo;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.ledger.JournalEntryPart;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class DirectoPartMapper {

  public static final String SOURCE = "DIRECTO";

  public GeneralLedgerBook map(DirectoBook book) {
    var documents = book.transactions().stream().map(Document::of).toList();
    refuseRepeatedDocuments(documents);
    PersonalCodeChecksum.stopIfAnyPasses(
        documents.stream().flatMap(Document::identifyingCodes).toList());
    var accounts =
        book.accounts().stream().map(DirectoAccountMapper::toGeneralLedgerAccount).toList();
    var protection = new PayrollProtection(book.accounts(), book.objects());
    var protectedParts =
        documents.stream().filter(protection::protects).flatMap(Document::parts).toList();
    var ordinaryParts =
        documents.stream().filter(not(protection::protects)).flatMap(Document::parts);
    return new GeneralLedgerBook(
        accounts,
        Stream.concat(ordinaryParts, PayrollSummary.of(protectedParts).stream()).toList(),
        protectedParts.stream().map(JournalEntryPart::sourceKey).collect(toUnmodifiableSet()));
  }

  private static void refuseRepeatedDocuments(List<Document> documents) {
    long repeated =
        documents.stream().collect(groupingBy(Document::identity, counting())).values().stream()
            .filter(count -> count > 1)
            .count();
    if (repeated > 0) {
      throw new IllegalStateException(
          "Directo documents repeat a type and number: count=" + repeated);
    }
  }
}
