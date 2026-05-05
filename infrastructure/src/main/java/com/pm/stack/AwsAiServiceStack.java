package com.pm.stack;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.VpcAttributes;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;

public class AwsAiServiceStack extends Stack {

  public AwsAiServiceStack(final App scope, final String id, final StackProps props) {
    super(scope, id, props);

    // Pass existing networking explicitly so the template always contains valid IDs
    // (avoids VPC lookup context issues and CloudFormation early validation failures).
    CfnParameter vpcIdParam = CfnParameter.Builder.create(this, "VpcId")
        .type("String")
        .description("Existing VPC ID to deploy into (e.g. default VPC)")
        .build();

    CfnParameter publicSubnetIdsParam = CfnParameter.Builder.create(this, "PublicSubnetIds")
        .type("CommaDelimitedList")
        .description("Comma-delimited list of PUBLIC subnet IDs in the VPC")
        .build();

    IVpc vpc = Vpc.fromVpcAttributes(this, "AiVpc", VpcAttributes.builder()
        .vpcId(vpcIdParam.getValueAsString())
        // CDK validates subnetIds length against availabilityZones length during synth.
        // Because PublicSubnetIds is a CFN parameter, CDK cannot know its true length at synth time,
        // so we provide a single AZ to avoid a hard failure during synthesis.
        .availabilityZones(List.of("us-east-1a"))
        .publicSubnetIds(publicSubnetIdsParam.getValueAsList())
        .build());

    Cluster cluster = Cluster.Builder.create(this, "AiCluster")
        .vpc(vpc)
        .build();

    // Repo is created by EcrRepositoriesStack.
    IRepository repo = Repository.fromRepositoryName(this, "AiServiceRepo", "ai-service");

    FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "AiTaskDef")
        .cpu(256)
        .memoryLimitMiB(512)
        .build();

    ContainerDefinitionOptions containerOptions = ContainerDefinitionOptions.builder()
        .image(ContainerImage.fromEcrRepository(repo, "latest"))
        .environment(Map.of(
            "SPRING_PROFILES_ACTIVE", "prod",
            "USE_OPENAI", "false",
            // Use H2 for AWS performance testing so we don't need to provision Postgres here.
            "SPRING_DATASOURCE_URL", "jdbc:h2:mem:aidb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver",
            "SPRING_DATASOURCE_USERNAME", "sa",
            "SPRING_DATASOURCE_PASSWORD", "",
            "SPRING_JPA_HIBERNATE_DDL_AUTO", "update",
            "SPRING_JPA_DATABASE_PLATFORM", "org.hibernate.dialect.H2Dialect"
        ))
        .portMappings(List.of(
            PortMapping.builder()
                .containerPort(4003)
                .hostPort(4003)
                .protocol(Protocol.TCP)
                .build()
        ))
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(LogGroup.Builder.create(this, "AiLogGroup")
                .retention(RetentionDays.ONE_DAY)
                .build())
            .streamPrefix("ai-service")
            .build()))
        .build();

    taskDef.addContainer("ai-service", containerOptions);

    ApplicationLoadBalancedFargateService service =
        ApplicationLoadBalancedFargateService.Builder.create(this, "AiAlbService")
            .cluster(cluster)
            .serviceName("ai-service")
            .taskDefinition(taskDef)
            .desiredCount(1)
            .healthCheckGracePeriod(Duration.seconds(60))
            .publicLoadBalancer(true)
            // Without NAT gateways, tasks need a public IP to reach ECR and the internet.
            .assignPublicIp(true)
            .build();

    // Default health check path is "/", but ai-service doesn't serve 200 at root.
    // Use Actuator health so ECS doesn't continuously replace tasks as unhealthy.
    service.getTargetGroup().configureHealthCheck(HealthCheck.builder()
        .path("/actuator/health")
        .healthyHttpCodes("200")
        .interval(Duration.seconds(30))
        .timeout(Duration.seconds(5))
        .healthyThresholdCount(2)
        .unhealthyThresholdCount(3)
        .build());

    CfnOutput.Builder.create(this, "AiServiceUrl")
        .value("http://" + service.getLoadBalancer().getLoadBalancerDnsName())
        .build();
  }

  public static void main(final String[] args) {
    App app = new App(AppProps.builder().outdir("./cdk.out").build());

    // Required for CDK lookups like `Vpc.fromLookup`.
    // Defaults to the account/region we are targeting for this project.
    String account = System.getenv().getOrDefault("CDK_DEFAULT_ACCOUNT", "523688044307");
    String region = System.getenv().getOrDefault("CDK_DEFAULT_REGION", "us-east-1");

    new AwsAiServiceStack(app, "AwsAiServiceStack",
        StackProps.builder()
            .env(Environment.builder().account(account).region(region).build())
            .build());
    app.synth();
  }
}

