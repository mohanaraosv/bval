package org.apache.bval.jsr.job;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.validation.Path;

import org.apache.bval.jsr.ApacheFactoryContext;
import org.apache.bval.jsr.ConstraintViolationImpl;
import org.apache.bval.jsr.GraphContext;
import org.apache.bval.jsr.descriptor.ConstraintD;
import org.apache.bval.jsr.descriptor.ExecutableD;
import org.apache.bval.jsr.descriptor.ReturnValueD;
import org.apache.bval.jsr.metadata.Metas;
import org.apache.bval.jsr.util.NodeImpl;
import org.apache.bval.jsr.util.PathImpl;
import org.apache.bval.util.Exceptions;
import org.apache.bval.util.Validate;
import org.apache.bval.util.reflection.TypeUtils;

public abstract class ValidateReturnValue<E extends Executable, T> extends ValidationJob<T> {
    public static class ForMethod<T> extends ValidateReturnValue<Method, T> {
        private final T object;

        ForMethod(ApacheFactoryContext validatorContext, T object, Method method, Object returnValue,
            Class<?>[] groups) {
            super(validatorContext,
                new Metas.ForMethod(Validate.notNull(method, IllegalArgumentException::new, "method")), returnValue,
                groups);
            this.object = Validate.notNull(object, IllegalArgumentException::new, "object");
        }

        @Override
        protected T getRootBean() {
            return object;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<T> getRootBeanClass() {
            return (Class<T>) object.getClass();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected ExecutableD<Method, ?, ?> describe() {
            return (ExecutableD<Method, ?, ?>) validatorContext.getDescriptorManager()
                .getBeanDescriptor(object.getClass())
                .getConstraintsForMethod(executable.getName(), executable.getParameterTypes());
        }
    }

    public static class ForConstructor<T> extends ValidateReturnValue<Constructor<?>, T> {

        ForConstructor(ApacheFactoryContext validatorContext, Constructor<? extends T> ctor, Object returnValue,
            Class<?>[] groups) {
            super(validatorContext,
                new Metas.ForConstructor(Validate.notNull(ctor, IllegalArgumentException::new, "ctor")), returnValue,
                groups);
        }

        @Override
        protected T getRootBean() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<T> getRootBeanClass() {
            return (Class<T>) executable.getDeclaringClass();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected ExecutableD<Constructor<T>, ?, ?> describe() {
            return (ExecutableD<Constructor<T>, ?, ?>) validatorContext.getDescriptorManager()
                .getBeanDescriptor(executable.getDeclaringClass())
                .getConstraintsForConstructor(executable.getParameterTypes());
        }
    }

    protected final E executable;
    private final Object returnValue;

    ValidateReturnValue(ApacheFactoryContext validatorContext, Metas<E> meta, Object returnValue, Class<?>[] groups) {
        super(validatorContext, groups);

        final Type type = Validate.notNull(meta, "meta").getType();
        Exceptions.raiseUnless(TypeUtils.isInstance(returnValue, type), IllegalArgumentException::new,
            "%s is not an instance of %s", returnValue, type);

        this.executable = meta.getHost();
        this.returnValue = returnValue;
    }

    @Override
    protected Frame<?> computeBaseFrame() {
        final PathImpl path = PathImpl.create();
        path.addNode(new NodeImpl.ReturnValueNodeImpl());

        return new SproutFrame<ReturnValueD<?, ?>>((ReturnValueD<?, ?>) describe().getReturnValueDescriptor(),
            new GraphContext(validatorContext, path, returnValue)) {
            @Override
            Object getBean() {
                return getRootBean();
            }
        };
    }

    @Override
    ConstraintViolationImpl<T> createViolation(String messageTemplate, ConstraintValidatorContextImpl<T> context,
        Path propertyPath) {

        final String message = validatorContext.getMessageInterpolator().interpolate(messageTemplate, context);

        return new ConstraintViolationImpl<>(messageTemplate, message, getRootBean(), context.getFrame().getBean(),
            propertyPath, context.getFrame().context.getValue(), context.getConstraintDescriptor(), getRootBeanClass(),
            context.getConstraintDescriptor().unwrap(ConstraintD.class).getDeclaredOn(), returnValue, null);
    }

    protected abstract ExecutableD<?, ?, ?> describe();

    protected abstract T getRootBean();
}
