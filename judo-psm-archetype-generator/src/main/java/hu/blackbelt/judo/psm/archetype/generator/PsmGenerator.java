package hu.blackbelt.judo.psm.archetype.generator;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.ValueResolver;
import com.github.jknack.handlebars.io.URLTemplateLoader;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This class loads descriptor yaml file and processing it.
 * The yaml file contains a entries describes the generation itself. On entry can
 * be used to generate several entries of files. @see {@link GeneratorTemplate}
 */
@Slf4j
public class PsmGenerator {

    public static final String NAME = "name";
    public static final Boolean CLIENT_TEMPLATE_DEBUG = System.getProperty("clientTemplateDebug") != null;
    public static final String YAML = ".yaml";
    public static final String ADD_DEBUG_TO_TEMPLATE = "addDebugToTemplate";
    public static final String TEMPLATE = "template";
    public static final String SELF = "self";
    public static final String ACTOR_TYPES = "actorTypes";
    public static final String ACTOR_TYPE = "actorType";
    public static final String MODEL = "model";

    @Builder(builderMethodName = "psmGeneratorParameter")
    public static final class PsmGeneratorParameter {
        PsmGeneratorContext projectGenerator;

        @Builder.Default
        Predicate<ActorType> actorTypePredicate = a -> true;

        @NonNull
        Function<ActorType, File> actorTypeTargetDirectoryResolver;

        @NonNull
        Supplier<File> targetDirectoryResolver;

        Log log;

        @Builder.Default
        Supplier<Map<String, ?>> extraContextVariables = () -> ImmutableMap.of();
    }

    @Builder(builderMethodName = "psmGeneratorResult")
    @Getter
    public static final class PsmGeneratorResult {

        @Builder.Default
        Map<ActorType, Collection<GeneratedFile>> generatedByActors = new ConcurrentHashMap<>();

        @Builder.Default
        Collection<GeneratedFile> generated = new CopyOnWriteArrayList<>();
    }


    public static PsmGeneratorResult executePsmGeneration(PsmGeneratorParameter.PsmGeneratorParameterBuilder builder) throws Exception {
        return executePsmGeneration(builder.build());
    }

