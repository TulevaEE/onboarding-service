package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final EpisService episService;
    private final ApplicationConverter applicationConverter;

    public List<Application> get(Person person, String language) {
        return
            episService.getApplications(person).stream()
                .map(applicationDTO -> applicationConverter.convert(applicationDTO, language))
                .collect(toList());
    }
}
