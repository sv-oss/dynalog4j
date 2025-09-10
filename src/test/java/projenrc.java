import io.github.cdklabs.projen.java.JavaProject;
import io.github.cdklabs.projen.java.JavaProjectOptions;
import io.github.cdklabs.projen.java.MavenCompileOptions;
import io.github.cdklabs.projen.java.PluginOptions;
import io.github.cdklabs.projen.java.PluginExecution;
import io.github.cdklabs.projen.github.workflows.Triggers;
import io.github.cdklabs.projen.github.workflows.PullRequestOptions;
import io.github.cdklabs.projen.github.workflows.PushOptions;
import io.github.cdklabs.projen.github.workflows.Job;
import io.github.cdklabs.projen.github.workflows.JobStep;
import io.github.cdklabs.projen.github.workflows.JobPermissions;
import io.github.cdklabs.projen.github.workflows.JobStepOutput;
import java.util.Map;
import java.util.List;

public class projenrc {
    public static void main(String[] args) {
        JavaProject project = new JavaProject(JavaProjectOptions.builder()
            .artifactId("DynaLog4J")
            .groupId("au.gov.vic.dgs.digitalplatforms")
            .name("dynalog4j")
            .version("1.0.0")
            .description("Dynamic Log4j2 Level Management Tool")
            
            // Enable GitHub workflows
            .githubOptions(io.github.cdklabs.projen.github.GitHubOptions.builder()
                .workflows(true)
                .pullRequestLint(false)
                .build())
            
            // Java 21 configuration
            .compileOptions(MavenCompileOptions.builder()
                .source("21")
                .target("21")
                .build())
            
            .build());
        
        // Add dependencies - projen format is groupId/artifactId@version
        // Log4j2 Core for configuration manipulation (updated for Java 21)
        project.addDependency("org.apache.logging.log4j/log4j-core@2.22.1");
        
        // JSON and YAML parsing for configuration backends (updated for Java 21)
        project.addDependency("com.fasterxml.jackson.core/jackson-core@2.16.1");
        project.addDependency("com.fasterxml.jackson.core/jackson-databind@2.16.1");
        project.addDependency("com.fasterxml.jackson.dataformat/jackson-dataformat-yaml@2.16.1");
        
        // AWS SDK for DynamoDB backend (updated for Java 21)
        project.addDependency("software.amazon.awssdk/dynamodb@2.25.11");
        
        // AWS SSO support for authentication
        project.addDependency("software.amazon.awssdk/sso@2.25.11");
        project.addDependency("software.amazon.awssdk/ssooidc@2.25.11");
        
        // SLF4J for logging from the sidecar itself (updated for Java 21)
        project.addDependency("org.slf4j/slf4j-api@2.0.12");
        project.addDependency("org.apache.logging.log4j/log4j-slf4j2-impl@2.22.1");
        
        // CLI library for command line parsing (updated for Java 21)
        project.addDependency("info.picocli/picocli@4.7.6");
        
        // Test dependencies (updated for Java 21)
        project.addTestDependency("org.mockito/mockito-core@5.11.0");
        project.addTestDependency("org.mockito/mockito-junit-jupiter@5.11.0");
        project.addTestDependency("org.assertj/assertj-core@3.25.3");
        project.addTestDependency("org.testcontainers/junit-jupiter@1.19.7");
        project.addTestDependency("org.testcontainers/localstack@1.19.7");
        project.addTestDependency("org.awaitility/awaitility@4.2.1");
        project.addTestDependency("uk.org.webcompere/system-stubs-jupiter@2.1.6");

        // Add Maven Shade Plugin with proper execution configuration
        project.addPlugin("org.apache.maven.plugins/maven-shade-plugin@3.5.0", 
            PluginOptions.builder()
                .executions(List.of(
                    PluginExecution.builder()
                        .id("shade-jar")
                        .phase("package")
                        .goals(List.of("shade"))
                        .configuration(Map.of(
                            "createDependencyReducedPom", false,
                            "transformers", Map.of(
                                "transformer", Map.of(
                                    "@implementation", "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer",
                                    "mainClass", "au.gov.vic.dgs.digitalplatforms.dynalog4j.App"
                                )
                            )
                        ))
                        .build()
                ))
                .build()
        );

        // Add Maven Surefire Plugin configuration
        project.addPlugin("org.apache.maven.plugins/maven-surefire-plugin@3.2.5");

        project.addGitIgnore(".vscode");
        
        // Add GitHub workflows via projen
        addPullRequestWorkflow(project);
        // addMainBranchWorkflow(project);
        addReleasePleaseWorkflow(project);
        
        project.synth();
    }
    
