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

/**
 * The structure rules (see the README, Layout): a module is {@code controller -> service -> domain}, with {@code dto}
 * for the objects passed between them, and modules reach each other only through their services.
 */
@AnalyzeClasses(packages = "com.oneutility.cba.bankingapi", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.oneutility.cba.bankingapi.";

    @ArchTest
    static final ArchRule modules_use_each_other_only_through_services_and_dtos =
            classes().that().resideInAPackage(ROOT + "(*)..").and().resideOutsideOfPackage(ROOT + "common..")
                    .should(onlyReachOtherModulesThroughServicesAndDtos());

    @ArchTest
    static final ArchRule common_depends_on_no_module = noClasses().that().resideInAPackage(ROOT + "common..")
            .should().dependOnClassesThat().resideOutsideOfPackages(ROOT + "common..", "java..", "jakarta..",
                    "org.springframework..", "org.slf4j..", "com.fasterxml..");

    @ArchTest
    static final ArchRule controllers_do_not_use_the_domain = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + "*.domain..");

    @ArchTest
    static final ArchRule services_do_not_use_controllers = noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule domain_depends_on_no_other_layer = noClasses().that().resideInAPackage(ROOT + "*.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..dto..");

    @ArchTest
    static final ArchRule dtos_depend_on_no_other_layer = noClasses().that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", ROOT + "*.domain..");

    @ArchTest
    static final ArchRule modules_have_no_cycles = slices().matching(ROOT + "(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule transactions_only_in_services = noClasses().that().resideOutsideOfPackage("..service..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    private static ArchCondition<JavaClass> onlyReachOtherModulesThroughServicesAndDtos() {
        return new ArchCondition<>("use other modules only through their service and dto packages") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String module = module(origin.getPackageName());
                origin.getDirectDependenciesFromSelf().forEach(dependency -> {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    if (!targetPackage.startsWith(ROOT)) {
                        return;
                    }
                    String targetModule = module(targetPackage);
                    if (targetModule.equals(module) || targetModule.equals("common")
                            || targetPackage.startsWith(ROOT + targetModule + ".service")
                            || targetPackage.startsWith(ROOT + targetModule + ".dto")) {
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
