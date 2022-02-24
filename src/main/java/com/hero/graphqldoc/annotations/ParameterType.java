/*
 * @author : Oguz Kahraman
 * @since : 23.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ParameterType {

    String example() default "";

    String description() default "";

    boolean required() default true;

}
