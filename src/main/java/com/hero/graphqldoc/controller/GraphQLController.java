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
import com.hero.graphqldoc.annotations.SubscriptionType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.StringLiteral;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

@Controller
public class GraphQLController {

    private static final String SECURE_TEXT = "Only for authenticated users with roles : %s";
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

    /**
     * This method is used to init the GraphQL schema
     */
    @PostConstruct
    private void init() throws IOException, AnnotationFormatException {
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        SchemaParser parser = new SchemaParser();
        Resource[] resources = applicationContext.getResources("classpath*:" + properties.getSchemaLocationPattern());
        for (Resource resource : resources) {
            typeRegistry.merge(parser.parse(resource.getInputStream()));
        }
        getQueries(typeRegistry);
        getMutations(typeRegistry);
        getSubscriptions(typeRegistry);
    }

    /**
     * Creates the GraphQL schema for the queries
     *
     * @param typeRegistry schema details
     */
    private void getQueries(TypeDefinitionRegistry typeRegistry)
            throws AnnotationFormatException {
        List<GraphQLTypeDetails> queries = QueryParser.getQueries(typeRegistry, GraphType.QUERY);
        addMethods(queries, QueryType.class, "query", typeRegistry);
    }

    /**
     * Creates the GraphQL schema for the mutations
     *
     * @param typeRegistry schema details
     */
    private void getMutations(TypeDefinitionRegistry typeRegistry) throws AnnotationFormatException {
        List<GraphQLTypeDetails> queries = QueryParser.getQueries(typeRegistry, GraphType.MUTATION);
        addMethods(queries, MutationType.class, "mutation", typeRegistry);
    }

    /**
     * Creates the GraphQL schema for the subscriptions
     *
     * @param typeRegistry schema details
     */
    private void getSubscriptions(TypeDefinitionRegistry typeRegistry) throws AnnotationFormatException {
        List<GraphQLTypeDetails> queries = QueryParser.getQueries(typeRegistry, GraphType.SUBSCRIPTION);
        addMethods(queries, SubscriptionType.class, "subscription", typeRegistry);
    }

    /**
     * This method is used to get the classes with GraphQL annotations
     *
     * @param annotation like @GraphQLQuery, @GraphQLMutation, @GraphQLSubscription
     * @return classes annotated with the given annotation
     */
    @SneakyThrows
    public Set<Class<?>> getAnnotatedClasses(Class<? extends Annotation> annotation) {
        Set<Class<?>> classes = new HashSet<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        var beans = scanner.findCandidateComponents(properties.getPackageName());
        for (var bean : beans) {
            var className = bean.getBeanClassName();
            classes.add(Class.forName(className));
        }
        return classes;
    }

