package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.returns.Returns.Return;
import ee.tuleva.onboarding.comparisons.returns.provider.ReturnProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnsService {

    private final List<ReturnProvider> returnProviders;

    public Returns getReturns(Person person, LocalDate fromDate) {
        int pillar = 2; // TODO
        Instant fromTime = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Return> returns = new ArrayList<>();
        for (ReturnProvider returnProvider : returnProviders) {
            Returns allReturns = returnProvider.getReturns(person, fromTime, pillar);
            returns.addAll(allReturns.getReturns());
        }

        return Returns.builder()
            .from(fromDate)
            .returns(returns)
            .build();
    }
}
