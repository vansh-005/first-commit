package com.memorylayer.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.MetricOptions;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.Map;

/**
 * Phase 7: operational alarms — none existed before this phase. One SNS topic with a single
 * email subscription fans out every alarm, deliberately simple for MVP scale; split into
 * per-severity topics only if alarm volume ever justifies it. Thresholds are the exact values
 * from the approved Phase 7 plan, not guesses.
 *
 * <p>Kept as its own stack rather than added to {@code ApiStack}/{@code IngestionStack}: these
 * are cross-cutting operational resources over both, not a property of either one.
 */
public class AlarmsStack extends Stack {

    public AlarmsStack(final Construct scope, final String id, final StackProps props,
                        final ApiStack apiStack, final IngestionStack ingestionStack,
                        final DataStack dataStack, final String alarmEmail) {
        super(scope, id, props);

        Topic alarmTopic = Topic.Builder.create(this, "AlarmTopic")
                .topicName("memory-layer-alarms")
                .displayName("Memory Layer operational alarms")
                .build();
        alarmTopic.addSubscription(new EmailSubscription(alarmEmail));
        SnsAction notify = new SnsAction(alarmTopic);

        Function apiFunction = apiStack.getApiFunction();
        Function coordinatorFunction = ingestionStack.getCoordinatorFunction();
        Function reconcilerFunction = ingestionStack.getReconcilerFunction();
        Queue ingestionQueue = dataStack.getIngestionQueue();
        Queue ingestionDlq = dataStack.getIngestionDeadLetterQueue();

        // --- API Lambda --------------------------------------------------------------------
        errorCountAlarm("ApiErrors", apiFunction.metricErrors(period(Duration.minutes(5))), 3, notify);
        Alarm.Builder.create(this, "ApiThrottles")
                .metric(apiFunction.metricThrottles(period(Duration.minutes(5))))
                .threshold(1)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .alarmDescription("API Lambda throttled at least once in 5 minutes")
                .build()
                .addAlarmAction(notify);

        // --- Ingestion Lambdas ---------------------------------------------------------------
        errorCountAlarm("CoordinatorErrors", coordinatorFunction.metricErrors(period(Duration.minutes(5))), 1, notify);
        errorCountAlarm("ReconcilerErrors", reconcilerFunction.metricErrors(period(Duration.minutes(5))), 1, notify);

        // --- EventBridge: the reconciler's own schedule rule failing to invoke ---------------
        // IRule exposes no convenience metric method, unlike IFunction/IQueue — built directly
        // against the documented AWS/Events namespace and RuleName dimension.
        Alarm.Builder.create(this, "ReconcilerScheduleFailedInvocations")
                .metric(Metric.Builder.create()
                        .namespace("AWS/Events")
                        .metricName("FailedInvocations")
                        .dimensionsMap(Map.of("RuleName", ingestionStack.getReconcilerScheduleRule().getRuleName()))
                        .period(Duration.minutes(5))
                        .statistic("Sum")
                        .build())
                .threshold(1)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .alarmDescription("EventBridge failed to invoke the Status Reconciler at least once")
                .build()
                .addAlarmAction(notify);

        // --- Reconciler "didn't run" — tolerant ~10 minute window, not one missed minute -----
        // The schedule is rate(1 minute), so ~10 invocations are expected in any 10-minute
        // window; alarming only when that whole window shows zero avoids paging on a single
        // transient blip.
        Alarm.Builder.create(this, "ReconcilerNotRunning")
                .metric(reconcilerFunction.metricInvocations(period(Duration.minutes(10))))
                .threshold(1)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.LESS_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.BREACHING)
                .alarmDescription("Status Reconciler had zero invocations in a 10-minute window")
                .build()
                .addAlarmAction(notify);

        // --- SQS -------------------------------------------------------------------------------
        // 35 minutes: within the approved 30-45 minute guidance range, comfortably below the
        // ~100-minute normal backpressure budget (Docs/ARCHITECTURE.md §7.3.2) so it still
        // fires well before a healthy-but-waiting message could reach the DLQ.
        Alarm.Builder.create(this, "IngestionQueueOldestMessage")
                .metric(ingestionQueue.metricApproximateAgeOfOldestMessage(MetricOptions.builder()
                        .period(Duration.minutes(5))
                        .statistic("Maximum")
                        .build()))
                .threshold(Duration.minutes(35).toSeconds())
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .alarmDescription("Oldest message in the ingestion queue exceeds 35 minutes")
                .build()
                .addAlarmAction(notify);

        // Any DLQ occupancy is anomalous here — Phase 4 verification confirmed the DLQ stays
        // empty under normal operation, including through repeated real ConflictException
        // backpressure.
        Alarm.Builder.create(this, "IngestionDlqVisible")
                .metric(ingestionDlq.metricApproximateNumberOfMessagesVisible(MetricOptions.builder()
                        .period(Duration.minutes(5))
                        .statistic("Maximum")
                        .build()))
                .threshold(0)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .alarmDescription("A message is visible in the ingestion DLQ")
                .build()
                .addAlarmAction(notify);
    }

    private void errorCountAlarm(String id, Metric metric, int threshold, SnsAction notify) {
        Alarm.Builder.create(this, id)
                .metric(metric)
                .threshold(threshold)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build()
                .addAlarmAction(notify);
    }

    private static MetricOptions period(Duration duration) {
        return MetricOptions.builder().period(duration).statistic("Sum").build();
    }
}
