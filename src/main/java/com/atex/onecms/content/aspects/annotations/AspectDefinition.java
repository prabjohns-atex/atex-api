package com.atex.onecms.content.aspects.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an aspect type.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AspectDefinition {
    String[] value() default {};
    boolean storeWithMainAspect() default false;
    int maxVersions() default -1;
}
