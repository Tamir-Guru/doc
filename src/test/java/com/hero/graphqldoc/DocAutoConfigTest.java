///*
// * @author : Oguz Kahraman
// * @since : 20.02.2022
// *
// * Copyright - graphqldoc
// **/
//package com.hero.graphqldoc;
//
//import com.hero.graphqldoc.core.DocAutoConfig;
//import com.hero.graphqldoc.controller.GraphQLController;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//
//class DocAutoConfigTest {
//
//    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
//            .withUserConfiguration(TestApp.class);
//
//    @Test
//    void cache_configuration_loaded_when_not_disabled_explicitly() {
//        this.contextRunner.withUserConfiguration(DocAutoConfig.class).run((context) -> {
//            assertThat(context).hasSingleBean(GraphQLController.class);
//        });
//    }
//
//    @EnableAutoConfiguration
//    static class TestApp {
//
//    }
//
//}