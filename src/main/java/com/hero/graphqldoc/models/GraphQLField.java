/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import lombok.Data;

@Data
public class GraphQLField {

    private Boolean required = false;
    private String example = "";
    private String description = "";
    private String name = "";
    private String type = "";
    private Boolean javaType = true;
    private Boolean listType = false;

}
