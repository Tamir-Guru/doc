/*
 * @author : Oguz Kahraman
 * @since : 20.02.2022
 *
 * Copyright - graphqldoc
 **/
package com.hero.graphqldoc.core;

import com.hero.graphqldoc.controller.GraphQLController;
import com.hero.graphqldoc.properties.GraphQLDocProperties;
import graphql.kickstart.autoconfigure.tools.GraphQLJavaToolsAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;

@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(DispatcherServlet.class)
@AutoConfigureAfter({WebMvcAutoConfiguration.class, WebFluxAutoConfiguration.class, GraphQLJavaToolsAutoConfiguration.class})
@EnableConfigurationProperties(GraphQLDocProperties.class)
@AutoConfigureOrder(Integer.MAX_VALUE)
public class DocAutoConfig {

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    @ConditionalOnProperty(value = "graphql.doc.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    GraphQLDocWebMvcConfig swaggerWebMvcConfigurer() {
        return new GraphQLDocWebMvcConfig();
    }

    @Bean
    GraphQLController graphqlDocController(GraphQLDocProperties properties) {
        return new GraphQLController(properties);
    }

    @Bean
    @ConditionalOnMissingBean(TemplateEngine.class)
    public TemplateEngine templateEngine() {
        return new SpringTemplateEngine();
    }

    @ConditionalOnClass(TemplateEngine.class)
    @Bean
    public void templateResolverTemplatesPages() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/pages/");
        resolver.setTemplateMode("HTML");
        resolver.setSuffix(".html");
        resolver.setCacheable(true);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine.addTemplateResolver(resolver);
    }

}
