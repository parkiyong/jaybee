package io.github.parkiyong.jaybee.core.handler;

import io.github.parkiyong.jaybee.api.exception.JaybeeException;
import io.github.parkiyong.jaybee.api.model.HandlerDescriptor;
import io.github.parkiyong.jaybee.api.model.HandlerField;
import io.github.parkiyong.jaybee.api.spi.ExpressionEvaluator;
import io.github.parkiyong.jaybee.api.spi.HandlerResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Default {@link HandlerResolver} supporting expression-resolved handlers and
 * reflective class instantiation with {@code <field>} / {@code <property>} injection.
 */
public final class ReflectiveHandlerResolver implements HandlerResolver {

    @Override
    public Object resolve(
            HandlerDescriptor descriptor,
            ExpressionEvaluator expressionEvaluator,
            Function<String, Object> variableLookup
    ) {
        if (descriptor.expression() != null && !descriptor.expression().isBlank()) {
            Object resolved = expressionEvaluator.evaluate(descriptor.expression(), variableLookup);
            if (resolved == null) {
                throw new JaybeeException("Handler expression evaluated to null: " + descriptor.expression());
            }
            return resolved;
        }

        String className = descriptor.className();
        Object instance = instantiate(className);
        for (HandlerField field : descriptor.fields()) {
            Object rawValue = field.value();
            if (rawValue instanceof String s && (s.contains("#{") || s.contains("${"))) {
                rawValue = expressionEvaluator.evaluate(s, variableLookup);
            }
            inject(instance, field.name(), rawValue, field.property());
        }
        return instance;
    }

    private Object instantiate(String className) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ReflectiveHandlerResolver.class.getClassLoader();
            }
            Class<?> clazz = Class.forName(className, true, cl);
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new JaybeeException("Failed to instantiate handler class: " + className, e);
        }
    }

    private void inject(Object target, String name, Object value, boolean preferProperty) {
        if (preferProperty && tryInvokeSetter(target, name, value)) {
            return;
        }
        if (trySetField(target, name, value)) {
            return;
        }
        if (!preferProperty && tryInvokeSetter(target, name, value)) {
            return;
        }
        throw new JaybeeException(
                "Cannot inject " + (preferProperty ? "property" : "field")
                        + " '" + name + "' on handler class " + target.getClass().getName()
        );
    }

    private boolean tryInvokeSetter(Object target, String name, Object value) {
        String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                Object converted = convertValue(value, paramType);
                try {
                    method.setAccessible(true);
                    method.invoke(target, converted);
                    return true;
                } catch (Exception e) {
                    throw new JaybeeException(
                            "Failed to invoke setter " + setterName + " on " + target.getClass().getName(), e
                    );
                }
            }
        }
        return false;
    }

    private boolean trySetField(Object target, String name, Object value) {
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                Object converted = convertValue(value, field.getType());
                field.set(target, converted);
                return true;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Exception e) {
                throw new JaybeeException(
                        "Failed to inject field '" + name + "' on " + target.getClass().getName(), e
                );
            }
        }
        return false;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        String text = String.valueOf(value);
        if (targetType == String.class) {
            return text;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(text.trim());
        }
        if (targetType == long.class || targetType == Long.class) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(text.trim());
        }
        if (targetType == double.class || targetType == Double.class) {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(text.trim());
        }
        if (targetType == float.class || targetType == Float.class) {
            return value instanceof Number n ? n.floatValue() : Float.parseFloat(text.trim());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return value instanceof Boolean b ? b : Boolean.parseBoolean(text.trim());
        }
        if (targetType == short.class || targetType == Short.class) {
            return value instanceof Number n ? n.shortValue() : Short.parseShort(text.trim());
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return value instanceof Number n ? n.byteValue() : Byte.parseByte(text.trim());
        }
        return value;
    }
}
