package lemon.needle.aop;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aopalliance.intercept.MethodInterceptor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.sf.cglib.core.MethodWrapper;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.reflect.FastClass;

/**
 * Builds a construction proxy that can participate in AOP. This class manages applying type and
 * method matchers to come up with the set of intercepted methods.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class ProxyFactory<T> implements ConstructionProxyFactory<T> {

    private static final Logger logger = Logger.getLogger(ProxyFactory.class.getName());

    private final ImmutableMap<Method, List<MethodInterceptor>> interceptors;
    private final Class<T> declaringClass;
    private final List<Method> methods;
    private final Callback[] callbacks;

    /**
     * PUBLIC is default; it's used if all the methods we're intercepting are public. This impacts
     * which classloader we should use for loading the enhanced class
     */
    private BytecodeGen.Visibility visibility = BytecodeGen.Visibility.PUBLIC;

    ProxyFactory(Iterable<MethodAspect> methodAspects) {
        @SuppressWarnings("unchecked") // the member of injectionPoint is always a Constructor<T>
        Constructor<T> constructor = null;//(Constructor<T>) injectionPoint.getMember();

        declaringClass = constructor.getDeclaringClass();

        // Find applicable aspects. Bow out if none are applicable to this class.
        List<MethodAspect> applicableAspects = Lists.newArrayList();
        for (MethodAspect methodAspect : methodAspects) {
            if (methodAspect.matches(declaringClass)) {
                applicableAspects.add(methodAspect);
            }
        }

        if (applicableAspects.isEmpty()) {
            interceptors = ImmutableMap.of();
            methods = ImmutableList.of();
            callbacks = null;
            return;
        }

        // Get list of methods from cglib.
        methods = Lists.newArrayList();
        Enhancer.getMethods(declaringClass, null, methods);

        // Create method/interceptor holders and record indices.
        List<MethodInterceptorsPair> methodInterceptorsPairs = Lists.newArrayList();
        for (Method method : methods) {
            methodInterceptorsPairs.add(new MethodInterceptorsPair(method));
        }

        // Iterate over aspects and add interceptors for the methods they apply to
        boolean anyMatched = false;
        for (MethodAspect methodAspect : applicableAspects) {
            for (MethodInterceptorsPair pair : methodInterceptorsPairs) {
                if (methodAspect.matches(pair.method)) {
                    if (pair.method.isSynthetic()) {
                        logger.log(Level.WARNING, "Method [{0}] is synthetic and is being intercepted by {1}." + " This could indicate a bug.  The method may be intercepted twice," + " or may not be intercepted at all.", new Object[] { pair.method, methodAspect.interceptors() });
                    }
                    visibility = visibility.and(BytecodeGen.Visibility.forMember(pair.method));
                    pair.addAll(methodAspect.interceptors());
                    anyMatched = true;
                }
            }
        }

        if (!anyMatched) {
            interceptors = ImmutableMap.of();
            callbacks = null;
            return;
        }

        ImmutableMap.Builder<Method, List<MethodInterceptor>> interceptorsMapBuilder = null; // lazy

        callbacks = new Callback[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            MethodInterceptorsPair pair = methodInterceptorsPairs.get(i);

            if (!pair.hasInterceptors()) {
                callbacks[i] = net.sf.cglib.proxy.NoOp.INSTANCE;
                continue;
            }

            if (interceptorsMapBuilder == null) {
                interceptorsMapBuilder = ImmutableMap.builder();
            }

            ImmutableList<MethodInterceptor> deDuplicated = ImmutableSet.copyOf(pair.interceptors).asList();
            interceptorsMapBuilder.put(pair.method, deDuplicated);
            callbacks[i] = new InterceptorStackCallback(pair.method, deDuplicated);
        }

        interceptors = interceptorsMapBuilder != null ? interceptorsMapBuilder.build() : ImmutableMap.<Method, List<MethodInterceptor>> of();
    }

    /** Returns the interceptors that apply to the constructed type. */
    public ImmutableMap<Method, List<MethodInterceptor>> getInterceptors() {
        return interceptors;
    }

    @Override
    public ConstructionProxy<T> create(Constructor<T> contructor) throws Exception {
        if (interceptors.isEmpty()) {
            return new DefaultConstructionProxyFactory<T>().create(contructor);
        }

        @SuppressWarnings("unchecked")
        Class<? extends Callback>[] callbackTypes = new Class[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] == net.sf.cglib.proxy.NoOp.INSTANCE) {
                callbackTypes[i] = net.sf.cglib.proxy.NoOp.class;
            } else {
                callbackTypes[i] = net.sf.cglib.proxy.MethodInterceptor.class;
            }
        }

        // Create the proxied class. We're careful to ensure that all enhancer state is not-specific
        // to this injector. Otherwise, the proxies for each injector will waste PermGen memory
        try {
            Enhancer enhancer = BytecodeGen.newEnhancer(declaringClass, visibility);
            enhancer.setCallbackFilter(new IndicesCallbackFilter(methods));
            enhancer.setCallbackTypes(callbackTypes);
            return new ProxyConstructor<T>(enhancer, contructor, callbacks, interceptors);
        } catch (Throwable e) {
            throw new Exception(declaringClass.getName(), e);
        }
    }

    private static class MethodInterceptorsPair {
        final Method method;
        List<MethodInterceptor> interceptors; // lazy

        MethodInterceptorsPair(Method method) {
            this.method = method;
        }

        void addAll(List<MethodInterceptor> interceptors) {
            if (this.interceptors == null) {
                this.interceptors = Lists.newArrayList();
            }
            this.interceptors.addAll(interceptors);
        }

        boolean hasInterceptors() {
            return interceptors != null;
        }
    }

    /**
     * A callback filter that maps methods to unique IDs. We define equals and hashCode without using
     * any state related to the injector so that enhanced classes intercepting the same methods can be
     * shared between injectors (and child injectors, etc).
     */
    private static class IndicesCallbackFilter implements CallbackFilter {
        final Map<Object, Integer> indices;
        final int hashCode;

        IndicesCallbackFilter(List<Method> methods) {
            final Map<Object, Integer> indices = Maps.newHashMap();
            for (int i = 0; i < methods.size(); i++) {
                indices.put(MethodWrapper.create(methods.get(i)), i);
            }
            this.indices = indices;
            this.hashCode = indices.hashCode();
        }

        @Override
        public int accept(Method method) {
            return indices.get(MethodWrapper.create(method));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IndicesCallbackFilter && ((IndicesCallbackFilter) o).indices.equals(indices);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /** Constructs instances that participate in AOP. */
    private static class ProxyConstructor<T> implements ConstructionProxy<T> {
        final Class<?> enhanced;
        final Constructor<T> constructor;
        final Callback[] callbacks;

        final int constructorIndex;
        final ImmutableMap<Method, List<MethodInterceptor>> methodInterceptors;
        final FastClass fastClass;

        @SuppressWarnings("unchecked") // the constructor promises to construct 'T's
        ProxyConstructor(Enhancer enhancer, Constructor<T> contructor, Callback[] callbacks, ImmutableMap<Method, List<MethodInterceptor>> methodInterceptors) {
            this.enhanced = enhancer.createClass(); // this returns a cached class if possible
            this.constructor = contructor;
            this.callbacks = callbacks;
            this.methodInterceptors = methodInterceptors;
            this.fastClass = BytecodeGen.newFastClassForMember(enhanced, constructor);
            this.constructorIndex = fastClass.getIndex(constructor.getParameterTypes());
        }

        @Override
        @SuppressWarnings("unchecked") // the constructor promises to produce 'T's
        public T newInstance(Object... arguments) throws InvocationTargetException {
            Enhancer.registerCallbacks(enhanced, callbacks);
            try {
                return (T) fastClass.newInstance(constructorIndex, arguments);
            } finally {
                Enhancer.registerCallbacks(enhanced, null);
            }
        }

        @Override
        public Constructor<T> getConstructor() {
            return constructor;
        }

        @Override
        public ImmutableMap<Method, List<MethodInterceptor>> getMethodInterceptors() {
            return methodInterceptors;
        }
    }
}
