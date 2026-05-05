package com.pm.stack;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.Dashboard;
import software.amazon.awscdk.services.cloudwatch.GraphWidget;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;

public class MonitoringStack extends Stack {
  public MonitoringStack(final App scope, final String id, final StackProps props) {
    super(scope, id, props);

    // Minimal VPC + ECS cluster only to provide a stable ClusterName dimension token.
    // No ECS services/tasks are created here, so no container image/ECR work is needed.
    Vpc vpc = Vpc.Builder.create(this, "MonitoringVPC")
        .maxAzs(2)
        .natGateways(0)
        .subnetConfiguration(
            List.of(
                SubnetConfiguration.builder()
                    .name("Public")
                    .subnetType(SubnetType.PUBLIC)
                    .cidrMask(24)
                    .build()))
        .build();

    Cluster ecsCluster = Cluster.Builder.create(this, "PatientManagementCluster")
        .vpc(vpc)
        .build();

    Dashboard dashboard = Dashboard.Builder.create(this, "ServiceDashboard")
        .dashboardName("patient-management-service-dashboard")
        .build();

    addCpuAlarmAndWidget(dashboard, ecsCluster, "auth-service");
    addCpuAlarmAndWidget(dashboard, ecsCluster, "billing-service");
    addCpuAlarmAndWidget(dashboard, ecsCluster, "analytics-service");
    addCpuAlarmAndWidget(dashboard, ecsCluster, "patient-service");

    addMemoryAlarmAndWidget(dashboard, ecsCluster, "auth-service");
    addMemoryAlarmAndWidget(dashboard, ecsCluster, "billing-service");
    addMemoryAlarmAndWidget(dashboard, ecsCluster, "analytics-service");
    addMemoryAlarmAndWidget(dashboard, ecsCluster, "patient-service");
  }

  private void addCpuAlarmAndWidget(Dashboard dashboard, Cluster ecsCluster, String serviceName) {
    Metric cpuMetric = Metric.Builder.create()
        .namespace("AWS/ECS")
        .metricName("CPUUtilization")
        .dimensionsMap(Map.of(
            "ClusterName", ecsCluster.getClusterName(),
            "ServiceName", serviceName))
        .period(Duration.minutes(1))
        .build();

    Alarm.Builder.create(this, serviceName + "HighCpuAlarm")
        .alarmName(serviceName + "-high-cpu")
        .metric(cpuMetric)
        .threshold(80)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
        .evaluationPeriods(2)
        .build();

    GraphWidget widget = GraphWidget.Builder.create()
        .title(serviceName + " CPU Utilization")
        .left(List.of(cpuMetric))
        .width(12)
        .height(6)
        .build();

    dashboard.addWidgets(widget);
  }

  private void addMemoryAlarmAndWidget(Dashboard dashboard, Cluster ecsCluster, String serviceName) {
    Metric memoryMetric = Metric.Builder.create()
        .namespace("AWS/ECS")
        .metricName("MemoryUtilization")
        .dimensionsMap(Map.of(
            "ClusterName", ecsCluster.getClusterName(),
            "ServiceName", serviceName))
        .period(Duration.minutes(1))
        .build();

    Alarm.Builder.create(this, serviceName + "HighMemoryAlarm")
        .alarmName(serviceName + "-high-memory")
        .metric(memoryMetric)
        .threshold(80)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
        .evaluationPeriods(2)
        .build();

    GraphWidget widget = GraphWidget.Builder.create()
        .title(serviceName + " Memory Utilization")
        .left(List.of(memoryMetric))
        .width(12)
        .height(6)
        .build();

    dashboard.addWidgets(widget);
  }

  public static void main(final String[] args) {
    App app = new App(AppProps.builder().outdir("./cdk.out").build());

    StackProps props = StackProps.builder()
        .synthesizer(new BootstraplessSynthesizer())
        .build();

    new MonitoringStack(app, "MonitoringStack", props);
    app.synth();
    System.out.println("App synthesizing MonitoringStack...");
  }
}