    public static PsmGeneratorResult executePsmGeneration(PsmGeneratorParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(PsmGenerator.log);
                                                });
        try {
            return getPsmGeneratorResult(parameter, log);
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    private static PsmGeneratorResult getPsmGeneratorResult(PsmGeneratorParameter parameter, Log log) throws InterruptedException, ExecutionException {
        PsmGeneratorResult result = PsmGeneratorResult.psmGeneratorResult().build();

        parameter.projectGenerator.getModelResourceSupport().getStreamOfPsmAccesspointActorType().forEach(
                app -> { result.generatedByActors.put(app, ConcurrentHashMap.newKeySet()); });

        Set<ActorType> actorTypes = parameter.projectGenerator.getModelResourceSupport().getStreamOfPsmAccesspointActorType()
                .filter(parameter.actorTypePredicate).collect(Collectors.toSet());

        Model model = parameter.projectGenerator.getModelResourceSupport().getStreamOfPsmNamespaceModel().findFirst().get();

        List<CompletableFuture<GeneratedFile>> tasks = new ArrayList<>();

        parameter.projectGenerator.getGeneratorTemplates().stream().forEach(generatorTemplate -> {

            Function<Object, Context.Builder> defaultContextBuilder = o -> {
                ImmutableMap.Builder params = ImmutableMap.<String, Object>builder()
                    .put(ADD_DEBUG_TO_TEMPLATE, CLIENT_TEMPLATE_DEBUG)
                    .put(ACTOR_TYPES, actorTypes)
                    .put(TEMPLATE, generatorTemplate)
                    .put(SELF, o)
                    .put(MODEL, model);

                Map<String, ?> extraVariables = parameter.extraContextVariables.get();
                extraVariables.forEach((k, v) -> {
                    if (k != null && v != null) {
                        params.put(k, v);
                    }
                });

                Context.Builder contextBuilder = Context.newBuilder(params.build());
                if (parameter.projectGenerator.getValueResolvers().size() > 0) {
                    contextBuilder.push(parameter.projectGenerator.getValueResolvers().toArray(ValueResolver[]::new));
                }
                return contextBuilder;
            };

            Function<Object, StandardEvaluationContext> defaultStandardEvaluationContext = o -> {
                StandardEvaluationContext templateContext = parameter.projectGenerator.createSpringEvaulationContext();
                templateContext.setVariable(ADD_DEBUG_TO_TEMPLATE, CLIENT_TEMPLATE_DEBUG);
                templateContext.setVariable(ACTOR_TYPES, actorTypes);
                templateContext.setVariable(TEMPLATE, generatorTemplate);
                templateContext.setVariable(SELF, o);
                templateContext.setVariable(MODEL, model);

                Map<String, ?> extraVariables = parameter.extraContextVariables.get();
                extraVariables.forEach((k, v) -> {
                    templateContext.setVariable(k, v);
                });
                return templateContext;
            };

            StandardEvaluationContext evaulationContext = defaultStandardEvaluationContext.apply(model);
            final TemplateEvaluator templateEvaulator;
            try {
                templateEvaulator = generatorTemplate.getTemplateEvalulator(
                        parameter.projectGenerator, evaulationContext);
            } catch (IOException e) {
                throw new RuntimeException("Could not evaluate template", e);
            }

            if (generatorTemplate.isActorTypeBased()) {
                actorTypes.forEach(actorType -> {
                    evaulationContext.setVariable(ACTOR_TYPE, actorType);

                    Collection processingList = new HashSet(Arrays.asList(actorType));
                    if (templateEvaulator.getFactoryExpression() != null) {
                        processingList = templateEvaulator.getFactoryExpressionResultOrValue(actorType, Collection.class);
                    }
                    templateEvaulator.getFactoryExpressionResultOrValue(processingList, Collection.class).stream().forEach(element -> {
                        tasks.add(CompletableFuture.supplyAsync(() -> {
                            StandardEvaluationContext templateContext = defaultStandardEvaluationContext.apply(element);
                            templateContext.setVariable(ACTOR_TYPE, actorType);

                            Context.Builder contextBuilder = defaultContextBuilder.apply(element)
                                    .combine(ACTOR_TYPE, actorType);

                            generatorTemplate.evalToContextBuilder(templateEvaulator, contextBuilder, templateContext);
                            GeneratedFile generatedFile = generateFile(parameter.projectGenerator, templateContext, templateEvaulator, generatorTemplate, contextBuilder, log);
                            result.generatedByActors.get(actorType).add(generatedFile);
                            return generatedFile;
                        }));
                    });
                });
            } else {
                evaulationContext.setVariable(TEMPLATE, generatorTemplate);
                Set iterableCollection = new HashSet(Arrays.asList(generatorTemplate));

                if (templateEvaulator.getTemplate() != null) {
                    iterableCollection = actorTypes;
                }
                templateEvaulator.getFactoryExpressionResultOrValue(iterableCollection, Collection.class).stream().forEach(element -> {
                    tasks.add(CompletableFuture.supplyAsync(() -> {

                        StandardEvaluationContext templateContext = defaultStandardEvaluationContext.apply(element);
                        Context.Builder contextBuilder = defaultContextBuilder.apply(element);

                        generatorTemplate.evalToContextBuilder(templateEvaulator, contextBuilder, evaulationContext);
                        GeneratedFile generatedFile = generateFile(parameter.projectGenerator, templateContext, templateEvaulator, generatorTemplate, contextBuilder, log);
                        result.generated.add(generatedFile);
                        return generatedFile;
                    }));
                });
            }
        });

        allFuture(tasks).get();
        return result;
    }


    private static GeneratedFile generateFile(final PsmGeneratorContext projectGenerator,
                                              final StandardEvaluationContext evaluationContext,
                                              final TemplateEvaluator templateEvaulator,
                                              final GeneratorTemplate generatorTemplate,
                                              final Context.Builder contextBuilder,
                                              final Log log) {

        GeneratedFile generatedFile = new GeneratedFile();
        generatedFile.setPath(templateEvaulator.getPathExpression().getValue(evaluationContext, String.class));

        if (generatorTemplate.isCopy()) {
            String location = generatorTemplate.getTemplateName();
            if (location.startsWith("/")) {
                location =  location.substring(1);
            }
            location = projectGenerator.getTemplateLoader().resolve(location);
            try {
                URL resource = projectGenerator.getUrlResolver().getResource(location);
                if (resource != null) {
                    generatedFile.setContent(ByteStreams.toByteArray(resource.openStream()));
                }  else {
                    log.error("Could not locate: " + location);
                }
            } catch (Exception e) {
                log.error("Could not resolve: " + location);
            }
        } else {
            StringWriter sourceFile = new StringWriter();
            try {
                templateEvaulator.getTemplate().apply(contextBuilder.build(), sourceFile);
            } catch (IOException e) {
                log.error("Could not generate template: " + generatedFile.getPath());
            }
            generatedFile.setContent(sourceFile.toString().getBytes(Charsets.UTF_8));
        }
        return generatedFile;
    }

    public static Consumer<Map.Entry<ActorType, Collection<GeneratedFile>>> getDirectoryWriterForActor(Function<ActorType, File> actorTypeTargetDirectoryResolver, Log log) {
        return e -> {
            File targetDirectory = actorTypeTargetDirectoryResolver.apply(e.getKey());
            GeneratorIgnore generatorIgnore = new GeneratorIgnore(targetDirectory.toPath());
            e.getValue().stream().forEach(f -> {
                File outFile = new File(targetDirectory, f.getPath());
                outFile.getParentFile().mkdirs();
                if (!generatorIgnore.shouldExcludeFile(outFile.toPath())) {
                    try {
                        ByteStreams.copy(new ByteArrayInputStream(f.getContent()), new FileOutputStream(outFile));
                    } catch (Exception exception) {
                        log.error("Could not write file: " + outFile.getAbsolutePath(), exception);
                    }
                }
            });
        };
    }

    public static Consumer<Collection<GeneratedFile>> getDirectoryWriter(Supplier<File> targetDirectoryResolver, Log log) {
        return e -> {
            File targetDirectory = targetDirectoryResolver.get();
            GeneratorIgnore generatorIgnore = new GeneratorIgnore(targetDirectory.toPath());
            e.stream().forEach(f -> {
                File outFile = new File(targetDirectory, f.getPath());
                outFile.getParentFile().mkdirs();
                if (!generatorIgnore.shouldExcludeFile(outFile.toPath())) {
                    try {
                        ByteStreams.copy(new ByteArrayInputStream(f.getContent()), new FileOutputStream(outFile));
                    } catch (Exception exception) {
                        log.error("Could not write file: " + outFile.getAbsolutePath(), exception);
                    }
                }
            });

        };
    }


    public static void executePsmGenerationToDirectory(PsmGeneratorParameter.PsmGeneratorParameterBuilder builder) throws Exception {
        executePsmGenerationToDirectory(builder.build());
    }

    public static void executePsmGenerationToDirectory(PsmGeneratorParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(PsmGenerator.log);
                                                });

        try {
            PsmGeneratorResult result = executePsmGeneration(parameter);
            result.generatedByActors
                    .entrySet()
                    .stream()
                    .filter(e -> parameter.actorTypePredicate.test(e.getKey()))
                    .forEach(getDirectoryWriterForActor(parameter.actorTypeTargetDirectoryResolver, log));
            getDirectoryWriter(parameter.targetDirectoryResolver, log).accept(result.generated);
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    @SneakyThrows(IOException.class)
    public static InputStream getGeneratedFilesAsZip(Collection<GeneratedFile> generatedFiles) {
        ByteArrayOutputStream generatedZip = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(generatedZip);
        for (GeneratedFile generatedFile : generatedFiles) {
            zipOutputStream.putNextEntry(new ZipEntry(generatedFile.getPath()));
            zipOutputStream.write(generatedFile.getContent(), 0, generatedFile.getContent().length);
            zipOutputStream.flush();
            zipOutputStream.closeEntry();
        }
        zipOutputStream.flush();
        zipOutputStream.close();
        return new ByteArrayInputStream(generatedZip.toByteArray());
    }


    public static <T> CompletableFuture<List<T>> allFuture(List<CompletableFuture<T>> futures) {
        CompletableFuture[] cfs = futures.toArray(new CompletableFuture[futures.size()]);

        return CompletableFuture.allOf(cfs)
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }

    public static PsmGeneratorContext createGeneratorContext(PsmModel psmModel,
                                                             String descriptorName,
                                                             List<URI> uris,
                                                             Collection<Class> helpers,
                                                             Collection<ValueResolver> valueResolvers,
                                                             Function<List<URI>, URLTemplateLoader> urlTemplateLoaderFactory,
                                                             Function<List<URI>, URLResolver> urlResolverFactory) throws IOException {

        URLTemplateLoader urlTemplateLoader = null;
        URLResolver urlResolver = null;

        if (urlTemplateLoaderFactory != null) {
            urlTemplateLoader = urlTemplateLoaderFactory.apply(uris);
            if (urlResolverFactory != null) {
                urlResolver = urlResolverFactory.apply(uris);
            } else {
                throw new IllegalStateException("Could not determinate URLResolver");
            }
        } else {
            urlTemplateLoader = ChainedURLTemplateLoader.createFromURIs(uris);
            if (urlResolverFactory != null) {
                urlResolver = urlResolverFactory.apply(uris);
            } else {
                urlResolver = (URLResolver) urlTemplateLoader;
            }
        }

        if (uris == null || uris.isEmpty()) {
            throw new IllegalArgumentException("Minimum one URI is mandatory for templates");
        }

        URI rootUri = uris.get(0);
        List<URI> scriptUris = new ArrayList<>();
        for (URI uri : uris) {
            if (uri != rootUri) {
                scriptUris.add(uri);
            }
        }

        List<GeneratorTemplate> generatorTemplates = new ArrayList<>();
        generatorTemplates.addAll(GeneratorTemplate.loadYamlURL(UriHelper.calculateRelativeURI(rootUri, descriptorName + YAML).normalize().toURL()));
        if (generatorTemplates.isEmpty()) {
            throw new IllegalArgumentException("No template loaded.");
        }

        for (URI uri : scriptUris) {
            Collection<GeneratorTemplate> overridedTemplates =
                    GeneratorTemplate.loadYamlURL(UriHelper.calculateRelativeURI(uri, descriptorName + YAML).normalize().toURL());
            Collection<GeneratorTemplate> replaceableTemplates = new HashSet<>();
            generatorTemplates.forEach(t -> {
                overridedTemplates.stream().filter(o -> o.getTemplateName().equals(t.getTemplateName())).forEach(f -> replaceableTemplates.add(f));
            });
            generatorTemplates.removeAll(replaceableTemplates);
            generatorTemplates.addAll(overridedTemplates);
        }

        List<ValueResolver> valueResolversPar = new ArrayList<>();
        valueResolversPar.add(new PsmValueResolver());
        if (valueResolvers != null) {
            valueResolversPar.addAll(valueResolvers);
        }

        PsmGeneratorContext psmProjectGenerator = new PsmGeneratorContext(psmModel, urlTemplateLoader, urlResolver, generatorTemplates, helpers, valueResolversPar);
        return psmProjectGenerator;
    }

}
