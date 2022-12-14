package hu.blackbelt.judo.psm.generator.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.internal.lang3.builder.ReflectionToStringBuilder;
import com.github.jknack.handlebars.internal.lang3.builder.ToStringStyle;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;


/**
 * Generator model describes a collection of generator template and global expression mapped to variables.
 */
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder(builderMethodName = "generatorModelBuilder")
public class GeneratorModel {

	@Builder.Default
	private String name = "not set";

	@Builder.Default
	private Collection<TemplateSpringELExpression> templateContext = new HashSet();


	@Builder.Default
	private Collection<GeneratorTemplate> templates = new HashSet<>();

	public static GeneratorModel loadYamlURL(String originalUri, URL yaml) throws IOException {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);
		GeneratorModel model = null;
		try {
			InputStream is = yaml.openStream();
			model = mapper.readValue(is, new TypeReference<GeneratorModel>(){});
			if (model.getTemplates() == null) {
				model.setTemplates(new ArrayList<>());
			}
			model.getTemplates().forEach(t -> {
				t.setTemplateBaseUri(originalUri);
			});
		} catch (FileNotFoundException e) {
			log.warn("Yaml file not defined: " + yaml.toString());
		} catch (IOException e) {
			throw new IllegalArgumentException("Yaml file read error: " + yaml.toString(), e);
		}
		if (model != null) {
			log.debug(ReflectionToStringBuilder.toString(model.getTemplates(), ToStringStyle.MULTI_LINE_STYLE));
		}
		return model;
	}

	public void overrideTemplates(Collection<GeneratorTemplate> overridedTemplates) {
		Collection<GeneratorTemplate> replaceableTemplates = new HashSet<>();
		templates.forEach(t -> {
			overridedTemplates.stream().filter(o -> o.getName().equals(t.getTemplateName())).forEach(f -> replaceableTemplates.add(f));
		});
		templates.removeAll(replaceableTemplates);
		templates.addAll(overridedTemplates);
	}
}

