/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GraphQLTypeDetails {

    private Map<String, GraphQLQueryType> inputs = new HashMap<>();
    private GraphQLQueryType qlQueryType;

}
