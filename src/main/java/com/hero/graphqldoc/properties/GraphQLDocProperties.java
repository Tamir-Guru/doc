/*
 * @author : Oguz Kahraman
 * @since : 20.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "graphql.doc")
public class GraphQLDocProperties {

    private String schemaLocationPattern = "**/*.graphqls";
    private Boolean enabled = false;
    private String packageName = "";
    private String title = "GraphQL Doc";
    private String description = "Welcome Documentation";
    private String supportLink;
    private String twitterLink;
    private String linkedinLink;
    private String instagramLink;
    private String url = "index.html";
    private String logoUrl = "assets/images/logo.png";
    private String appVersion = "v1.0.0";

}
