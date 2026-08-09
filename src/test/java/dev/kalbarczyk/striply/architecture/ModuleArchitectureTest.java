package dev.kalbarczyk.striply.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@SuppressWarnings("unused")
@AnalyzeClasses(
        packages = "dev.kalbarczyk.striply",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule modules_should_be_free_of_cycles = slices()
            .matching("dev.kalbarczyk.striply.(*)..")
            .should()
            .beFreeOfCycles();


    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence.."
            );

    @ArchTest
    static final ArchRule catalog_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("catalog");

    @ArchTest
    static final ArchRule identity_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("identity");

    @ArchTest
    static final ArchRule organization_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("organization");

    @ArchTest
    static final ArchRule customer_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("customer");

    @ArchTest
    static final ArchRule checkout_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("checkout");

    @ArchTest
    static final ArchRule payment_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("payment");

    @ArchTest
    static final ArchRule refund_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("refund");

    @ArchTest
    static final ArchRule webhook_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("webhook");

    @ArchTest
    static final ArchRule developer_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("developer");

    @ArchTest
    static final ArchRule audit_internals_should_not_be_accessed_by_other_modules =
            moduleInternalsShouldNotBeAccessedByOtherModules("audit");


    private static ArchRule moduleInternalsShouldNotBeAccessedByOtherModules(
            final String module
    ) {
        final String modulePackage = "dev.kalbarczyk.striply." + module;
        return noClasses()
                .that()
                .resideOutsideOfPackage(modulePackage + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        modulePackage + ".application..",
                        modulePackage + ".domain..",
                        modulePackage + ".infrastructure..",
                        modulePackage + ".api.web.."
                )
                .allowEmptyShould(true)
                .as("classes outside " + module
                        + " should not access its internal packages");
    }
}
