package hu.blackbelt.judo.tatami.client.workflow.maven.plugin;

import com.github.jknack.handlebars.io.TemplateSource;
import hu.blackbelt.judo.psm.archetype.generator.ChainedURLTemplateLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

public class MavenPluginExtendedChainedURLTemplateLoader extends ChainedURLTemplateLoader {
    public MavenPluginExtendedChainedURLTemplateLoader(ChainedURLTemplateLoader parent, URI root) {
        super(parent, root);
    }

    @Override
    public TemplateSource sourceAt(String location) throws IOException {
        return super.sourceAt(location);
    }

    @Override
    public URL getResource(String location) throws IOException {
        return super.getResource(location);
    }
}
