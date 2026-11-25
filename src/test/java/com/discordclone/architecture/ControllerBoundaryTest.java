package com.discordclone.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.discordclone", importOptions = ImportOption.DoNotIncludeTests.class)
class ControllerBoundaryTest {

    /** Returning an entity leaks fields such as password hashes; binding one lets clients set them. */
    @ArchTest
    static final ArchRule controllersDoNotExposeEntities = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should(notUseEntitiesInSignature());

    private static ArchCondition<JavaMethod> notUseEntitiesInSignature() {
        return new ArchCondition<>("not accept or return a JPA entity, even inside a generic type") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Method reflected = method.reflect();
                Stream.concat(Stream.of(reflected.getGenericReturnType()),
                                Stream.of(reflected.getGenericParameterTypes()))
                        .filter(ControllerBoundaryTest::mentionsEntity)
                        .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " uses entity type " + type.getTypeName())));
            }
        };
    }

    private static boolean mentionsEntity(Type type) {
        if (type instanceof Class<?> raw) {
            return raw.isAnnotationPresent(Entity.class)
                    || (raw.isArray() && mentionsEntity(raw.getComponentType()));
        }
        if (type instanceof ParameterizedType generic) {
            return mentionsEntity(generic.getRawType())
                    || Stream.of(generic.getActualTypeArguments()).anyMatch(ControllerBoundaryTest::mentionsEntity);
        }
        if (type instanceof WildcardType wildcard) {
            return Stream.of(wildcard.getUpperBounds()).anyMatch(ControllerBoundaryTest::mentionsEntity);
        }
        return false;
    }
}
