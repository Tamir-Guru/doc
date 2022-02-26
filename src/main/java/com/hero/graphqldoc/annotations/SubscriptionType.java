/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.annotations;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@GraphQLType(type = "Subscription")
public @interface SubscriptionType {

    @AliasFor(annotation = GraphQLType.class, attribute = "description")
    String description() default "";

    @AliasFor(annotation = GraphQLType.class, attribute = "key")
    String key() default "";

}
