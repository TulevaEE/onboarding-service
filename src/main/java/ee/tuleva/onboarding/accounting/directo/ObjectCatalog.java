package ee.tuleva.onboarding.accounting.directo;

import static ee.tuleva.onboarding.accounting.directo.DirectoFields.require;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

import java.util.List;
import java.util.Map;

final class ObjectCatalog {

  private static final String EMPLOYEE_OBJECT_LEVEL = "50";

  private final Map<String, String> levelsByCode;

  ObjectCatalog(List<DirectoObject> objects) {
    levelsByCode =
        objects.stream()
            .filter(object -> object.level() != null && !object.level().isBlank())
            .collect(
                toMap(
                    object -> require(object.code(), "objects.code").trim(),
                    object -> require(object.level(), "objects.level").trim(),
                    ObjectCatalog::theSameLevel));
  }

  boolean isClassified(String code) {
    return levelsByCode.containsKey(code);
  }

  boolean isEmployee(String code) {
    return EMPLOYEE_OBJECT_LEVEL.equals(levelsByCode.get(code));
  }

  Map<String, String> dimensionsOf(DocumentRow row) {
    return row.objectCodes().stream()
        .collect(
            toUnmodifiableMap(
                code -> dimensionOf(code).key(),
                code -> code,
                (first, second) -> {
                  throw new IllegalArgumentException(
                      "Directo row has two objects of one level: field=rows.object");
                }));
  }

  private ObjectDimension dimensionOf(String code) {
    var level = levelsByCode.get(code);
    if (level == null) {
      throw new IllegalArgumentException("Directo object unclassified: field=rows.object");
    }
    return ObjectDimension.atLevel(level);
  }

  private static String theSameLevel(String level, String otherLevel) {
    if (!level.equals(otherLevel)) {
      throw new IllegalArgumentException(
          "Directo object listed at two levels: field=objects.level");
    }
    return level;
  }
}
