package com.solubris;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This annotation suggests that the subject can be moved to a library in the future.
 *
 * <p>Normally a method sits in its original sites for a while to see how it goes before extraction.
 * But in this case, it's easy to forget such methods.
 * That is where this annotation helps.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {METHOD, PACKAGE, TYPE})
public @interface ToBeShared {
    /**
     * The target library to be moved to.
     *
     * <p>This is only a suggestion as by the time it comes for extraction, things may have changed.
     */
    String target() default "";
}