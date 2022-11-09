package hu.blackbelt.judo.psm.archetype.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.internal.lang3.builder.ReflectionToStringBuilder;
import com.github.jknack.handlebars.internal.lang3.builder.ToStringStyle;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;


/**
 * Generator template describes one generation properties.
 * <ul>
 *     <li>factoryExpression: A SpringEL Expression which can result a collection which will be used as list ofg generated output. The collection's elements is used
 *     as the 'self' inside the template and pathExpression.</li>
 *     <li>pathExpression: A SpringEL Expression which is used what is the target of the generated template. It returns a string which is a relative path. When `actorTypeBased` is used,
 *     the target directory will be created as the actor target concated with actor's target directory</li>
 *     <li>actorTypeBased: boolean arguments. When it set the template factory will be executed in all actors, and the `actorType` will be placed in template context.</li>
 *     <li>templateName: the relative path for template used for generation. (except `copy` is false.</li>
 *     <li>copy: when it set generation will be ignored, the file file will be copied. When factoryExpression returns several elements, the file will be copied several times.</li>
 * </ul>
 */
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder(builderMethodName = "generatorTemplateBuilder")
public class GeneratorTemplate {

	private String factoryExpression;
	private String pathExpression;

	private String template;
	private String templateName;

	@Builder.Default
	private boolean actorTypeBased = false;

	@Builder.Default
	private Collection<Expression> templateContext = new HashSet();

	@Builder.Default
	@Getter
	private ExpressionParser parser = new SpelExpressionParser();

	@Builder.Default
	private boolean copy = false;

	public Map<String, org.springframework.expression.Expression> parseExpressions() {
		Map<String, org.springframework.expression.Expression> templateExpressions = new HashMap<>();
		templateContext.stream().forEach(ctx -> {
			final org.springframework.expression.Expression contextTemplate = parser.parseExpression(ctx.getExpression());
			templateExpressions.put(ctx.getName(), contextTemplate);
		});
		return templateExpressions;
	}


	@AllArgsConstructor
	@NoArgsConstructor
	@Getter
	@Setter
	public static class Expression {
		private String name;
		private String expression;
	}
	public static Collection<GeneratorTemplate> loadYamlURL(URL yaml) throws IOException {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		List<GeneratorTemplate> templates = new ArrayList<>();
		try {
			InputStream is = yaml.openStream();
			templates = mapper.readValue(is, new TypeReference<List<GeneratorTemplate>>(){});
		} catch (FileNotFoundException e) {
			log.warn("Yaml file not defined: " + yaml.toString());
		} catch (IOException e) {
			log.warn("Yaml file read error: " + yaml.toString(), e);
		}

		log.debug(ReflectionToStringBuilder.toString(templates, ToStringStyle.MULTI_LINE_STYLE));
		return templates;
	}

	public TemplateEvaluator getTemplateEvalulator(PsmGeneratorContext projectGenerator, StandardEvaluationContext standardEvaluationContext) throws IOException {
		return new TemplateEvaluator(projectGenerator, this, standardEvaluationContext);
	}

	public void evalToContextBuilder(TemplateEvaluator templateEvaulator, Context.Builder contextBuilder, StandardEvaluationContext templateExpressionContext) {
		templateContext.stream().forEach(ctx -> {

			Class type = templateEvaulator.getTemplateExpressions().get(ctx.getName()).getValueType(templateExpressionContext);
			contextBuilder.combine(ctx.getName(),
					templateEvaulator.getTemplateExpressions().get(ctx.getName()).getValue(templateExpressionContext,
							templateEvaulator.getTemplateExpressions().get(ctx.getName()).getValue(templateExpressionContext, type)));
		});
	}

}