    private static void addPullRequestWorkflow(JavaProject project) {
        var workflow = project.getGithub().addWorkflow("pull-request");
        
        workflow.on(Triggers.builder()
            .pullRequest(PullRequestOptions.builder()
                .branches(List.of("main"))
                .build())
            .build());
        
        workflow.addJob("test", Job.builder()
            .runsOn(List.of("ubuntu-latest"))
            .permissions(JobPermissions.builder().build())  // Default permissions
            .steps(List.of(
                JobStep.builder()
                    .name("Checkout code")
                    .uses("actions/checkout@v4")
                    .build(),
                JobStep.builder()
                    .name("Set up Java and Maven")
                    .uses("s4u/setup-maven-action@v1.18.0")
                    .with(Map.of(
                        "java-version", "21",
                        "java-distribution", "temurin"
                    ))
                    .build(),
                JobStep.builder()
                    .name("Build and test")
                    .run("mvn clean compile test")
                    .build()
            ))
            .build());
    }
    
    // private static void addMainBranchWorkflow(JavaProject project) {
    //     var workflow = project.getGithub().addWorkflow("main-branch-ci-cd");
        
    //     workflow.on(Triggers.builder()
    //         .push(PushOptions.builder()
    //             .branches(List.of("main"))
    //             .build())
    //         .build());
        
    //     // Build, test, and package job
    //     workflow.addJob("build-test-package", Job.builder()
    //         .runsOn(List.of("ubuntu-latest"))
    //         .permissions(JobPermissions.builder().build())
    //         .outputs(Map.of("version", JobStepOutput.builder()
    //             .stepId("get-version")
    //             .outputName("version")
    //             .build()))
    //         .steps(List.of(
    //             JobStep.builder().name("Checkout code").uses("actions/checkout@v4").build(),
    //             JobStep.builder()
    //                 .name("Set up Java 21")
    //                 .uses("actions/setup-java@v4")
    //                 .with(Map.of("java-version", "21", "distribution", "temurin"))
    //                 .build(),
    //             JobStep.builder()
    //                 .name("Cache Maven dependencies")
    //                 .uses("actions/cache@v3")
    //                 .with(Map.of(
    //                     "path", "~/.m2",
    //                     "key", "${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}",
    //                     "restore-keys", "${{ runner.os }}-maven-"
    //                 ))
    //                 .build(),
    //             JobStep.builder().name("Build and test").run("mvn clean compile test").build(),
    //             JobStep.builder().name("Package application").run("mvn package").build(),
    //             JobStep.builder()
    //                 .name("Get version from pom.xml")
    //                 .id("get-version")
    //                 .run("VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)\necho \"version=$VERSION\" >> $GITHUB_OUTPUT")
    //                 .build(),
    //             JobStep.builder()
    //                 .name("Upload artifacts")
    //                 .uses("actions/upload-artifact@v3")
    //                 .with(Map.of("name", "jar-artifacts", "path", "target/*.jar"))
    //                 .build()
    //         ))
    //         .build());
            
    //     // Publish release job
    //     workflow.addJob("publish-release", Job.builder()
    //         .runsOn(List.of("ubuntu-latest"))
    //         .needs(List.of("build-test-package"))
    //         .permissions(JobPermissions.builder()
    //             .contents(io.github.cdklabs.projen.github.workflows.JobPermission.WRITE)
    //             .build())
    //         .steps(List.of(
    //             JobStep.builder().name("Checkout code").uses("actions/checkout@v4").build(),
    //             JobStep.builder()
    //                 .name("Download artifacts")
    //                 .uses("actions/download-artifact@v3")
    //                 .with(Map.of("name", "jar-artifacts", "path", "target/"))
    //                 .build(),
    //             JobStep.builder()
    //                 .name("Create GitHub Release")
    //                 .uses("softprops/action-gh-release@v1")
    //                 .with(Map.of(
    //                     "tag_name", "v${{ needs.build-test-package.outputs.version }}",
    //                     "name", "Release v${{ needs.build-test-package.outputs.version }}",
    //                     "files", "target/DynaLog4J-*.jar",
    //                     "generate_release_notes", true
    //                 ))
    //                 .build()
    //         ))
    //         .build());
            
    //     // Build container job
    //     workflow.addJob("build-container", Job.builder()
    //         .runsOn(List.of("ubuntu-latest"))
    //         .needs(List.of("build-test-package"))
    //         .permissions(JobPermissions.builder()
    //             .contents(io.github.cdklabs.projen.github.workflows.JobPermission.READ)
    //             .idToken(io.github.cdklabs.projen.github.workflows.JobPermission.WRITE)
    //             .build())
    //         .env(Map.of(
    //             "REGISTRY", "gcr.io",
    //             "IMAGE_NAME", "dynalog4j"
    //         ))
    //         .steps(List.of(
    //             JobStep.builder().name("Checkout code").uses("actions/checkout@v4").build(),
    //             JobStep.builder()
    //                 .name("Download artifacts")
    //                 .uses("actions/download-artifact@v3")
    //                 .with(Map.of("name", "jar-artifacts", "path", "target/"))
    //                 .build(),
    //             JobStep.builder().name("Set up Docker Buildx").uses("docker/setup-buildx-action@v3").build(),
    //             JobStep.builder()
    //                 .name("Authenticate to Google Cloud")
    //                 .uses("google-github-actions/auth@v1")
    //                 .with(Map.of("credentials_json", "${{ secrets.GCP_SA_KEY }}"))
    //                 .build(),
    //             JobStep.builder().name("Configure Docker for GCR").run("gcloud auth configure-docker gcr.io").build(),
    //             JobStep.builder()
    //                 .name("Build and push Docker image")
    //                 .uses("docker/build-push-action@v5")
    //                 .with(Map.of(
    //                     "context", ".",
    //                     "push", true,
    //                     "tags", String.join("\n", List.of(
    //                         "${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/${{ env.IMAGE_NAME }}:latest",
    //                         "${{ env.REGISTRY }}/${{ secrets.GCP_PROJECT_ID }}/${{ env.IMAGE_NAME }}:v${{ needs.build-test-package.outputs.version }}"
    //                     )),
    //                     "cache-from", "type=gha",
    //                     "cache-to", "type=gha,mode=max"
    //                 ))
    //                 .build()
    //         ))
    //         .build());
    // }
    
