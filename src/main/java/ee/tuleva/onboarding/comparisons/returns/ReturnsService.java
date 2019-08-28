package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class ReturnsService {

    private final List<ReturnProvider> returnProviders;

    public Returns get(Person person, LocalDate fromDate) {
        int pillar = 2; // TODO
        Instant fromTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Return> returns = returnProviders.stream()
            .map(returnProvider -> returnProvider.getReturns(person, fromTime, pillar).getReturns())
            .flatMap(List::stream)
            .collect(toList());

        return Returns.builder()
            .from(fromDate)
            .returns(returns)
            .build();
    }
}
