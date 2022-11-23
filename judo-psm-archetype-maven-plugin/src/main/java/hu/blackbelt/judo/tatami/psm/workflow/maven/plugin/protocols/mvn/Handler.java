package hu.blackbelt.judo.tatami.psm.workflow.maven.plugin.protocols.mvn;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(final URL u) throws IOException {
        final URL resourceUrl = ClassLoader.getSystemClassLoader().getResource(u.getPath());
        return resourceUrl.openConnection();
    }
}