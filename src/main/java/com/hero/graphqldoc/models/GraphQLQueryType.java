/*
 * @author : Oguz Kahraman
 * @since : 21.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import com.hero.graphqldoc.enums.ElementType;
import lombok.Data;


@Data
public class GraphQLQueryType {

    private String name;
    private boolean notNull = Boolean.FALSE;
    private ElementType elementType = ElementType.TYPE;
    private String outputName;

}
