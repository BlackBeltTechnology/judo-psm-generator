package hu.blackbelt.psm.archetype.maven.plugin.test;

import com.github.jknack.handlebars.internal.lang3.StringUtils;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@Log
public class TestHelper {

    public static String plainName(String input) {
        return input
//                .replaceAll("[^\\\\u(\\p{XDigit}{4})]", "_")
                .replaceAll("[^\\.A-Za-z0-9_]", "_").toLowerCase();
    }

    public static String cleanup(String string) {
        return string.replaceAll("[\\n\\t ]", "");
    }

    public static String pathName(String fqName) {
        return fqName
                .replaceAll("\\.", "__")
                .replaceAll("::", "__")
                .replaceAll("#", "__")
                .replaceAll("/", "__")
                .replaceAll("([a-z])([A-Z]+)", "$1_$2")
                .toLowerCase();
    }

    public static String path(String fqName) {
        String fq = pathName(fqName);
        if (fq.lastIndexOf("__") > -1) {
            return fq.substring(fq.lastIndexOf("__") + 2);
        } else {
            return fq;
        }
    }

    public static String packageName(String packageName) {
        List<String> nameTokens = stream(packageName
                .split("::"))
                .collect(Collectors.toList());
        if (nameTokens.size() > 2) {
            nameTokens.remove(0);
            nameTokens.remove(nameTokens.size() - 1);
            return nameTokens.stream()
                    .map(s -> StringUtils.capitalize(
                            stream(s.replaceAll("#", "::")
                                    .replaceAll("\\.", "::")
                                    .replaceAll("/", "::")
                                    .replaceAll("_", "::")
                                    .split("::"))
                                    .map(t -> StringUtils.capitalize(t))
                                    .collect(Collectors.joining())
                    ))
                    .collect(Collectors.joining());
        }
        return null;
    }

    public static String modelName(String fqName) {
        String[] splitted = fqName.split("::");
        return fqClass(stream(splitted)
                .map(s -> StringUtils.capitalize(s))
                .findFirst().get());
    }

    @Deprecated
    public static String fqClass(String fqName) {
        return stream(fqName.replaceAll("#", "::")
                .replaceAll("\\.", "::")
                .replaceAll("/", "::")
                .replaceAll("_", "::")
                .split("::"))
                .map(s -> StringUtils.capitalize(s))
                .collect(Collectors.joining());
    }

    public static String fqClassWithoutModel(String fqName) {
        return stream(fqName.replaceAll("#", "::")
                .replaceAll("\\.", "::")
                .replaceAll("/", "::")
                .split("::"))
                .skip(1)
                .map(s -> StringUtils.capitalize(s))
                .collect(Collectors.joining());
    }

}
