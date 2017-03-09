package lemon.ioc.di.provider.param;

import net.cassite.style.reflect.MemberSup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lemon.ioc.di.Scope;
import lemon.ioc.di.annotations.Ignore;
import lemon.ioc.di.exception.AnnoHandleException;
import lemon.ioc.di.provider.*;

import java.lang.annotation.Annotation;

import static net.cassite.style.aggregation.Aggregation.*;

/**
 * Handler for Ignore annotation. <br>
 * the param would be ignored.
 *
 * @author lemon
 * @see Ignore
 * @since 0.3.1
 */
public class ParamIgnoreHandler implements ParamAnnotationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParamIgnoreHandler.class);

    @Override
    public boolean canHandle(Annotation[] annotations) {
        return $(annotations).findOne(a -> a instanceof Ignore) != null;
    }

    @Override
    public Object handle(Scope scope, MemberSup<?> caller, Class<?> cls, Class<?> expectedClass, Annotation[] toHandle, ParamHandlerChain chain) throws AnnoHandleException {
        LOGGER.debug("Entered ParamIgnoreHandler with args:\n\tcaller:\t{}\n\tcls:\t{}\n\ttoHandle:\t{}\n\tchain:\t{}", caller, cls, toHandle, chain);
        try {
            return chain.next().handle(scope, caller, cls, expectedClass, toHandle, chain);
        } catch (IrrelevantAnnotationHandlingException e) {
            LOGGER.debug("Start handling with ParamIgnoreHandler");
            throw new IgnoredAnnotationHandlingException();
        }
    }
}