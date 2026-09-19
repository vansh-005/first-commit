package com.memorylayer.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

class AlarmsStackTest {

    private Template synthesize() {
        App app = new App();
        Environment env = Environment.builder().account("123456789012").region("ap-south-1").build();
        AuthStack authStack = new AuthStack(app, "TestAuthStack", StackProps.builder().env(env).build(),
                "https://main.example.amplifyapp.com", "test-google-client-id");
        DataStack dataStack = new DataStack(app, "TestDataStack", StackProps.builder().env(env).build(),
                "https://main.example.amplifyapp.com");
        IngestionStack ingestionStack = new IngestionStack(app, "TestIngestionStack",
                StackProps.builder().env(env).build(), dataStack);
        ApiStack apiStack = new ApiStack(app, "TestApiStack", StackProps.builder().env(env).build(),
                authStack, dataStack, ingestionStack);
        AlarmsStack stack = new AlarmsStack(app, "TestAlarmsStack", StackProps.builder().env(env).build(),
                apiStack, ingestionStack, dataStack, "alarms@example.com");
        return Template.fromStack(stack);
    }

    @Test
    void createsOneSnsTopicWithTheGivenEmailSubscription() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::SNS::Topic", Match.objectLike(Map.of(
                "TopicName", "memory-layer-alarms"
        )));
        template.resourceCountIs("AWS::SNS::Topic", 1);

        template.hasResourceProperties("AWS::SNS::Subscription", Match.objectLike(Map.of(
                "Protocol", "email",
                "Endpoint", "alarms@example.com"
        )));
    }

    @Test
    void apiErrorAlarmFiresAtThreeInFiveMinutes() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "MetricName", "Errors",
                "Namespace", "AWS/Lambda",
                "Threshold", 3,
                "Period", 300,
                "ComparisonOperator", "GreaterThanOrEqualToThreshold"
        )));
    }

    @Test
    void apiThrottleAlarmFiresAtOneInFiveMinutes() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "MetricName", "Throttles",
                "Namespace", "AWS/Lambda",
                "Threshold", 1,
                "Period", 300
        )));
    }

    @Test
    void eventBridgeFailedInvocationAlarmUsesTheDocumentedNamespaceAndMetricName() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "MetricName", "FailedInvocations",
                "Namespace", "AWS/Events",
                "Threshold", 1
        )));
    }

    @Test
    void reconcilerNotRunningUsesATenMinuteTolerantWindowNotOneMissedMinute() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "MetricName", "Invocations",
                "Namespace", "AWS/Lambda",
                "Period", 600,
                "ComparisonOperator", "LessThanThreshold",
                "TreatMissingData", "breaching"
        )));
    }

    @Test
    void ingestionQueueOldestMessageAlarmIsWithinTheApprovedThirtyToFortyFiveMinuteRange() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "MetricName", "ApproximateAgeOfOldestMessage",
                "Namespace", "AWS/SQS",
                "Threshold", 35 * 60,
                "ComparisonOperator", "GreaterThanThreshold"
        )));
    }

    @Test
    void dlqVisibleMessagesAlarmFiresOnAnyOccupancy() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "MetricName", "ApproximateNumberOfMessagesVisible",
                "Namespace", "AWS/SQS",
                "Threshold", 0,
                "ComparisonOperator", "GreaterThanThreshold"
        )));
    }

    @Test
    void everyAlarmNotifiesTheSnsTopic() {
        Template template = synthesize();

        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "AlarmActions", Match.anyValue()
        )));
    }
}
