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
        CiStack stack = new CiStack(app, "TestCiStack", StackProps.builder().env(env).build(), "octo/repo");
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
                        "repo:octo/repo:environment:production",
                        "repo:octo/repo:ref:refs/heads/main")));
        Map<String, Object> statement = Map.of(
                "Action", "sts:AssumeRoleWithWebIdentity",
                "Condition", condition);
        Map<String, Object> trust = Map.of("Statement", List.of(Match.objectLike(statement)));
        synth().hasResourceProperties("AWS::IAM::Role", Match.objectLike(Map.of(
                "MaxSessionDuration", 3600,
                "AssumeRolePolicyDocument", Match.objectLike(trust))));
    }

    @Test
    void roleCanOnlyAssumeBootstrapRolesAndDescribeApiStack() {
        Template template = synth();
        String json = template.toJSON().toString();
        // No broad grants: only the two statements below exist on the role's inline policy.
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", List.of(
                                Match.objectLike(Map.of("Action", "sts:AssumeRole")),
                                Match.objectLike(Map.of("Action", "cloudformation:DescribeStacks"))))))));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"Action\":\"*\""));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("AdministratorAccess"));
    }
}
