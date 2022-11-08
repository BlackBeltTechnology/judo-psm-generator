package hu.blackbelt.judo.psm.archetype.generator;

import com.github.jknack.handlebars.ValueResolver;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;

import java.util.*;

import static org.apache.commons.lang3.Validate.notNull;

public class ActorTypeValueResolver implements ValueResolver {
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
        notNull(context, "The context is required.");
        if (context instanceof Map) {
            return Collections.emptySet();
        } else if (context instanceof Collection) {
            return Collections.emptySet();
        }
        //Collection<M> members = cache(context.getClass()).values();
        Map<String, Object> propertySet = new LinkedHashMap<>();
        propertySet.put("fQName", resolve(context, "fQName"));

        return propertySet.entrySet();
    }
}
