package hu.blackbelt.judo.psm.archetype.generator;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.accesspoint.ActorType;

import java.io.IOException;

public enum ActorTypeHelpers implements Helper<ActorType> {

    fQName33 {
        @Override
        public Object apply(ActorType context, Options options) throws IOException {
            return options.fn(PsmUtils.namespaceToString(context.getNamespace()) + "::" + context.getName());
        }
    }
}