    /**
     * This method is used to get the class methods with GraphQL annotations
     *
     * @param annotation with @GraphQLDocDetail
     * @return methods annotated with the given annotation
     */
    public Set<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotation) {
        Set<Method> classes = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                classes.add(method);
            }
        }
        return classes;
    }

    /**
     * This method creates requests, responses for given GraphQL queries.
     * It also creates the GraphQL schema for the queries
     */
    private void addMethods(List<GraphQLTypeDetails> queries, Class<? extends Annotation> annotationClass,
                            String queryString, TypeDefinitionRegistry typeDefinitionRegistry) throws AnnotationFormatException {
        Map<String, GraphQLTypeDetails> queryNameList = queries.stream().collect(toMap(item -> item.getQlQueryType().getName(), i -> i));
        Set<Class<?>> queryClasses = getAnnotatedClasses(annotationClass);
        for (Class<?> clazz : queryClasses) {
            GraphQLType graphQLType = getGraphQlAnnotationType(annotationClass, clazz);
            GraphQLObject object = new GraphQLObject();
            object.setDescription(graphQLType.description());
            object.setKey(graphQLType.key());
            Set<Method> methods = getAnnotatedMethods(clazz, GraphQLDocDetail.class);
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
                    methodObject.setAuthString(createAuthString(method));
                    objectDetails.add(methodObject);
                    createClassFields(method, methodObject, queryNameList, queryString, typeDefinitionRegistry);
                }
            }
            object.setObjects(objectDetails);
            map.put(graphQLType.key().replace(" ", "_"), object);
        }
    }

    private String createAuthString(Method method) {
        ExpressionParser parser = new SpelExpressionParser();
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        if (authorize == null) {
            return null;
        }
        Expression expression = parser.parseExpression(authorize.value());
        MethodReference reference = ((MethodReference) ((SpelExpression) expression).getAST());
        switch (reference.getName()) {
            case "isAnonymous":
                return "Only anonymous users can access this resource";
            case "hasRole", "hasAnyRole":
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < reference.getChildCount(); i++) {
                    StringLiteral node = (StringLiteral) reference.getChild(i);
                    if (node.getOriginalValue() != null) {
                        builder.append(node.getOriginalValue().replaceAll("ROLE_", "")).append(", ");
                    }
                }
                return String.format(SECURE_TEXT, builder.substring(0, builder.toString().length() - 2));
            case "isAuthenticated":
                return String.format(SECURE_TEXT, "Any Role");
            default:
                return null;
        }
    }

    /**
     * Map sub annotation fields to Main annotation (@GraphQLType) fields
     * for generic use
     */
    private GraphQLType getGraphQlAnnotationType(Class<? extends Annotation> annotationClass, Class<?> clazz) throws AnnotationFormatException {
        if (annotationClass.isAssignableFrom(QueryType.class)) {
            QueryType queryType = clazz.getAnnotation(QueryType.class);
            Map<String, Object> annotationParameters = getMap(queryType.key(), queryType.description(), "Query");
            return TypeFactory.annotation(GraphQLType.class, annotationParameters);
        } else if (annotationClass.isAssignableFrom(MutationType.class)) {
            MutationType queryType = clazz.getAnnotation(MutationType.class);
            Map<String, Object> annotationParameters = getMap(queryType.key(), queryType.description(), "Mutation");
            return TypeFactory.annotation(GraphQLType.class, annotationParameters);
        } else if (annotationClass.isAssignableFrom(SubscriptionType.class)) {
            SubscriptionType queryType = clazz.getAnnotation(SubscriptionType.class);
            Map<String, Object> annotationParameters = getMap(queryType.key(), queryType.description(), "Subscription");
            return TypeFactory.annotation(GraphQLType.class, annotationParameters);
        }
        return null;
    }

    /**
     * Get annotation field maps
     *
     * @return hashmap of annotation fields
     */
    @NotNull
    private Map<String, Object> getMap(String queryType, String queryType1, String type) {
        Map<String, Object> annotationParameters = new HashMap<>();
        annotationParameters.put("key", queryType);
        annotationParameters.put("description", queryType1);
        annotationParameters.put("type", type);
        return annotationParameters;
    }

    /**
     * Generates graphQL query for given class and method
     * This method prepare all required fields for HTML
     */
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
        String returnElement = "";
        if (!checksIsJava(returnClas)) {
            returnElement = "{" + returnElements.get(returnClas.getSimpleName()) + "}";
        }
        Document doc = parser.parseDocument(String.format(REQUEST_TEMPLATE, queryString, methodObject.getName(), stringBuilder, returnElement));
        methodObject.setInputJson(AstPrinter.printAst(doc));

    }

    /**
     * Prepares method params for graphQL schema
     */
    private void generateMethods(Method method, GraphQLMethodObject methodObject, TypeDefinitionRegistry typeRegistry,
                                 GraphQLTypeDetails typeDetails, StringBuilder stringBuilder) {
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

    /**
     * Adds param examples for graphQL schema
     */
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

    /**
     * Generates example for Java classes like String and Integer
     */
    private void generateParamRequest(Parameter param, StringBuilder stringBuilder) {
        stringBuilder.append(param.getName()).append(":").append(" ");
        ParameterType schemaType = param.getAnnotation(ParameterType.class);
        if (schemaType != null) {
            stringBuilder.append(getFieldExample(param.getType(), schemaType.example()));
        } else {
            stringBuilder.append(getFieldExample(param.getType(), null));
        }
    }

    /**
     * Generates example for method parameters
     */
    private void generateExampleRequest(Parameter param) {
        StringBuilder builder = new StringBuilder();
        builder.append(param.getName()).append(": ").append("{");
        Class<?> current = param.getType();
        do {
            for (Field field : current.getDeclaredFields()) {
                addFieldRequestExample(builder, field);
            }
        } while ((current = current.getSuperclass()) != null);
        builder.append("}");
        requestExamples.put(param.getType().getSimpleName(), builder.toString());
    }

    /**
     * Adds example value for given fields for request object
     */
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

    /**
     * Checks if element is collection and adds example value
     * If yes, gets main type of collection
     * If no adds example value
     */
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

    /**
     * Adds example value for given field for params
     */
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

    /**
     * Checks if given type is java type
     *
     * @param param class type
     * @return true if java type
     */
    private boolean checksIsJava(Class<?> param) {
        return param.isPrimitive() || param.getPackageName().startsWith(JAVA) || Enum.class.isAssignableFrom(param);
    }

    /**
     * Creates all details of given class ttype
     * Creates example request
     * Creates return value
     * Creates request params
     */
    @SneakyThrows
    private void createClassDefinitions(GraphQLTypeDetails typeDetails, Class<?> returnClass, GraphQLMethodObject methodObject,
                                        String paramName, TypeDefinitionRegistry typeRegistry, JSONObject obj, StringBuilder returnElement) {
        GraphQLClassFields classFields = new GraphQLClassFields();
        List<GraphQLField> fields = new ArrayList<>();
        addNamesToMap(returnClass, paramName, classFields, typeDetails);
        List<FieldDetails> fieldDefinitions = QueryParser.example(typeRegistry, graphQLToJavaMap.get(returnClass.getSimpleName()));
        for (FieldDetails fieldDefinition : fieldDefinitions) {
            Field field = getField(returnClass, fieldDefinition.getName());
            String name = field.getName();
            GraphQLField fieldObject = new GraphQLField();
            fieldObject.setName(name);
            fieldObject.setJavaType(field.getType().isPrimitive() || field.getType().getPackageName().startsWith(JAVA));
            fieldObject.setListType(Collection.class.isAssignableFrom(field.getType()));
            Class<?> exTye = checkListType(field, fieldObject, returnElement);

            setExamples(obj, field, name, fieldObject);
            if (Boolean.FALSE.equals(fieldObject.getJavaType())) {
                returnElement.append("{\n" + BLANKS);
                GraphQLMethodObject methodObject2 = new GraphQLMethodObject();
                createClassDefinitions(typeDetails, exTye, methodObject2, fieldDefinition.getType().getName(), typeRegistry, new JSONObject(), returnElement);
                returnElement.append("\n}");
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
        createJSONs(returnClass, methodObject, paramName, obj, returnElement);
    }

    /**
     * get fields from given class
     * Its also gets superclass fields
     */
    @SuppressWarnings("java:S108")
    private Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        do {
            try {
                return current.getDeclaredField(fieldName);
            } catch (Exception ignored) {
            }
        } while ((current = current.getSuperclass()) != null);
        throw new NoSuchFieldException(fieldName);
    }

    /**
     * Create example response JSON
     */
    private void createJSONs(Class<?> returnClass, GraphQLMethodObject methodObject, String paramName, JSONObject obj, StringBuilder returnElement) {
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

    /**
     * Checks and add example to response json
     */
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

    /**
     * Checks if field is list
     * If true return list element type
     * If false return element type
     *
     * @return element type
     */
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

    /**
     * Creates map for GraphQL definition to Java object mapping
     */
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

    /**
     * Returns schema method name for java method name
     */
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

    /**
     * Returns example value for request object
     *
     * @return example value
     */
    private Object getObjectExample(Class<?> field, String example) {
        Object example1 = getAndReturnExample(field, example);
        if (example1 != null) return example1;
        return example != null ? example : "";
    }

    /**
     * Returns example value for response object
     *
     * @return example value
     */
    private Object getFieldExample(Class<?> field, String example) {
        Object example1 = getAndReturnExample(field, example);
        if (example1 != null) return example1;
        return example != null ? getStringQuotas(example) : "\"\"";
    }

    /**
     * Checks element type and returns example value
     * If not exists example, return default value
     *
     * @return example value
     */
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

    /**
     * Creates example string value with addtional quotes
     *
     * @param str original string
     * @return string with quotes
     */
    private String getStringQuotas(String str) {
        return "\"" + str + "\"";
    }

    /**
     * Add required objects to thymeleaf model
     *
     * @param model for thymeleaf template
     * @return thymeleaf path
     */
    @GetMapping(value = "${graphql.doc.endpoint:/document}")
    public String getInfo(Model model) {
        setSocialLinks(model);
        model.addAttribute("title", properties.getTitle());
        model.addAttribute("supportLink", properties.getSupportLink());
        model.addAttribute("docdescription", properties.getDocDescription());
        model.addAttribute("details", map);
        model.addAttribute("objectTypes", objectTypeMap);
        model.addAttribute("graphQLToJavaMap", graphQLToJavaMap);
        return "index.html";
    }

    /**
     * Add social links to thymeleaf model
     *
     * @param model for thymeleaf template
     */
    private void setSocialLinks(Model model) {
        model.addAttribute("instagramLink", properties.getInstagramLink());
        model.addAttribute("linkedinLink", properties.getLinkedinLink());
        model.addAttribute("twitterLink", properties.getTwitterLink());
        model.addAttribute("siteUrl", properties.getSiteUrl());
        model.addAttribute("logoUrl", properties.getLogoUrl());
        model.addAttribute("appVersion", properties.getAppVersion());
    }

}
