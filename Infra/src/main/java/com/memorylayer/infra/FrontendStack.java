package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.amplify.CfnApp;
import software.amazon.awscdk.services.amplify.CfnBranch;
import software.constructs.Construct;

import java.util.List;

/**
 * Amplify Hosting for the frontend, built on the stable L1 constructs (CfnApp/CfnBranch)
 * rather than the experimental amplify-alpha module, per project decision.
 *
 * <p>Manual one-time prerequisite (outside CDK, before this stack can deploy):
 * <ol>
 *   <li>Install the "AWS Amplify" GitHub App on the {@code vansh-005/first-commit} repo
 *       (github.com/apps/aws-amplify-console), or create a classic GitHub Personal Access
 *       Token with {@code repo} scope.</li>
 *   <li>Store that token as a plaintext Secrets Manager secret so it never enters source
 *       control or the CloudFormation template:
 *       <pre>
 *       aws secretsmanager create-secret \
 *         --name memory-layer/amplify-github-token \
 *         --secret-string '&lt;token&gt;' \
 *         --region ap-south-1
 *       </pre>
 *   </li>
 * </ol>
 * Do not run {@code cdk deploy} for this stack until that secret exists.
 */
public class FrontendStack extends Stack {

    private static final String GITHUB_TOKEN_SECRET_NAME = "memory-layer/amplify-github-token";

    // Amplify's own default SPA fallback rule: serve index.html for any path that isn't a
    // static asset, so React Router routes survive direct navigation/refresh.
    private static final String SPA_REWRITE_SOURCE =
            "</^[^.]+$|\\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json)$)([^.]+$)/>";

    public FrontendStack(final Construct scope, final String id, final StackProps props, final String apiBaseUrl) {
        super(scope, id, props);

        CfnApp app = CfnApp.Builder.create(this, "AmplifyApp")
                .name("memory-layer")
                .repository("https://github.com/vansh-005/first-commit")
                // Resolved from Secrets Manager at deploy time via a CloudFormation dynamic
                // reference — the token itself never appears in source or in this template.
                .accessToken(SecretValue.secretsManager(GITHUB_TOKEN_SECRET_NAME).unsafeUnwrap())
                .platform("WEB")
                .buildSpec(String.join("\n",
                        "version: 1",
                        "applications:",
                        "  - appRoot: Frontend",
                        "    frontend:",
                        "      phases:",
                        "        preBuild:",
                        "          commands:",
                        "            - npm ci",
                        "        build:",
                        "          commands:",
                        "            - npm run build",
                        "      artifacts:",
                        "        baseDirectory: dist",
                        "        files:",
                        "          - '**/*'",
                        "      cache:",
                        "        paths:",
                        "          - node_modules/**/*"))
                .customRules(List.of(
                        CfnApp.CustomRuleProperty.builder()
                                .source(SPA_REWRITE_SOURCE)
                                .target("/index.html")
                                .status("200")
                                .build()))
                .build();

        CfnBranch branch = CfnBranch.Builder.create(this, "MainBranch")
                .appId(app.getAttrAppId())
                .branchName("main")
                .stage("PRODUCTION")
                .enableAutoBuild(true)
                .environmentVariables(List.of(
                        CfnBranch.EnvironmentVariableProperty.builder()
                                .name("VITE_API_BASE_URL")
                                .value(apiBaseUrl)
                                .build()))
                .build();

        CfnOutput.Builder.create(this, "AmplifyAppId")
                .value(app.getAttrAppId())
                .build();

        CfnOutput.Builder.create(this, "AmplifyDefaultDomain")
                .value("https://" + branch.getBranchName() + "." + app.getAttrDefaultDomain())
                .description("Deployed frontend URL once the branch build completes")
                .build();
    }
}
