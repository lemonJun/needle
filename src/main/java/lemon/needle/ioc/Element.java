package lemon.needle.ioc;

import javax.lang.model.util.Elements;

import lemon.needle.ioc.binder.Binder;

/**
 * A core component of a module or injector.
 *
 * <p>The elements of a module can be inspected, validated and rewritten. Use {@link
 * Elements#getElements(com.google.inject.Module[]) Elements.getElements()} to read the elements
 * from a module, and {@link Elements#getModule(Iterable) Elements.getModule()} to rewrite them.
 * This can be used for static analysis and generation of Guice modules.
 *
 * <p>The elements of an injector can be inspected and exercised. Use {@link
 * com.google.InjectorOld.Injector#getBindings Injector.getBindings()} to reflect on Guice injectors.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author crazybob@google.com (Bob Lee)
 * @since 2.0
 */
public interface Element {

    /**
     * Returns an arbitrary object containing information about the "place" where this element was
     * configured. Used by Guice in the production of descriptive error messages.
     *
     * <p>Tools might specially handle types they know about; {@code StackTraceElement} is a good
     * example. Tools should simply call {@code toString()} on the source object if the type is
     * unfamiliar.
     */
    Object getSource();

    /**
     * Accepts an element visitor. Invokes the visitor method specific to this element's type.
     *
     * @param visitor to call back on
     */
    //    <T> T acceptVisitor(ElementVisitor<T> visitor);

    /**
     * Writes this module element to the given binder (optional operation).
     *
     * @param binder to apply configuration element to
     * @throws UnsupportedOperationException if the {@code applyTo} method is not supported by this
     *     element.
     */
    void applyTo(Binder binder);

}