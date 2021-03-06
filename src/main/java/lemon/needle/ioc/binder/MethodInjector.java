package lemon.needle.ioc.binder;

import java.lang.reflect.Method;

import javax.inject.Provider;

import lemon.needle.exception.NeedleException;
import lemon.needle.util.CommonUtil;

public class MethodInjector {

    private final Method method;
    private final Provider<?>[] providers;

    public MethodInjector(Method method, Provider<?>[] providers) {
        this.method = method;
        this.providers = providers;
    }

    public Object applyTo(Object bean) {
        try {
            return method.invoke(bean, CommonUtil.params(providers));
        } catch (Exception e) {
            throw new NeedleException(e, "Unable to invoke method[%s] on %s", method.getName(), bean.getClass());
        }
    }

    @Override
    public String toString() {
        return String.format("MethodInjector for %s", method);
    }

    public Method getMethod() {
        return method;
    }

    public Provider[] getProviders() {
        return providers;
    }

}
