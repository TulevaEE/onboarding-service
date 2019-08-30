package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static ee.tuleva.onboarding.comparisons.returns.provider.PersonalReturnProvider.THIRD_PILLAR;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class ReturnsService {

    private final List<ReturnProvider> returnProviders;

    public Returns get(Person person, LocalDate fromDate, List<String> keys) {
        int pillar = getPillar(keys);
        Instant fromTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Return> returns = returnProviders.stream()
            .filter(returnProvider -> keys == null || !Collections.disjoint(keys, returnProvider.getKeys()))
            .map(returnProvider -> returnProvider.getReturns(person, fromTime, pillar).getReturns())
            .flatMap(List::stream)
            .filter(aReturn -> keys == null || keys.contains(aReturn.getKey()))
            .collect(toList());

        return Returns.builder()
            .from(fromDate)
            .returns(returns)
            .build();
    }

    private Integer getPillar(List<String> keys) {
        if (keys != null && keys.contains(THIRD_PILLAR)) {
            return 3;
        } else {
            return 2;
        }
    }
}
