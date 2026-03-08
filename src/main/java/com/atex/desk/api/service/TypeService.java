package com.atex.desk.api.service;

import com.atex.onecms.ace.annotations.AceAspect;
import com.atex.onecms.content.aspects.annotations.AspectDefinition;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates ModelTypeBean responses from @AspectDefinition-annotated bean classes.
 * Replaces the legacy approach of storing types as content in the database.
 */
@Service
public class TypeService {

    private static final Logger LOG = Logger.getLogger(TypeService.class.getName());

    // ModelTypeFieldData modifier constants (from com.polopoly.model.ModelTypeFieldData)
    private static final long MOD_READ = 0x0001;
    private static final long MOD_WRITE = 0x0002;
    private static final long MOD_STATIC = 0x0008;
    private static final long MOD_TRANSIENT = 0x0100;

    private static final String[] SCAN_PACKAGES = {
        "com.atex.onecms",
        "com.atex.plugins",
        "com.atex.desk"
    };

    /** aspectName → bean class, last registered wins (plugin override) */
    private final Map<String, Class<?>> typeRegistry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        scanForAspectDefinitions();
        LOG.info("TypeService initialized with " + typeRegistry.size() + " type definitions");
    }

    private void scanForAspectDefinitions() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(AspectDefinition.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(AceAspect.class));

        for (String pkg : SCAN_PACKAGES) {
            for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
                    registerFromAnnotations(clazz);
                } catch (ClassNotFoundException e) {
                    LOG.log(Level.WARNING, "Could not load class: " + bd.getBeanClassName(), e);
                }
            }
        }
    }

    private void registerFromAnnotations(Class<?> clazz) {
        // Check @AspectDefinition
        AspectDefinition def = clazz.getAnnotation(AspectDefinition.class);
        if (def != null) {
            for (String aspectName : def.value()) {
                registerWithOverride(aspectName, clazz);
            }
        }
        // Check @AceAspect
        AceAspect ace = clazz.getAnnotation(AceAspect.class);
        if (ace != null) {
            registerWithOverride(ace.value(), clazz);
        }
    }

    private void registerWithOverride(String aspectName, Class<?> clazz) {
        Class<?> existing = typeRegistry.get(aspectName);
        if (existing != null) {
            LOG.fine("Type '" + aspectName + "' overridden: "
                + existing.getName() + " → " + clazz.getName());
        }
        typeRegistry.put(aspectName, clazz);
    }

    /**
     * Register a type definition programmatically (for plugin override).
     */
    public void registerType(String aspectName, Class<?> beanClass) {
        typeRegistry.put(aspectName, beanClass);
    }

    /**
     * Get the ModelTypeBean response for a given type name.
     * @return the type response map, or null if type not found
     */
    public Map<String, Object> getType(String typeName) {
        Class<?> beanClass = typeRegistry.get(typeName);
        if (beanClass == null) {
            return null;
        }
        return buildModelTypeBean(typeName, beanClass);
    }

    private Map<String, Object> buildModelTypeBean(String typeName, Class<?> beanClass) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("_type", "com.polopoly.model.ModelTypeBean");
        result.put("typeName", typeName);
        result.put("typeClass", "bean");
        result.put("generator", "desk-api");

        List<Map<String, Object>> attributes = new ArrayList<>();

        // Introspect bean properties (including inherited)
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Object.class);
            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                String name = pd.getName();
                // Skip "class" pseudo-property
                if ("class".equals(name)) continue;

                Method getter = pd.getReadMethod();
                Method setter = pd.getWriteMethod();
                if (getter == null) continue;

                long modifiers = 0;
                if (getter != null) modifiers |= MOD_READ;
                if (setter != null) modifiers |= MOD_WRITE;

                String javaTypeName = getTypeName(getter);

                Map<String, Object> attr = new LinkedHashMap<>();
                attr.put("name", name);
                attr.put("typeName", javaTypeName);
                attr.put("modifiers", modifiers);
                attributes.add(attr);
            }
        } catch (IntrospectionException e) {
            LOG.log(Level.WARNING, "Failed to introspect bean: " + beanClass.getName(), e);
        }

        // Add metadata attributes (matching Polopoly ModelTypePojo format)

        // p.beanClass — static read-only
        attributes.add(metadataAttribute("p.beanClass", "java.lang.String",
            MOD_READ | MOD_STATIC, beanClass.getName()));

        // p.publicInterfaces — static read-only
        String publicInterfaces = getPublicInterfaces(beanClass);
        attributes.add(metadataAttribute("p.publicInterfaces", "java.lang.String",
            MOD_READ | MOD_STATIC, publicInterfaces));

        // p.mt — static read-only
        attributes.add(metadataAttribute("p.mt", "java.lang.String",
            MOD_READ | MOD_STATIC, typeName));

        // _data — transient read-only
        attributes.add(metadataAttribute("_data", "java.lang.String",
            MOD_READ | MOD_TRANSIENT, null));

        result.put("attributes", attributes);
        result.put("beanClass", beanClass.getName());
        result.put("addAll", "true");

        return result;
    }

    private Map<String, Object> metadataAttribute(String name, String typeName,
                                                    long modifiers, String value) {
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("name", name);
        attr.put("typeName", typeName);
        attr.put("modifiers", modifiers);
        if (value != null) {
            attr.put("value", value);
        }
        return attr;
    }

    /**
     * Get the Java type name for a getter method's return type,
     * including generic parameters (e.g., java.util.List<com.atex.onecms.content.ContentId>).
     */
    private String getTypeName(Method getter) {
        Type genericType = getter.getGenericReturnType();

        if (genericType instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            Type[] typeArgs = pt.getActualTypeArguments();

            StringBuilder sb = new StringBuilder();
            sb.append(((Class<?>) rawType).getName());
            if (typeArgs.length > 0) {
                sb.append('<');
                for (int i = 0; i < typeArgs.length; i++) {
                    if (i > 0) sb.append(", ");
                    if (typeArgs[i] instanceof Class<?> c) {
                        sb.append(c.getName());
                    } else {
                        sb.append(typeArgs[i].getTypeName());
                    }
                }
                sb.append('>');
            }
            return sb.toString();
        }

        Class<?> returnType = getter.getReturnType();
        // Use primitive names for primitives
        if (returnType.isPrimitive()) {
            return returnType.getName();
        }
        return returnType.getName();
    }

    /**
     * Get comma-separated list of public interfaces implemented by the bean class.
     */
    private String getPublicInterfaces(Class<?> beanClass) {
        Class<?>[] interfaces = beanClass.getInterfaces();
        if (interfaces.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < interfaces.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(interfaces[i].getName());
        }
        return sb.toString();
    }
}
