/*
 * @author : Oguz Kahraman
 * @since : 23.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.models;

import graphql.language.TypeName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldDetails {

    private String name;
    private TypeName type;

}
