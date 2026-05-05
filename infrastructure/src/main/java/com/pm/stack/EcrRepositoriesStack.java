package com.pm.stack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecr.Repository;

public class EcrRepositoriesStack extends Stack {

  public EcrRepositoriesStack(final App scope, final String id, final StackProps props) {
    super(scope, id, props);

    // Minimal set to start AWS deployments (expand later if needed).
    Repository.Builder.create(this, "AiServiceRepo")
        .repositoryName("ai-service")
        .build();
  }

  public static void main(final String[] args) {
    App app = new App(AppProps.builder().outdir("./cdk.out").build());
    new EcrRepositoriesStack(app, "EcrRepositoriesStack", StackProps.builder().build());
    app.synth();
  }
}

