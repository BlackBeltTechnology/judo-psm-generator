package hu.blackbelt.judo.psm.archetype.generator;

import lombok.SneakyThrows;

import java.net.URI;
import java.net.URISyntaxException;

public class UriHelper {

    @SneakyThrows(URISyntaxException.class)
    public static URI calculateRelativeURI(URI uri, String path) {
        URI uriRoot = uri;
        if (uriRoot.toString().endsWith(".jar")) {
            uriRoot = new URI(concatUri("jar:" + uriRoot.toString() + "!/", path));
        } else if (uriRoot.toString().startsWith("jar:bundle:")) {
            uriRoot = new URI(uriRoot.toString().substring(4, uriRoot.toString().indexOf("!")) + path);
        } else {
            uriRoot = new URI(concatUri(uriRoot.toString(), path));
        }
        return uriRoot;
    }

     public static String concatUri(String root, String path) {
        String base = root;
        String pathRel = path;
        if (root.endsWith("/")) {
            base = root.substring(0, root.length() - 1);
        }
         if (path.startsWith("/")) {
             pathRel = path.substring(1, root.length());
         }
        return base + "/" + pathRel;
     }

    public static String lastPart(String url) {
        String base = url;
        if (url.endsWith("/")) {
            base = url.substring(0, url.length() - 1);
        }
        String[] urlParts = base.split("/");
        if (urlParts != null && urlParts.length > 0) {
            return urlParts[urlParts.length - 1];
        }
        return null;
    }


}
