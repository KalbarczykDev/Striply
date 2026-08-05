package dev.kalbarczyk.striply.architecture;


import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "dev.kalbarczyk.striply",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ModuleArchitectureTest {
    // Module packages do not contain production classes yet.
    // Remove .allowEmptyShould(true); when the first module implementation is added.
    @ArchTest
    static final ArchRule modules_should_be_free_of_cycles = slices()
            .matching("dev.kalbarczyk.striply.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true);

}