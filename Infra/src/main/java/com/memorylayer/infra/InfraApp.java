package com.memorylayer.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class InfraApp {

    public static void main(final String[] args) {
        App app = new App();

        // Region is locked to ap-south-1 per AGENTS.md; account comes from the CDK CLI's
        // resolved local credentials rather than being hardcoded in source.
        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region("ap-south-1")
                .build();

        ApiStack apiStack = new ApiStack(app, "MemoryLayerApiStack",
                StackProps.builder().env(env).build());

        new FrontendStack(app, "MemoryLayerFrontendStack",
                StackProps.builder().env(env).build(),
                apiStack.getApiEndpoint());

        app.synth();
    }
}
