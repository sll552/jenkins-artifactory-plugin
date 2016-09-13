package org.jfrog.hudson.pipeline.steps;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.cli.MissingArgumentException;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jfrog.hudson.pipeline.Utils;
import org.jfrog.hudson.pipeline.docker.DockerAgentUtils;
import org.jfrog.hudson.pipeline.docker.DockerUtils;
import org.jfrog.hudson.pipeline.types.BuildInfo;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by romang on 5/2/16.
 */
public class DockerPull extends AbstractStepImpl {

    private final String image;
    private String username;
    private String password;
    private final BuildInfo buildInfo;

    @DataBoundConstructor
    public DockerPull(String image, String username, String password, BuildInfo buildInfo) {
        this.image = image;
        this.username = username;
        this.password = password;
        this.buildInfo = buildInfo;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public String getImage() {
        return image;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public static class Execution extends AbstractSynchronousStepExecution<BuildInfo> {
        private static final long serialVersionUID = 1L;

        @Inject(optional = true)
        private transient DockerPull step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient FilePath ws;

        @Override
        protected BuildInfo run() throws Exception {
            JenkinsBuildInfoLog log = new JenkinsBuildInfoLog(listener);

            if (step.getImage() == null) {
                getContext().onFailure(new MissingArgumentException("Missing 'image' parameter"));
                return null;
            }

            BuildInfo buildInfo = Utils.prepareBuildinfo(build, step.getBuildInfo());

            String imageTag = step.getImage();
            if (!DockerUtils.isImageVersioned(imageTag)) {
                imageTag += ":latest";
            }

            DockerAgentUtils.pullImage(launcher, imageTag, step.getUsername(), step.getPassword());
            log.info("Successfully pulled docker image: " + imageTag);
            return buildInfo;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(DockerPull.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "dockerPull";
        }

        @Override
        public String getDisplayName() {
            return "Artifactory docker push";
        }
    }

}

