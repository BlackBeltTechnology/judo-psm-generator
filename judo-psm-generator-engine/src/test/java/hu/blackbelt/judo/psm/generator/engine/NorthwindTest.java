package hu.blackbelt.judo.psm.generator.engine;

import com.google.common.collect.ImmutableMap;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.generator.commons.TemplateHelperFinder;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.model.northwind.Demo;
import lombok.extern.slf4j.Slf4j;
//import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.SaveArguments.psmSaveArgumentsBuilder;
import static hu.blackbelt.judo.meta.psm.runtime.PsmModel.buildPsmModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.linesOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class NorthwindTest {

    public static final String NORTHWIND_TEST = "northwind-test";
    public static final String OVERRIDE_1 = "override1";
    public static final String OVERRIDE_2 = "override2";
    private final String TEST_SOURCE_MODEL_NAME = "urn:test.judo-meta-psm";
    private final String TEST = "northwind";
    private final String TARGET_TEST_CLASSES = "target/test-classes";

    PsmModel psmModel;

    String testName;
    Map<EObject, List<EObject>> resolvedTrace;

    @BeforeEach
    void setUp() {
        psmModel = buildPsmModel().uri(org.eclipse.emf.common.util.URI.createURI(TEST_SOURCE_MODEL_NAME)).name(TEST).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        psmModel.savePsmModel(psmSaveArgumentsBuilder().file(new File(TARGET_TEST_CLASSES, testName + "-psm.model")));
    }

    @Test
    void testCreateApplication() throws Exception {
        testName = "Northwind";

        psmModel = new Demo().fullDemo();
        File testOutput =  new File(TARGET_TEST_CLASSES, NORTHWIND_TEST);

        LinkedHashMap uris = new LinkedHashMap();
        uris.put(new File(TARGET_TEST_CLASSES, OVERRIDE_1).toString(), new File(TARGET_TEST_CLASSES, OVERRIDE_1).toURI());
        uris.put(new File(TARGET_TEST_CLASSES, OVERRIDE_2).toString(), new File(TARGET_TEST_CLASSES, OVERRIDE_2).toURI());

        try (Log bufferedLog = new BufferedSlf4jLogger(log)) {
            PsmGenerator.generateToDirectory(PsmGeneratorParameter.psmGeneratorParameter()
                            .generatorContext(PsmGenerator.createGeneratorContext(
                                    PsmGenerator.CreateGeneratorContextArgument.builder()
                                            .psmModel(psmModel)
                                            .descriptorName("test-project")
                                            .uris(uris)
                                            .helpers(TemplateHelperFinder.collectHelpersAsClass(this.getClass().getClassLoader()))
                                            .build()))
                            .log(bufferedLog)
                            .targetDirectoryResolver(() -> testOutput)
                            .actorTypeTargetDirectoryResolver( a -> testOutput)
                            .extraContextVariables(() -> ImmutableMap.of("extra", "extra"))
                    );
        }


        final Optional<ActorType> application = allPsm(ActorType.class)
                .findAny();
        assertTrue(application.isPresent());

        assertTrue(new File(testOutput, "ExternalUser").isDirectory());
        assertTrue(new File(testOutput, "InternalUser").isDirectory());

        assertThat(linesOf(new File(testOutput, "InternalUser/actorname"))).containsExactly(
                "DECORATED Name: InternalUser",
                "Extra: extra",
                "FQName: demo::InternalUser",
                "PlainName: internaluser",
                "Plain FQ: demo__internaluser",
                "Path FQ: demo__internal_user",
                "ModelName FQ: Demo",
                "Package Name FQ: ",
                ""
        );

        assertThat(linesOf(new File(testOutput, "InternalUser/actornameOverride1"))).containsExactly(
                "Name: InternalUser",
                "FQName: demo::InternalUser",
                "PlainName: internaluser",
                "Plain FQ: demo__internaluser",
                "Path FQ: demo__internal_user",
                "ModelName FQ: Demo",
                "Package Name FQ: ",
                ""
        );

    }

    static <T> Stream<T> asStream(Iterator<T> sourceIterator, boolean parallel) {
        Iterable<T> iterable = () -> sourceIterator;
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }

    <T> Stream<T> allPsm() {
        return asStream((Iterator<T>) psmModel.getResourceSet().getAllContents(), false);
    }

    private <T> Stream<T> allPsm(final Class<T> clazz) {
        return allPsm().filter(e -> clazz.isAssignableFrom(e.getClass())).map(e -> (T) e);
    }

}
