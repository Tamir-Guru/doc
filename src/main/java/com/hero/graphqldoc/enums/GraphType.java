/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.enums;

public enum GraphType {

    QUERY("Query"),
    MUTATION("Mutation"),
    SUBSCRIPTION("Subscription");

    private final String type;

    GraphType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

}
