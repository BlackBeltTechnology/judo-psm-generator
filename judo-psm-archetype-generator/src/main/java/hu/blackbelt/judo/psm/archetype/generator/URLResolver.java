package hu.blackbelt.judo.psm.archetype.generator;

import java.io.IOException;
import java.net.URL;

public interface URLResolver {
    URL getResource(String location) throws IOException;
}
