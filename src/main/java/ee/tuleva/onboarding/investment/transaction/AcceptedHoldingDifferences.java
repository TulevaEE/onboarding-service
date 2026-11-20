package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record AcceptedHoldingDifferences(
    @NotEmpty Set<@NotNull @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{9}[0-9]") String> isins,
    @NotBlank @Size(max = 500) String comment) {

  public AcceptedHoldingDifferences {
    isins = isins == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(isins));
  }

  List<String> sortedIsins() {
    return isins.stream().sorted().toList();
  }
}
