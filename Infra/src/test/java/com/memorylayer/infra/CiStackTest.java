package com.memorylayer.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

class CiStackTest {

    private static Template synth() {
        App app = new App();
        Environment env = Environment.builder().account("123456789012").region("ap-south-1").build();
        CiStack stack = new CiStack(app, "TestCiStack", StackProps.builder().env(env).build(), "octo@1/repo@2");
        return Template.fromStack(stack);
    }

    @Test
    void oidcProviderTrustsOnlyStsAudience() {
        synth().hasResourceProperties("AWS::IAM::OIDCProvider", Map.of(
                "Url", "https://token.actions.githubusercontent.com",
                "ClientIdList", List.of("sts.amazonaws.com")));
    }

    @Test
    void trustIsPinnedToRepoEnvironmentAndMainWithNoWildcards() {
        Map<String, Object> condition = Map.of("StringEquals", Map.of(
                "token.actions.githubusercontent.com:aud", "sts.amazonaws.com",
                "token.actions.githubusercontent.com:sub", List.of(
                        "repo:octo@1/repo@2:environment:production",
                        "repo:octo@1/repo@2:ref:refs/heads/main")));
        Map<String, Object> statement = Map.of(
                "Action", "sts:AssumeRoleWithWebIdentity",
                "Condition", condition);
        Map<String, Object> trust = Map.of("Statement", List.of(Match.objectLike(statement)));
        synth().hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
                "MaxSessionDuration", 3600,
                "AssumeRolePolicyDocument", Match.objectLike(trust))));
    }

    @Test
    void roleCanOnlyAssumeBootstrapRolesAndDescribeDeployableStacks() {
        Template template = synth();
        String assume = ":iam::123456789012:role/cdk-hnb659fds-%s-role-123456789012-ap-south-1";
        List<String> stacks = List.of("MemoryLayerAuthStack", "MemoryLayerDataStack", "MemoryLayerIngestionStack",
                "MemoryLayerApiStack", "MemoryLayerFrontendStack", "MemoryLayerAlarmsStack");

        Map<String, Object> policy = template.findResources("AWS::IAM::Policy").values().iterator().next();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) policy.get("Properties");
        String doc = props.get("PolicyDocument").toString();

        // Bootstrap roles: deploy, file-publishing and lookup.
        for (String kind : List.of("deploy", "file-publishing", "lookup")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    doc.contains(String.format(assume, kind)), kind + "-role must be assumable");
        }
        // DescribeStacks: every deployable stack, never the CI stack.
        for (String stack : stacks) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    doc.contains(":stack/" + stack + "/*"), stack + " must be describable");
        }
        org.junit.jupiter.api.Assertions.assertFalse(doc.contains("MemoryLayerCiStack"));

        // Exactly two statements, both with explicit (non-wildcard-only) resources.
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", List.of(
                                Match.objectLike(Map.of("Action", "sts:AssumeRole")),
                                Match.objectLike(Map.of("Action", "cloudformation:DescribeStacks"))))))));

        String json = template.toJSON().toString();
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"Action\":\"*\""));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"Resource\":\"*\""));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("AdministratorAccess"));
    }
}
