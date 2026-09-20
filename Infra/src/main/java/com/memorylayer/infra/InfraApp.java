package com.memorylayer.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class InfraApp {

    // Known, already-deployed Amplify domain (Phase 1: MemoryLayerFrontendStack). Stable as
    // long as that stack's CfnApp/CfnBranch logical IDs don't change (they won't for the
    // env-var additions Phase 2 makes), since CloudFormation keeps existing resources'
    // physical IDs stable across updates.
    public static final String AMPLIFY_ORIGIN = "https://main.d28nd6lc9fjyiv.amplifyapp.com";

    // Repository allowed to assume the CI deploy role (OIDC trust; see CiStack).
    public static final String GITHUB_REPO = "vansh-005/first-commit";

    public static void main(final String[] args) {
        App app = new App();

        // Region is locked to ap-south-1 per AGENTS.md; account comes from the CDK CLI's
        // resolved local credentials rather than being hardcoded in source.
        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region("ap-south-1")
                .build();

        // Phase 7: no silent placeholder fallback. A missing env var used to silently
        // synthesize "placeholder-google-client-id", which was then actually deployed to the
        // real Cognito Google identity provider during a Phase 5 incident (cdk deploy pulled
        // AuthStack in as an undeclared dependency of another stack) — briefly breaking Google
        // Sign-In. Failing synth loudly is strictly the better failure mode: there is no
        // legitimate reason to synthesize this stack against the real account without the
        // real value.
        String googleClientId = requireEnv("GOOGLE_OAUTH_CLIENT_ID");
        String alarmEmail = requireEnv("ALARM_EMAIL");

        AuthStack authStack = new AuthStack(app, "MemoryLayerAuthStack",
                StackProps.builder().env(env).build(), AMPLIFY_ORIGIN, googleClientId);

        DataStack dataStack = new DataStack(app, "MemoryLayerDataStack",
                StackProps.builder().env(env).build(), AMPLIFY_ORIGIN);

        // Constructed before ApiStack (as of Phase 5) — ApiStack's /search route needs a
        // reference to the Knowledge Base this stack creates.
        IngestionStack ingestionStack = new IngestionStack(app, "MemoryLayerIngestionStack",
                StackProps.builder().env(env).build(), dataStack);

        ApiStack apiStack = new ApiStack(app, "MemoryLayerApiStack",
                StackProps.builder().env(env).build(), authStack, dataStack, ingestionStack);

        new FrontendStack(app, "MemoryLayerFrontendStack",
                StackProps.builder().env(env).build(),
                apiStack.getApiEndpoint(), authStack);

        // Phase 7: constructed last — depends on resources from both ApiStack and
        // IngestionStack (Lambda functions, the reconciler's schedule Rule, the ingestion
        // queue/DLQ from DataStack).
        new AlarmsStack(app, "MemoryLayerAlarmsStack",
                StackProps.builder().env(env).build(), apiStack, ingestionStack, dataStack, alarmEmail);

        // GitHub Actions OIDC provider + deploy role for .github/workflows/deploy-infra.yml.
        // Deployed only locally via deploy.ps1 (never by the workflow — see CiStack).
        new CiStack(app, "MemoryLayerCiStack",
                StackProps.builder().env(env).build(), GITHUB_REPO);

        app.synth();
    }

    /** Fails synth immediately, with a clear message naming the missing variable, rather than
     * ever silently substituting a value that must never reach a real deployment. */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable " + name + " is not set. Refusing to synthesize "
                            + "with a silent placeholder — see Docs/OPERATIONS.md for the deploy procedure.");
        }
        return value;
    }
}
