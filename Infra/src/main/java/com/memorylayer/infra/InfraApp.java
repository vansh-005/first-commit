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

    public static void main(final String[] args) {
        App app = new App();

        // Region is locked to ap-south-1 per AGENTS.md; account comes from the CDK CLI's
        // resolved local credentials rather than being hardcoded in source.
        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region("ap-south-1")
                .build();

        String googleClientId = System.getenv("GOOGLE_OAUTH_CLIENT_ID");
        if (googleClientId == null || googleClientId.isBlank()) {
            // Synth-only placeholder: real deploy requires the real env var (not sensitive,
            // but deliberately kept out of source so nothing has to be edited to deploy).
            googleClientId = "placeholder-google-client-id";
        }

        AuthStack authStack = new AuthStack(app, "MemoryLayerAuthStack",
                StackProps.builder().env(env).build(), AMPLIFY_ORIGIN, googleClientId);

        DataStack dataStack = new DataStack(app, "MemoryLayerDataStack",
                StackProps.builder().env(env).build(), AMPLIFY_ORIGIN);

        ApiStack apiStack = new ApiStack(app, "MemoryLayerApiStack",
                StackProps.builder().env(env).build(), authStack, dataStack);

        new FrontendStack(app, "MemoryLayerFrontendStack",
                StackProps.builder().env(env).build(),
                apiStack.getApiEndpoint(), authStack);

        app.synth();
    }
}
