package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.CfnOIDCProvider;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.FederatedPrincipal;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * GitHub Actions -> AWS federation for the manual infrastructure deploy workflow
 * (.github/workflows/deploy-infra.yml). Short-lived credentials only (OIDC ->
 * sts:AssumeRoleWithWebIdentity); no long-lived access keys exist anywhere.
 *
 * <p>The role itself has almost no permissions: it may only assume the CDK bootstrap
 * deploy/file-publishing roles (which do the real work, exactly as for a local `cdk deploy`) and
 * read the ApiStack outputs for the post-deploy health check. Trust is pinned to this repository
 * and to two subjects: the `production` GitHub Environment (the deploy job, which can require
 * reviewer approval) and code running on `main` (the read-only-in-practice plan job that shows
 * `cdk diff` before approval). Pull requests, forks and other branches cannot assume it.
 *
 * <p>Deliberately NOT deployable from the workflow (the workflow's stack allowlist excludes it): a
 * role must never be able to rewrite its own trust policy. Deploy this stack locally through
 * Infra/deploy.ps1.
 */
public class CiStack extends Stack {

    private static final String OIDC_HOST = "token.actions.githubusercontent.com";
    // Default CDK bootstrap qualifier (matches the deployed CDKToolkit stack).
    private static final String BOOTSTRAP_QUALIFIER = "hnb659fds";

    /**
     * @param githubOidcRepo the repository as it appears in the OIDC {@code sub} claim. Repositories
     *     created after 2026-07-15 (including this one) get IMMUTABLE subjects that embed the numeric
     *     owner and repo IDs: {@code owner@ownerId/repo@repoId} - not the plain {@code owner/repo}.
     *     A plain-name trust policy never matches and fails with "Not authorized to perform
     *     sts:AssumeRoleWithWebIdentity". IDs also stop a deleted-and-recreated (or renamed) repo
     *     from inheriting this trust.
     */
    public CiStack(final Construct scope, final String id, final StackProps props, final String githubOidcRepo) {
        super(scope, id, props);

        CfnOIDCProvider provider = CfnOIDCProvider.Builder.create(this, "GithubOidcProvider")
                .url("https://" + OIDC_HOST)
                .clientIdList(List.of("sts.amazonaws.com"))
                .build();

        Role deployRole = Role.Builder.create(this, "GithubDeployRole")
                .roleName("memory-layer-github-deploy")
                .description("Assumed by GitHub Actions (OIDC) to run cdk deploy via the CDK bootstrap roles")
                .maxSessionDuration(Duration.hours(1))
                .assumedBy(new FederatedPrincipal(provider.getAttrArn(), Map.<String, Object>of(
                        "StringEquals", Map.<String, Object>of(
                                OIDC_HOST + ":aud", "sts.amazonaws.com",
                                OIDC_HOST + ":sub", List.of(
                                        "repo:" + githubOidcRepo + ":environment:production",
                                        "repo:" + githubOidcRepo + ":ref:refs/heads/main"))),
                        "sts:AssumeRoleWithWebIdentity"))
                .build();

        deployRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("AssumeCdkBootstrapRoles")
                .effect(Effect.ALLOW)
                .actions(List.of("sts:AssumeRole"))
                .resources(List.of(
                        bootstrapRoleArn("deploy-role"),
                        bootstrapRoleArn("file-publishing-role")))
                .build());

        deployRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ReadApiStackOutputsForSmokeTest")
                .effect(Effect.ALLOW)
                .actions(List.of("cloudformation:DescribeStacks"))
                .resources(List.of(formatArn(software.amazon.awscdk.ArnComponents.builder()
                        .service("cloudformation")
                        .resource("stack")
                        .resourceName("MemoryLayerApiStack/*")
                        .build())))
                .build());

        CfnOutput.Builder.create(this, "GithubDeployRoleArn").value(deployRole.getRoleArn()).build();
    }

    private String bootstrapRoleArn(final String kind) {
        return "arn:" + getPartition() + ":iam::" + getAccount() + ":role/cdk-" + BOOTSTRAP_QUALIFIER + "-" + kind
                + "-" + getAccount() + "-" + getRegion();
    }
}
