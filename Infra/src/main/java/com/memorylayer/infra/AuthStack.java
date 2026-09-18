package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AttributeMapping;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.CognitoDomainOptions;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.ProviderAttribute;
import software.amazon.awscdk.services.cognito.ResourceServerScope;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.StandardAttribute;
import software.amazon.awscdk.services.cognito.StandardAttributes;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientIdentityProvider;
import software.amazon.awscdk.services.cognito.UserPoolDomain;
import software.amazon.awscdk.services.cognito.UserPoolIdentityProviderGoogle;
import software.amazon.awscdk.services.cognito.UserPoolResourceServer;
import software.constructs.Construct;

import java.util.List;

/**
 * Phase 2: Cognito User Pool with Google federation + native email/password, a public
 * (no-secret) PKCE app client for the SPA, and a custom resource-server scope that protected
 * API routes require. Independent of ApiStack/FrontendStack so it can be created first; those
 * stacks consume its outputs (issuer, client ID, scope name).
 */
public class AuthStack extends Stack {

    public static final String RESOURCE_SERVER_IDENTIFIER = "memory-api";
    public static final String ACCESS_SCOPE_NAME = "access";
    public static final String FULL_ACCESS_SCOPE = RESOURCE_SERVER_IDENTIFIER + "/" + ACCESS_SCOPE_NAME;

    private static final String GOOGLE_CLIENT_SECRET_NAME = "memory-layer/google-oauth-client-secret";

    private final UserPool userPool;
    private final UserPoolClient userPoolClient;
    private final UserPoolDomain userPoolDomain;

    public AuthStack(final Construct scope, final String id, final StackProps props,
                      final String amplifyOrigin, final String googleClientId) {
        super(scope, id, props);

        this.userPool = UserPool.Builder.create(this, "UserPool")
                .userPoolName("memory-layer-users")
                .selfSignUpEnabled(true)
                .signInAliases(SignInAliases.builder().email(true).build())
                .autoVerify(AutoVerifiedAttrs.builder().email(true).build())
                .standardAttributes(StandardAttributes.builder()
                        .email(StandardAttribute.builder().required(true).mutable(true).build())
                        .build())
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                // Dev-friendly for the hackathon: deleting the stack deletes all users.
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        String domainPrefix = "memory-layer-auth-" + accountSuffix();
        this.userPoolDomain = UserPoolDomain.Builder.create(this, "UserPoolDomain")
                .userPool(userPool)
                .cognitoDomain(CognitoDomainOptions.builder().domainPrefix(domainPrefix).build())
                .build();

        UserPoolIdentityProviderGoogle googleProvider = UserPoolIdentityProviderGoogle.Builder.create(this, "GoogleProvider")
                .userPool(userPool)
                .clientId(googleClientId)
                // Resolved from Secrets Manager at deploy time via a CloudFormation dynamic
                // reference (same pattern as the Amplify GitHub token) — never plaintext here.
                .clientSecretValue(SecretValue.secretsManager(GOOGLE_CLIENT_SECRET_NAME))
                .scopes(List.of("openid", "email", "profile"))
                .attributeMapping(AttributeMapping.builder()
                        .email(ProviderAttribute.GOOGLE_EMAIL)
                        .emailVerified(ProviderAttribute.GOOGLE_EMAIL_VERIFIED)
                        .fullname(ProviderAttribute.GOOGLE_NAME)
                        .profilePicture(ProviderAttribute.GOOGLE_PICTURE)
                        .build())
                .build();

        ResourceServerScope accessScope = ResourceServerScope.Builder.create()
                .scopeName(ACCESS_SCOPE_NAME)
                .scopeDescription("Access the Memory Layer API")
                .build();

        UserPoolResourceServer resourceServer = UserPoolResourceServer.Builder.create(this, "ApiResourceServer")
                .userPool(userPool)
                .identifier(RESOURCE_SERVER_IDENTIFIER)
                .scopes(List.of(accessScope))
                .build();

        List<String> callbackUrls = List.of(amplifyOrigin + "/login", "http://localhost:5173/login");
        List<String> logoutUrls = List.of(amplifyOrigin + "/", "http://localhost:5173/");

        this.userPoolClient = UserPoolClient.Builder.create(this, "SpaClient")
                .userPool(userPool)
                .userPoolClientName("memory-layer-spa")
                // Public PKCE client: no client secret, since it runs in the browser.
                .generateSecret(false)
                .oAuth(OAuthSettings.builder()
                        .flows(OAuthFlows.builder().authorizationCodeGrant(true).build())
                        .scopes(List.of(
                                OAuthScope.OPENID,
                                OAuthScope.EMAIL,
                                OAuthScope.PROFILE,
                                OAuthScope.resourceServer(resourceServer, accessScope)))
                        .callbackUrls(callbackUrls)
                        .logoutUrls(logoutUrls)
                        .build())
                .supportedIdentityProviders(List.of(
                        UserPoolClientIdentityProvider.COGNITO,
                        UserPoolClientIdentityProvider.GOOGLE))
                .build();

        // Cognito has no CFN Ref-based link between a client's supportedIdentityProviders
        // (plain strings) and the actual IdP/resource-server resources, so CloudFormation has
        // no natural reason to create those first. Force correct ordering explicitly.
        userPoolClient.getNode().addDependency(googleProvider);
        userPoolClient.getNode().addDependency(resourceServer);

        CfnOutput.Builder.create(this, "UserPoolId").value(userPool.getUserPoolId()).build();
        CfnOutput.Builder.create(this, "UserPoolClientId").value(userPoolClient.getUserPoolClientId()).build();
        CfnOutput.Builder.create(this, "Issuer").value(userPool.getUserPoolProviderUrl()).build();
        CfnOutput.Builder.create(this, "HostedDomain").value(userPoolDomain.baseUrl()).build();
        CfnOutput.Builder.create(this, "GoogleRedirectUri")
                .value(userPoolDomain.baseUrl() + "/oauth2/idpresponse")
                .description("Register this exact URI as an Authorized redirect URI in Google Cloud Console")
                .build();
    }

    private String accountSuffix() {
        String account = this.getAccount();
        return account.length() > 6 ? account.substring(account.length() - 6) : account;
    }

    public String getIssuer() {
        return userPool.getUserPoolProviderUrl();
    }

    public String getUserPoolClientId() {
        return userPoolClient.getUserPoolClientId();
    }

    public String getUserPoolId() {
        return userPool.getUserPoolId();
    }

    public String getHostedDomain() {
        return userPoolDomain.baseUrl();
    }
}
