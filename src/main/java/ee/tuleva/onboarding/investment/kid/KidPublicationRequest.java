package ee.tuleva.onboarding.investment.kid;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.List;

record KidPublicationRequest(
    @NotBlank @Size(max = 200) String publishedBy,
    @NotEmpty List<@Valid @NotNull MonthToPublish> months) {

  record MonthToPublish(@NotNull YearMonth month, @NotNull Long calculationId) {}
}
