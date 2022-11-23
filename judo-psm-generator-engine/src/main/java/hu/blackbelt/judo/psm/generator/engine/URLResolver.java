package hu.blackbelt.judo.psm.generator.engine;

import java.io.IOException;
import java.net.URL;

public interface URLResolver {
    URL getResource(String location) throws IOException;
}
