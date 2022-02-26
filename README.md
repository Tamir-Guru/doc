### GraphQL Documentation support for [graphql-spring-boot](https://github.com/graphql-java-kickstart/graphql-spring-boot)

---

### Quick Start

#### Adding dependency

You should add following dependency to your `pom.xml`:

    <dependency>
            <groupId>com.hero</groupId>
            <artifactId>graphqldoc</artifactId>
            <version>0.1.0</version>
    </dependency>

### Enable Documentation

You should add following property to your `application.properties`:

    graphql.documentation.enabled=true

#### Available Properties

    graphql.doc.schema-location-pattern:.graphqls file path like **/*.graphqls
    graphql.doc.endpoint:url endpoint like /document (Don't forget to att autorized urls)
    graphql.doc.title=Title of document
    graphql.doc.description=Document description
    graphql.doc.package-name=Package name for resolvers
    graphql.doc.support-link=Support link like maito:x@x.com
    graphql.doc.instagram-link=Instagram page link
    graphql.doc.linkedin-link=LinkedIn page link
    graphql.doc.twitter-link=Twitter page link
    graphql.doc.url=Logo click link 
    graphql.doc.logo-url=logo url
    graphql.doc.app-version=Document version

## Usage

### Resolvers

You should use one of them following annotations for Resolver class:

- @QueryType
- @MutationType
- @SubscriptionType

All have `key` and `description` properties.

- **Description:** The description of resolver
- **Key:** Title key for resolver

    @QueryType(description = "Example Query Operations", key = "Example Query")
    public class ExampleResolver implements GraphQLQueryResolver {
    }

### Resolver Methods

You should use `@GraphQLDocDetail` annotation for each method of resolver.

    @GraphQLDocDetail(operation = "Get examples of example", description = "This example returns example")
    public List<DistrictResponse> getDistricts(String param1) {
        return exampleService.getExample(param1);
    }

This annotation has `operation` and `description` properties.

- **Description:** The description of method
- **Operation:** Title of method

### Method Parameter

You should use `@ParameterType` annotation for each method of resolver.

    @GraphQLDocDetail(operation = "Get examples of example", description = "This example returns example")
    public List<DistrictResponse> getDistricts(@ParameterType(example = "1") String param1) {
        return exampleService.getExample();
    }

This annotation has `example` and `description` property.

- **Example:** Example value for parameter

### Objects

You should use `@Schema` for classes and  `@SchemaType` for fields.

    @Schema(description = "Merchant Update Request")
    public class ExampleRequest {

        @SchemaType(example = "1", description = "Id of class")
        private Long id;
    
    }

`@Schema` has `description` property.

`@SchemaType` has `example`, `description` and `required` properties.

- **Example:** Example value for field
- **Description:** Description of field
- **Required:** true if parameter is required

