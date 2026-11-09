package com.oneutility.cba.bankingapi;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/** The module rules from the Engineering Standards (Code structure, Layout). */
@AnalyzeClasses(packages = "com.oneutility.cba.bankingapi", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.oneutility.cba.bankingapi.";

    @ArchTest
    static final ArchRule modules_use_each_other_only_through_application =
            classes().that().resideInAPackage(ROOT + "(*)..").and().resideOutsideOfPackage(ROOT + "shared..")
                    .should(onlyReachOtherModulesThroughApplication());

    @ArchTest
    static final ArchRule shared_depends_on_no_module = noClasses().that().resideInAPackage(ROOT + "shared..")
            .should().dependOnClassesThat().resideOutsideOfPackages(ROOT + "shared..", "java..", "jakarta..",
                    "org.springframework..", "org.slf4j..", "com.fasterxml..");

    @ArchTest
    static final ArchRule domain_depends_on_no_other_layer = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_api = noClasses().that()
            .resideInAPackage("..application..").should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule modules_have_no_cycles = slices().matching(ROOT + "(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule transactions_only_in_application = noClasses().that()
            .resideOutsideOfPackage("..application..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    private static ArchCondition<JavaClass> onlyReachOtherModulesThroughApplication() {
        return new ArchCondition<>("use other modules only through their application package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String module = module(origin.getPackageName());
                origin.getDirectDependenciesFromSelf().forEach(dependency -> {
                    JavaClass target = dependency.getTargetClass();
                    String targetPackage = target.getPackageName();
                    if (!targetPackage.startsWith(ROOT)) {
                        return;
                    }
                    String targetModule = module(targetPackage);
                    if (targetModule.equals(module) || targetModule.equals("shared")
                            || targetPackage.startsWith(ROOT + targetModule + ".application")) {
                        return;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                });
            }
        };
    }

    private static String module(String packageName) {
        String rest = packageName.length() > ROOT.length() ? packageName.substring(ROOT.length()) : "";
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
