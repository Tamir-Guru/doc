/*
 * @author : Oguz Kahraman
 * @since : 20.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.parser;

import com.hero.graphqldoc.enums.ElementType;
import com.hero.graphqldoc.enums.GraphType;
import com.hero.graphqldoc.models.FieldDetails;
import com.hero.graphqldoc.models.GraphQLQueryType;
import com.hero.graphqldoc.models.GraphQLTypeDetails;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.TypeDefinitionRegistry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QueryParser {

    @SneakyThrows
    public static List<GraphQLTypeDetails> getQueries(TypeDefinitionRegistry typeRegistry, GraphType graphType) {
        List<GraphQLTypeDetails> queryList = new ArrayList<>();
        List<ObjectTypeExtensionDefinition> objects = typeRegistry.objectTypeExtensions().get(graphType.getType());
        if (objects != null) {
            for (ObjectTypeExtensionDefinition extensionDefinition : objects) {
                for (FieldDefinition fieldDefinition : extensionDefinition.getFieldDefinitions()) {
                    GraphQLTypeDetails graphQLTypeDetails = new GraphQLTypeDetails();
                    GraphQLQueryType queryDetails = new GraphQLQueryType();
                    graphQLTypeDetails.setQlQueryType(queryDetails);
                    queryDetails.setName(fieldDefinition.getName());
                    getType(fieldDefinition.getType(), queryDetails);
                    for (InputValueDefinition inputValueDefinition : fieldDefinition.getInputValueDefinitions()) {
                        GraphQLQueryType queryDetails1 = new GraphQLQueryType();
                        queryDetails1.setName(inputValueDefinition.getName());
                        getType(inputValueDefinition.getType(), queryDetails1);
                        graphQLTypeDetails.getInputs().putIfAbsent(inputValueDefinition.getName(), queryDetails1);
                    }
                    queryList.add(graphQLTypeDetails);
                }
            }
        }
        return queryList;
    }

    public static List<FieldDetails> example(TypeDefinitionRegistry typeRegistry, String className) {
        Optional<TypeDefinition> objects = typeRegistry.getType(className);
        if (objects.isPresent()) {
            if (objects.get() instanceof ObjectTypeDefinition objectTypeDefinition) {
                return objectTypeDefinition.getFieldDefinitions().stream().map(item ->
                        new FieldDetails(item.getName(), getTypeName(item.getType()))).toList();
            } else if (objects.get() instanceof InputObjectTypeDefinition typeDefinition) {
                return typeDefinition.getInputValueDefinitions().stream().map(item ->
                        new FieldDetails(item.getName(), getTypeName(item.getType()))).toList();
            }
        }
        return new ArrayList<>();

    }

    private static void getType(Type<?> type, GraphQLQueryType queryDetails) {
        if (type instanceof NonNullType nnt) {
            queryDetails.setNotNull(true);
            getType(nnt.getType(), queryDetails);
        } else if (type instanceof ListType lt) {
            queryDetails.setElementType(ElementType.LIST);
            getType(lt.getType(), queryDetails);
        } else if (type instanceof TypeName tn) {
            queryDetails.setOutputName(tn.getName());
        }
    }

    private static TypeName getTypeName(Type<?> type) {
        if (type instanceof NonNullType nnt) {
            return getTypeName(nnt.getType());
        } else if (type instanceof ListType lt) {
            return getTypeName(lt.getType());
        } else if (type instanceof TypeName tn) {
            return tn;
        }
        return null;
    }

}
