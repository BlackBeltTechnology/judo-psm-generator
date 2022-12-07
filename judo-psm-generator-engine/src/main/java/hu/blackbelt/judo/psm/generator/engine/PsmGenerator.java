package hu.blackbelt.judo.psm.generator.engine;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.ValueResolver;
import com.github.jknack.handlebars.io.URLTemplateLoader;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import hu.blackbelt.epsilon.runtime.execution.api.Log;
import hu.blackbelt.epsilon.runtime.execution.impl.BufferedSlf4jLogger;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.support.PsmModelResourceSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
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


    public static PsmGeneratorResult execute(PsmGeneratorParameter.PsmGeneratorParameterBuilder builder) throws Exception {
        return execute(builder.build());
    }

    public static PsmGeneratorResult execute(PsmGeneratorParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(PsmGenerator.log);
                                                });
        try {
            return execute(parameter, log);
        } finally {
            if (loggerToBeClosed.get()) {
                log.close();
            }
        }
    }

    private static PsmGeneratorResult execute(PsmGeneratorParameter parameter, Log log) throws InterruptedException, ExecutionException {
        PsmGeneratorResult result = PsmGeneratorResult.psmGeneratorResult().build();

        parameter.generatorContext.getModelResourceSupport().getStreamOfPsmAccesspointActorType().forEach(
                app -> { result.generatedByActors.put(app, ConcurrentHashMap.newKeySet()); });

        Set<ActorType> actorTypes = parameter.generatorContext.getModelResourceSupport().getStreamOfPsmAccesspointActorType()
                .filter(parameter.actorTypePredicate).collect(Collectors.toSet());

        Model model = parameter.generatorContext.getModelResourceSupport().getStreamOfPsmNamespaceModel().findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find the model entry"));

        List<CompletableFuture<GeneratedFile>> tasks = new ArrayList<>();

        parameter.generatorContext.getGeneratorModel().getTemplates().stream().forEach(generatorTemplate -> {

            // Handlebars context builder
            // It creates parameters accessible within templates.
            // It returns the function, the context creation itself is called when template is processed.
            Function<Object, Context.Builder> defaultHandlebarsContextBuilder = o -> {
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
                if (parameter.generatorContext.getValueResolvers().size() > 0) {
                    contextBuilder.push(parameter.generatorContext.getValueResolvers().toArray(ValueResolver[]::new));
                }
                return contextBuilder;
            };

            // SpringEL Context builder
            Function<Object, StandardEvaluationContext> defaultSpringELContextProvider = o -> {
                StandardEvaluationContext templateContext = parameter.generatorContext.createSpringEvaluationContext();
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

            StandardEvaluationContext evaulationContext = defaultSpringELContextProvider.apply(model);
            final TemplateEvaluator templateEvaulator;
            try {
                templateEvaulator = generatorTemplate.getTemplateEvalulator(
                        parameter.generatorContext, evaulationContext);
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
                            StandardEvaluationContext templateContext = defaultSpringELContextProvider.apply(element);
                            templateContext.setVariable(ACTOR_TYPE, actorType);

                            Context.Builder contextBuilder = defaultHandlebarsContextBuilder.apply(element)
                                    .combine(ACTOR_TYPE, actorType);

                            generatorTemplate.evalToContextBuilder(templateEvaulator, contextBuilder, templateContext);
                            GeneratedFile generatedFile = generateFile(parameter.generatorContext, templateContext, templateEvaulator, generatorTemplate, contextBuilder, log);
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

                        StandardEvaluationContext templateContext = defaultSpringELContextProvider.apply(element);
                        Context.Builder contextBuilder = defaultHandlebarsContextBuilder.apply(element);

                        generatorTemplate.evalToContextBuilder(templateEvaulator, contextBuilder, evaulationContext);
                        GeneratedFile generatedFile = generateFile(parameter.generatorContext, templateContext, templateEvaulator, generatorTemplate, contextBuilder, log);
                        result.generated.add(generatedFile);
                        return generatedFile;
                    }));
                });
            }
        });

        StreamHelper.performFutures(tasks);
        return result;
    }


    private static GeneratedFile generateFile(final PsmGeneratorContext generatorContext,
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
            location = generatorContext.getTemplateLoader().resolve(location);
            try {
                URL resource = generatorContext.getUrlResolver().getResource(location);
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
                Context context = contextBuilder.build();
                if (generatorContext.getHandlebarsContextAccessor() != null) {
                    Arrays.stream(generatorContext.getHandlebarsContextAccessor().getMethods()).filter(m ->
                                    m.getName().equals("bindContext") &&
                                            Modifier.isPublic(m.getModifiers()) &&
                                            Modifier.isStatic(m.getModifiers()) &&
                                            m.getParameters().length == 1 &&
                                            Context.class.isAssignableFrom(m.getParameters()[0].getType())
                            ).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("The 'handleabarsContextAccessor' does not " +
                                    "have 'public static void bindContext(com.github.jknack.handlebars,Context)' method"))
                            .invoke(null, context);

                }
                templateEvaulator.getTemplate().apply(context, sourceFile);
            } catch (IllegalAccessException | InvocationTargetException | IOException e) {
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


    public static void generateToDirectory(PsmGeneratorParameter.PsmGeneratorParameterBuilder builder) throws Exception {
        generateToDirectory(builder.build());
    }

    public static void generateToDirectory(PsmGeneratorParameter parameter) throws Exception {
        final AtomicBoolean loggerToBeClosed = new AtomicBoolean(false);
        Log log = Objects.requireNonNullElseGet(parameter.log,
                                                () -> {
                                                    loggerToBeClosed.set(true);
                                                    return new BufferedSlf4jLogger(PsmGenerator.log);
                                                });

        try {
            PsmGeneratorResult result = execute(parameter);
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

    public static PsmGeneratorContext createGeneratorContext(
            PsmModel psmModel,
            String descriptorName,
            List<URI> uris,
            Collection<Class> helpers,
            Collection<ValueResolver> valueResolvers,
            Class handlebarsContextAccessor,
            Function<List<URI>, URLTemplateLoader> urlTemplateLoaderFactory,
            Function<List<URI>, URLResolver> urlResolverFactory) throws IOException {

        GeneratorModel effectiveModel = GeneratorModel.generatorModelBuilder().build();

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

        GeneratorModel generatorModel = GeneratorModel.loadYamlURL(UriHelper.calculateRelativeURI(rootUri, descriptorName + YAML).normalize().toURL());
        for (URI uri : scriptUris) {
            GeneratorModel overridedGeneratorModel = GeneratorModel.loadYamlURL(UriHelper.calculateRelativeURI(uri, descriptorName + YAML).normalize().toURL());
            if (overridedGeneratorModel != null) {
                generatorModel.overrideTemplates(overridedGeneratorModel.getTemplates());
            }
        }

        List<ValueResolver> valueResolversPar = new ArrayList<>();
        valueResolversPar.add(new PsmValueResolver());
        if (valueResolvers != null) {
            valueResolversPar.addAll(valueResolvers);
        }

        PsmGeneratorContext psmProjectGenerator = new PsmGeneratorContext(psmModel, urlTemplateLoader, urlResolver, generatorModel, helpers, valueResolversPar, handlebarsContextAccessor);
        return psmProjectGenerator;
    }

}
