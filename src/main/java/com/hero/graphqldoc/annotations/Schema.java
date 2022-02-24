/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Schema {

    String description() default "";

}
