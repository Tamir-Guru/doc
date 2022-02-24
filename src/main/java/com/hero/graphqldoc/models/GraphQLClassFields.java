/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GraphQLClassFields {

    private String description;
    private String name;
    private List<GraphQLField> fields = new ArrayList<>();

}
