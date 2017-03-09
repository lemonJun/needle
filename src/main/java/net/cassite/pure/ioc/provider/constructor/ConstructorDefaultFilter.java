package net.cassite.pure.ioc.provider.constructor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import net.cassite.pure.ioc.Scope;
import net.cassite.pure.ioc.annotations.DefaultBy;
import net.cassite.pure.ioc.exception.AnnoHandleException;
import net.cassite.pure.ioc.provider.ConstructorFilter;
import net.cassite.pure.ioc.provider.ConstructorFilterChain;
import net.cassite.style.reflect.ConstructorSup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.cassite.style.Style.*;
import static net.cassite.style.aggregation.Aggregation.*;

/**
 * Constructor Filter handling Default annotation <br>
 * return the constructor with Default annotation attached.
 *
 * @author lemon
 * @see DefaultBy
 */
public class ConstructorDefaultFilter implements ConstructorFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConstructorDefaultFilter.class);

    @Override
    public boolean canHandle(Set<Annotation> annotations) {
        for (Annotation ann : annotations) {
            if (ann.annotationType() == DefaultBy.class) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ConstructorSup<Object> handle(Scope scope, List<ConstructorSup<Object>> cons, ConstructorFilterChain chain) throws AnnoHandleException {
        LOGGER.debug("Entered ConstructorDefaultFilter with args:\n\tcons:\t{}\n\tchain:\t{}", cons, chain);
        ConstructorSup<Object> nextRes = chain.next().handle(scope, cons, chain);
        if (null == nextRes) {
            LOGGER.debug("start handling with ConstructorDefaultFilter");
        }
        return nextRes == null ? If($(cons).findOne(c -> c.isAnnotationPresent(DefaultBy.class)), c -> c).Else(() -> null) : nextRes;
    }

}