    private static void addReleasePleaseWorkflow(JavaProject project) {
        var workflow = project.getGithub().addWorkflow("release-please");
        
        workflow.on(Triggers.builder()
            .push(PushOptions.builder()
                .branches(List.of("main"))
                .build())
            .build());
        
        // Release-please job
        workflow.addJob("release-please", Job.builder()
            .runsOn(List.of("ubuntu-latest"))
            .permissions(JobPermissions.builder()
                .contents(io.github.cdklabs.projen.github.workflows.JobPermission.WRITE)
                .pullRequests(io.github.cdklabs.projen.github.workflows.JobPermission.WRITE)
                .build())
            .outputs(Map.of(
                "release_created", JobStepOutput.builder()
                    .stepId("release")
                    .outputName("release_created")
                    .build(),
                "tag_name", JobStepOutput.builder()
                    .stepId("release")
                    .outputName("tag_name")
                    .build()
            ))
            .steps(List.of(
                JobStep.builder()
                    .name("Run release-please")
                    .id("release")
                    .uses("google-github-actions/release-please-action@v3")
                    .with(Map.of(
                        "release-type", "maven",
                        "package-name", "dynalog4j"
                    ))
                    .build()
            ))
            .build());
            
        // Build and publish job (only runs when release is created)
        workflow.addJob("build-and-publish", Job.builder()
            .ifValue("${{ needs.release-please.outputs.release_created }}")
            .needs(List.of("release-please"))
            .runsOn(List.of("ubuntu-latest"))
            .permissions(JobPermissions.builder()
                .contents(io.github.cdklabs.projen.github.workflows.JobPermission.WRITE)
                .idToken(io.github.cdklabs.projen.github.workflows.JobPermission.WRITE)
                .build())
            .steps(List.of(
                JobStep.builder().name("Checkout code").uses("actions/checkout@v4").build(),
                JobStep.builder()
                    .name("Set up Java and Maven")
                    .uses("s4u/setup-maven-action@v1.18.0")
                    .with(Map.of(
                        "java-version", "21",
                        "java-distribution", "temurin"
                    ))
                    .build(),
                JobStep.builder().name("Build and package").run("mvn clean package")
                    .build(),
                JobStep.builder()
                    .name("Upload release assets")
                    .uses("softprops/action-gh-release@v1")
                    .with(Map.of(
                        "tag_name", "${{ needs.release-please.outputs.tag_name }}",
                        "files", "target/DynaLog4J-*.jar"
                    ))
                    .build(),
                JobStep.builder().name("Set up Docker Buildx").uses("docker/setup-buildx-action@v3").build(),
                JobStep.builder()
                    .name("Authenticate to Google Cloud")
                    .uses("google-github-actions/auth@v1")
                    .with(Map.of("credentials_json", "${{ secrets.GCP_SA_KEY }}"))
                    .build(),
                JobStep.builder().name("Configure Docker for GCR").run("gcloud auth configure-docker gcr.io").build(),
                JobStep.builder()
                    .name("Extract version from tag")
                    .id("extract-version")
                    .run("VERSION=$(echo ${{ needs.release-please.outputs.tag_name }} | sed 's/^v//')\necho \"version=$VERSION\" >> $GITHUB_OUTPUT")
                    .build(),
                JobStep.builder()
                    .name("Build and push Docker image")
                    .uses("docker/build-push-action@v5")
                    .with(Map.of(
                        "context", ".",
                        "push", true,
                        "tags", String.join("\n", List.of(
                            "gcr.io/${{ secrets.GCP_PROJECT_ID }}/dynalog4j:latest",
                            "gcr.io/${{ secrets.GCP_PROJECT_ID }}/dynalog4j:${{ steps.extract-version.outputs.version }}"
                        )),
                        "cache-from", "type=gha",
                        "cache-to", "type=gha,mode=max"
                    ))
                    .build()
            ))
            .build());
    }
}