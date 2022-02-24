/*
 * @author : Oguz Kahraman
 * @since : 20.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import lombok.Data;

import java.util.List;

@Data
public class GraphQLObject {

    private String description;
    private String key;
    private List<GraphQLMethodObject> objects;

}
