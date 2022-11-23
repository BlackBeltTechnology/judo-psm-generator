package hu.blackbelt.judo.psm.generator.engine;

import com.github.jknack.handlebars.ValueResolver;
import com.google.common.collect.ImmutableSet;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;

import java.util.*;

import static org.apache.commons.lang3.Validate.notNull;

public class PsmValueResolver implements ValueResolver {
    @Override
    public Object resolve(Object context, String name) {
        if (context instanceof ActorType) {
            ActorType actorType = (ActorType) context;
            if ("fQName".equals(name)) {
                return PsmUtils.namespaceToString(actorType.getNamespace()) + "::" + actorType.getName();
            }
        }
        return UNRESOLVED;
    }

    @Override
    public Object resolve(final Object context) {
        return UNRESOLVED;
    }

    @Override
    public Set<Map.Entry<String, Object>> propertySet(Object context) {
        return ImmutableSet.of();
    }
}
