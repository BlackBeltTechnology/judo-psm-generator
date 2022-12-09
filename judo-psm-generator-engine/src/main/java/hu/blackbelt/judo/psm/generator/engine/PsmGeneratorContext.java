package hu.blackbelt.judo.psm.generator.engine;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.ValueResolver;
import com.github.jknack.handlebars.cache.HighConcurrencyTemplateCache;
import com.github.jknack.handlebars.cache.TemplateCache;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.URLTemplateLoader;
import com.google.common.base.Charsets;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.meta.psm.support.PsmModelResourceSupport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;

/**
 * This class holds the state over a generation process. The individual generation for templates uses
 * context to create Handlebars SpringEL instances.
 */
@Slf4j
public class PsmGeneratorContext {
    @Getter
    private final URLTemplateLoader templateLoader;

    @Getter
    private final URLResolver urlResolver;

    @Getter
    private final PsmModelResourceSupport modelResourceSupport;

    @Getter
    private final GeneratorModel generatorModel;

    @Getter
    private final Collection<Class> helpers;

    @Getter
    private final Collection<ValueResolver> valueResolvers;

    @Getter
    private final Class contextAccessor;


    TemplateCache templateCache = new HighConcurrencyTemplateCache();

    public PsmGeneratorContext(PsmModel psmModel,
                               URLTemplateLoader templateLoader,
                               URLResolver urlResolver,
                               GeneratorModel generatorModel,
                               Collection<Class> helpers,
                               Collection<ValueResolver> valueResolvers,
                               Class contextAccessor) {

        this.templateLoader = templateLoader;
        modelResourceSupport = PsmModelResourceSupport.psmModelResourceSupportBuilder()
                .resourceSet(psmModel.getResourceSet())
                .build();
        this.generatorModel = generatorModel;
        this.urlResolver = urlResolver;
        this.helpers = helpers;
        this.valueResolvers = valueResolvers;
        this.contextAccessor = contextAccessor;
    }

    public Handlebars createHandlebars() {
        Handlebars handlebars = new Handlebars();
        handlebars.with(templateLoader).with(templateCache);
        handlebars.setStringParams(true);
        handlebars.setCharset(Charsets.UTF_8);
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.prettyPrint(true);
        handlebars.setInfiniteLoops(true);
        handlebars.registerHelpers(StringHelpers.class);
//        handlebars.registerHelpers(ActorTypeHelpers.class);
        for (Class clazz : helpers) {
            handlebars.registerHelpers(clazz);
        }

        handlebars.registerHelper("times", (Helper<Integer>) (n, options) -> {
            String accum = "";
            for(Integer i = 0; i < n; ++i) {
                accum += options.fn(i);
            }
            return accum;
        });
        return handlebars;
    }

    public StandardEvaluationContext createSpringEvaluationContext() {
        StandardEvaluationContext springElContext = new StandardEvaluationContext();

        for (Class helper : helpers) {
            Arrays.stream(helper.getMethods()).filter(m ->
                            Modifier.isPublic(m.getModifiers()) &&
                                    Modifier.isStatic(m.getModifiers()))
                    .forEach(m -> springElContext.registerFunction(m.getName(), m));

        }
        return springElContext;
    }
}
