/*
 * @author : Oguz Kahraman
 * @since : 20.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.hero.graphqldoc.annotations.GraphQLDocDetail;
import com.hero.graphqldoc.annotations.GraphQLType;
import com.hero.graphqldoc.annotations.MutationType;
import com.hero.graphqldoc.annotations.ParameterType;
import com.hero.graphqldoc.annotations.QueryType;
import com.hero.graphqldoc.annotations.Schema;
import com.hero.graphqldoc.annotations.SchemaType;
import com.hero.graphqldoc.enums.GraphType;
import com.hero.graphqldoc.models.FieldDetails;
import com.hero.graphqldoc.models.GraphQLClassFields;
import com.hero.graphqldoc.models.GraphQLField;
import com.hero.graphqldoc.models.GraphQLMethodObject;
import com.hero.graphqldoc.models.GraphQLObject;
import com.hero.graphqldoc.models.GraphQLTypeDetails;
import com.hero.graphqldoc.parser.QueryParser;
import com.hero.graphqldoc.properties.GraphQLDocProperties;
import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.leangen.geantyref.AnnotationFormatException;
import io.leangen.geantyref.TypeFactory;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.util.ReflectionUtilsPredicates.withAnnotation;

@Controller
public class GraphQLController {

    private static final String JAVA = "java.";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BLANKS = "        ";
    private static final String REQUEST_TEMPLATE = """
            %s {
              %s%s 
            %s
            }
            """;
    private final GraphQLDocProperties properties;
    private final Map<String, GraphQLObject> map = new HashMap<>();
    private final Map<String, GraphQLClassFields> objectTypeMap = new HashMap<>();
    private final Map<String, String> graphQLToJavaMap = new HashMap<>();
    private final Map<String, JSONObject> examples = new HashMap<>();
    private final Map<String, String> returnElements = new HashMap<>();
    private final Map<String, String> requestExamples = new HashMap<>();
    @Autowired
    private ApplicationContext applicationContext;

    public GraphQLController(GraphQLDocProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    private void init() throws IOException, AnnotationFormatException {
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        SchemaParser parser = new SchemaParser();
        Resource[] resources = applicationContext.getResources("classpath*:" + properties.getSchemaLocationPattern());
        for (Resource resource : resources) {
            typeRegistry.merge(parser.parse(resource.getInputStream()));
        }
        Reflections reflections = new Reflections(properties.getPackageName());
        getQueries(typeRegistry, reflections);
        getMutations(typeRegistry, reflections);
    }

    private void getQueries(TypeDefinitionRegistry typeRegistry, Reflections reflections)
            throws AnnotationFormatException {
        List<GraphQLTypeDetails> queries = QueryParser.getQueries(typeRegistry, GraphType.QUERY);
        addMethods(reflections, queries, QueryType.class, "query", typeRegistry);
    }

    private void getMutations(TypeDefinitionRegistry typeRegistry, Reflections reflections) throws AnnotationFormatException {
        List<GraphQLTypeDetails> queries = QueryParser.getQueries(typeRegistry, GraphType.MUTATION);
        addMethods(reflections, queries, MutationType.class, "mutation", typeRegistry);
    }

    private void addMethods(Reflections reflections, List<GraphQLTypeDetails> queries, Class<? extends Annotation> annotationClass,
                            String queryString, TypeDefinitionRegistry typeDefinitionRegistry) throws AnnotationFormatException {
        Map<String, GraphQLTypeDetails> queryNameList = queries.stream().collect(toMap(item -> item.getQlQueryType().getName(), i -> i));
        Set<Class<?>> queryClasses = reflections.getTypesAnnotatedWith(annotationClass);
        for (Class<?> clazz : queryClasses) {
            GraphQLType graphQLType = getGraphQlAnnotationType(annotationClass, clazz);
            GraphQLObject object = new GraphQLObject();
            object.setDescription(graphQLType.description());
            object.setKey(graphQLType.key());
            Set<Method> methods = getAllMethods(clazz, withAnnotation(GraphQLDocDetail.class));
            List<GraphQLMethodObject> objectDetails = new ArrayList<>();
            for (Method method : methods) {
                String name = checkMethodIsAvailable(queryNameList.keySet(), method.getName());
                if (name != null) {
                    GraphQLMethodObject methodObject = new GraphQLMethodObject();
                    GraphQLDocDetail graphQLDocDetail = method.getAnnotation(GraphQLDocDetail.class);
                    methodObject.setDescription(graphQLDocDetail.description());
                    methodObject.setName(name);
                    methodObject.setMethodName(method.getName());
                    methodObject.setOperation(graphQLDocDetail.operation());
                    objectDetails.add(methodObject);
                    createClassFields(method, methodObject, queryNameList, queryString, typeDefinitionRegistry);
                }
            }
            object.setObjects(objectDetails);
            map.put(graphQLType.key().replace(" ", "_"), object);
        }
    }

    private GraphQLType getGraphQlAnnotationType(Class<? extends Annotation> annotationClass, Class<?> clazz) throws AnnotationFormatException {
        if (annotationClass.isAssignableFrom(QueryType.class)) {
            QueryType queryType = clazz.getAnnotation(QueryType.class);
            Map<String, Object> annotationParameters = getMap(queryType.key(), queryType.description(), "Query");
            return TypeFactory.annotation(GraphQLType.class, annotationParameters);
        } else if (annotationClass.isAssignableFrom(MutationType.class)) {
            MutationType queryType = clazz.getAnnotation(MutationType.class);
            Map<String, Object> annotationParameters = getMap(queryType.key(), queryType.description(), "Mutation");
            return TypeFactory.annotation(GraphQLType.class, annotationParameters);
        }
        return null;
    }

    @NotNull
    private Map<String, Object> getMap(String queryType, String queryType1, String type) {
        Map<String, Object> annotationParameters = new HashMap<>();
        annotationParameters.put("key", queryType);
        annotationParameters.put("description", queryType1);
        annotationParameters.put("type", type);
        return annotationParameters;
    }

    private void createClassFields(Method method, GraphQLMethodObject methodObject, Map<String, GraphQLTypeDetails> queryNameList, String queryString,
                                   TypeDefinitionRegistry typeRegistry) {
        Class<?> returnClas;
        GraphQLTypeDetails typeDetails = queryNameList.get(methodObject.getName());
        if (!objectTypeMap.containsKey(method.getReturnType().getSimpleName())) {
            StringBuilder builder = new StringBuilder(BLANKS);
            JSONObject obj = new JSONObject();
            if (Collection.class.isAssignableFrom(method.getReturnType())) {
                returnClas = (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
            } else {
                returnClas = method.getReturnType();
            }
            createClassDefinitions(typeDetails, returnClas, methodObject, null, typeRegistry, obj, builder);
        } else {
            returnClas = method.getReturnType();
        }
        methodObject.setOutputName(returnClas.getSimpleName());
        methodObject.setOutputJson(GSON.toJson(JsonParser.parseString(examples.get(returnClas.getSimpleName()).toString())));
        StringBuilder stringBuilder = new StringBuilder();
        generateMethods(method, methodObject, typeRegistry, typeDetails, stringBuilder);

        Parser parser = new Parser();
        try {
            String returnElement = "";
            if (!checksIsJava(returnClas)) {
                returnElement = "{" + returnElements.get(returnClas.getSimpleName()) + "}";
            }
            Document doc = parser.parseDocument(String.format(REQUEST_TEMPLATE, queryString, methodObject.getName(), stringBuilder, returnElement));
            methodObject.setInputJson(AstPrinter.printAst(doc));

        } catch (Exception e) {
            System.out.println(String.format("%s {\n  %s%s {\n%s}\n}\n", queryString, methodObject.getName(), stringBuilder,
                    this.returnElements.get(returnClas.getSimpleName())));
            e.printStackTrace();
        }
    }

    private void generateMethods(Method method, GraphQLMethodObject methodObject, TypeDefinitionRegistry typeRegistry, GraphQLTypeDetails typeDetails, StringBuilder stringBuilder) {
        if (method.getParameters() != null && method.getParameters().length > 0) {
            stringBuilder.append("(");
            for (Parameter parameter : method.getParameters()) {
                if (!objectTypeMap.containsKey(parameter.getType().getSimpleName())) {
                    Class<?> returnClass = parameter.getType();
                    JSONObject obj = new JSONObject();
                    createClassDefinitions(typeDetails, returnClass, methodObject, parameter.getName(), typeRegistry, obj, new StringBuilder(BLANKS));
                }
                addParamExample(stringBuilder, parameter);
                stringBuilder.append(", ");
            }
            stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
            stringBuilder.append(")");
        }
    }

    private void addParamExample(StringBuilder stringBuilder, Parameter parameter) {
        if (parameter.getType().getName().startsWith(JAVA)) {
            generateParamRequest(parameter, stringBuilder);
        } else {

            if (checksIsJava(parameter.getType())) {
                generateParamRequest(parameter, stringBuilder);
            } else {
                if (!requestExamples.containsKey(parameter.getType().getSimpleName())) {
                    generateExampleRequest(parameter);
                }
                stringBuilder.append(requestExamples.get(parameter.getType().getSimpleName()));
            }
        }
    }

    private void generateParamRequest(Parameter param, StringBuilder stringBuilder) {
        stringBuilder.append(param.getName()).append(":").append(" ");
        ParameterType schemaType = param.getAnnotation(ParameterType.class);
        if (schemaType != null) {
            stringBuilder.append(getFieldExample(param.getType(), schemaType.example()));
        } else {
            stringBuilder.append(getFieldExample(param.getType(), null));
        }
    }

    private void generateExampleRequest(Parameter param) {
        StringBuilder builder = new StringBuilder();
        builder.append(param.getName()).append(": ").append("{");
        for (Field field : param.getType().getDeclaredFields()) {
            addFieldRequestExample(builder, field);
        }
        builder.append("}");
        requestExamples.put(param.getType().getSimpleName(), builder.toString());
    }

    private void addFieldRequestExample(StringBuilder builder, Field field) {
        if (!checksIsJava(field.getType())) {
            builder.append(field.getName()).append(": {");
            for (Field field1 : field.getType().getDeclaredFields()) {
                addFieldRequestExample(builder, field1);
                builder.append(",");
            }
            builder.delete(builder.length() - 1, builder.length());
            builder.append("},");
        } else {
            builder.append(field.getName()).append(": ");
            checkCollectionAndCreate(builder, field);
            builder.append(",");
        }
    }

    private void checkCollectionAndCreate(StringBuilder builder, Field field) {
        if (Collection.class.isAssignableFrom(field.getType())) {
            builder.append("[ ");
            Class<?> type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            if (!checksIsJava(type)) {
                builder.append("{ ");
            }
            addExample(builder, field);
            if (!checksIsJava(type)) {
                builder.append(" }");
            }
            builder.append(" ]");
        } else {
            addExample(builder, field);
        }
    }

    private void addExample(StringBuilder builder, Field field) {
        SchemaType schemaType = field.getAnnotation(SchemaType.class);
        Class<?> type;
        if (Collection.class.isAssignableFrom(field.getType())) {
            type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
        } else {
            type = field.getType();
        }
        if (checksIsJava(type)) {
            if (schemaType != null) {
                builder.append(getFieldExample(type, schemaType.example()));
            } else {
                builder.append(getFieldExample(type, null));
            }
        } else {
            for (Field field1 : type.getDeclaredFields()) {
                addFieldRequestExample(builder, field1);
            }
        }

    }

    private boolean checksIsJava(Class<?> param) {
        return param.isPrimitive() || param.getPackageName().startsWith(JAVA) || Enum.class.isAssignableFrom(param);
    }

    @SneakyThrows
    private void createClassDefinitions(GraphQLTypeDetails typeDetails, Class<?> returnClass, GraphQLMethodObject methodObject,
                                        String paramName, TypeDefinitionRegistry typeRegistry, JSONObject obj, StringBuilder returnElement) {
        GraphQLClassFields classFields = new GraphQLClassFields();
        List<GraphQLField> fields = new ArrayList<>();
        addNamesToMap(returnClass, paramName, classFields, typeDetails);
        List<FieldDetails> fieldDefinitions = QueryParser.example(typeRegistry, graphQLToJavaMap.get(returnClass.getSimpleName()));
        for (FieldDetails fieldDefinition : fieldDefinitions) {
            Field field = returnClass.getDeclaredField(fieldDefinition.getName());
            String name = field.getName();
            GraphQLField fieldObject = new GraphQLField();
            fieldObject.setName(name);
            fieldObject.setJavaType(field.getType().isPrimitive() || field.getType().getPackageName().startsWith(JAVA));
            fieldObject.setListType(Collection.class.isAssignableFrom(field.getType()));
            Class<?> exTye = checkListType(field, fieldObject, returnElement);

            setExamples(obj, field, name, fieldObject);
            if (Boolean.FALSE.equals(fieldObject.getJavaType())) {
                if (Boolean.TRUE.equals(fieldObject.getListType())) {
                    returnElement.append("{\n" + BLANKS);
                }
                GraphQLMethodObject methodObject2 = new GraphQLMethodObject();
                createClassDefinitions(typeDetails, exTye, methodObject2, fieldDefinition.getType().getName(), typeRegistry, new JSONObject(), returnElement);
                if (Boolean.TRUE.equals(fieldObject.getListType())) {
                    returnElement.append("\n}");
                }
                obj.put(name, examples.get(exTye.getSimpleName()));
            }
            fieldObject.setType(fieldDefinition.getType().getName());
            fields.add(fieldObject);
        }
        classFields.setFields(fields);
        classFields.setName(returnClass.getSimpleName());
        if (returnClass.getAnnotation(Schema.class) != null) {
            Schema schemaType = returnClass.getAnnotation(Schema.class);
            classFields.setDescription(schemaType.description());
        }
        if (paramName == null) {
            JSONObject objData = new JSONObject();
            JSONObject objField = new JSONObject();
            objData.put("data", objField);
            if (checksIsJava(returnClass)) {
                objField.put(methodObject.getName(), getFieldExample(returnClass, null));
                examples.put(returnClass.getSimpleName(), objData);
            } else {
                objField.put(methodObject.getName(), obj);
                examples.put(returnClass.getSimpleName(), objData);
            }
            if (!checksIsJava(returnClass)) {
                returnElements.put(returnClass.getSimpleName(), returnElement.toString());
            }
        } else if (methodObject.getName() == null) {
            examples.put(returnClass.getSimpleName(), obj);
        }
    }

    private void setExamples(JSONObject obj, Field field, String name, GraphQLField fieldObject) {
        SchemaType schemaType = field.getAnnotation(SchemaType.class);
        if (schemaType != null) {
            fieldObject.setDescription(schemaType.description());
            fieldObject.setExample(schemaType.example());
            fieldObject.setRequired(schemaType.required());
            obj.put(name, getObjectExample(field.getType(), schemaType.example()));
        } else {
            fieldObject.setRequired(false);
            obj.put(name, getObjectExample(field.getType(), ""));
        }
    }

    private Class<?> checkListType(Field field, GraphQLField fieldObject, StringBuilder returnElement) {
        if (Boolean.TRUE.equals(fieldObject.getListType())) {
            returnElement.append(field.getName()).append(BLANKS);
            Class<?> type = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            fieldObject.setJavaType(type.isPrimitive() || type.getPackageName().startsWith(JAVA));
            return type;
        } else {
            returnElement.append(field.getName()).append("\n" + BLANKS);
            fieldObject.setJavaType(field.getType().isPrimitive() || field.getType().getPackageName().startsWith(JAVA));
        }
        return field.getType();
    }

    private void addNamesToMap(Class<?> returnClass, String paramName, GraphQLClassFields classFields, GraphQLTypeDetails typeDetails) {
        if (!checksIsJava(returnClass)) {
            objectTypeMap.putIfAbsent(returnClass.getSimpleName(), classFields);
        }
        if (paramName != null) {
            if (!checksIsJava(returnClass)) {
                if (typeDetails.getInputs().containsKey(paramName)) {
                    graphQLToJavaMap.putIfAbsent(returnClass.getSimpleName(), typeDetails.getInputs().get(paramName).getOutputName());
                } else {
                    graphQLToJavaMap.putIfAbsent(returnClass.getSimpleName(), paramName);
                }
            }
        } else {
            graphQLToJavaMap.putIfAbsent(returnClass.getSimpleName(), typeDetails.getQlQueryType().getOutputName());
        }
    }

    private String checkMethodIsAvailable(Set<String> queries, String methodName) {
        if (queries.contains(methodName)) {
            return methodName;
        } else if (methodName.startsWith("get")) {
            String val1 = methodName.replace("get", "");
            char[] c = val1.toCharArray();
            c[0] = Character.toLowerCase(c[0]);
            String string = new String(c);
            return queries.contains(string) ? string : null;
        } else {
            char[] c = methodName.toCharArray();
            c[0] = Character.toUpperCase(c[0]);
            String string = new String(c);
            return queries.contains("get" + string) ? "get" + string : null;
        }
    }

    private Object getObjectExample(Class<?> field, String example) {
        Object example1 = getAndReturnExample(field, example);
        if (example1 != null) return example1;
        return example != null ? example : "";
    }

    private Object getFieldExample(Class<?> field, String example) {
        Object example1 = getAndReturnExample(field, example);
        if (example1 != null) return example1;
        return example != null ? getStringQuotas(example) : "\"\"";
    }

    @Nullable
    private Object getAndReturnExample(Class<?> field, String example) {
        if (field.equals(boolean.class) || field.equals(Boolean.class)) {
            return Boolean.valueOf(example != null ? example : "true");
        } else if (Number.class.isAssignableFrom(field) || field.equals(int.class) || field.equals(double.class) ||
                field.equals(long.class) || field.equals(float.class) || field.equals(short.class)) {
            return example != null ? example : 1;
        } else if (Enum.class.isAssignableFrom(field)) {
            return example != null ? example : field.getEnumConstants()[0].toString();
        }
        return null;
    }

    private String getStringQuotas(String str) {
        return "\"" + str + "\"";
    }

    @GetMapping(value = "/document")
    public String getInfo(Model model) {
        setSocialLinks(model);
        model.addAttribute("title", properties.getTitle());
        model.addAttribute("supportLink", properties.getSupportLink());
        model.addAttribute("docdescription", properties.getDescription());
        model.addAttribute("details", map);
        model.addAttribute("objectTypes", objectTypeMap);
        model.addAttribute("graphQLToJavaMap", graphQLToJavaMap);
        return "index.html";
    }

    private void setSocialLinks(Model model) {
        model.addAttribute("instagramLink", properties.getInstagramLink());
        model.addAttribute("linkedinLink", properties.getLinkedinLink());
        model.addAttribute("twitterLink", properties.getTwitterLink());
        model.addAttribute("url", properties.getUrl());
        model.addAttribute("logoUrl", properties.getLogoUrl());
        model.addAttribute("appVersion", properties.getAppVersion());
    }

}
