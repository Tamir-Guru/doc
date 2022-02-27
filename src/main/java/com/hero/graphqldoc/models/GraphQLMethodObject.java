/*
 * @author : Oguz Kahraman
 * @since : 20.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import lombok.Data;

@Data
public class GraphQLMethodObject {

    private String description;
    private String operation;
    private String name;
    private String methodName;
    private String outputJson;
    private String outputName;
    private String inputJson;
    private String authString;

}
