package org.jfrog.hudson.pipeline.docker;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.pipeline.Utils;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by romang on 7/28/16.
 */
public class DockerUtils implements Serializable {

    /**
     * Get image Id from imageTag using DockerBuildInfo client
     *
     * @param imageTag
     * @return
     */
    public static String getImageDigest(String imageTag) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        return dockerClient.inspectImageCmd(imageTag).exec().getId();
    }

    /**
     * Get image Id from imageTag using DockerBuildInfo client
     *
     * @param imageTag
     * @return
     */
    public static void pushImage(String imageTag, String username, String password) {
        AuthConfig config = new AuthConfig();
        config.withUsername(username);
        config.withPassword(password);
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        dockerClient.pushImageCmd(imageTag).withAuthConfig(config).exec(new PushImageResultCallback()).awaitSuccess();
    }

    /**
     * Get image Id from imageTag using DockerBuildInfo client
     *
     * @param imageTag
     * @return
     */
    public static void pullImage(String imageTag, String username, String password) {
        AuthConfig config = new AuthConfig();
        config.withUsername(username);
        config.withPassword(password);
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        dockerClient.pullImageCmd(imageTag).withAuthConfig(config).exec(new PullImageResultCallback()).awaitSuccess();
    }

    /**
     * Get parent digest of an image
     *
     * @param digest
     * @return
     */
    public static String getParentDigest(String digest) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        return dockerClient.inspectImageCmd(digest).exec().getParent();
    }

    /**
     * Get image created time string
     *
     * @param digest
     * @return
     */
    public static String getImageCreated(String digest) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        return dockerClient.inspectImageCmd(digest).exec().getCreated();
    }

    /**
     * Get config digest from manifest (image id)
     *
     * @param manifest
     * @return
     * @throws IOException
     */
    public static String getConfigDigest(String manifest) throws IOException {
        JsonNode manifestTree = Utils.mapper().readTree(manifest);
        return StringUtils.remove(manifestTree.get("config").get("digest").toString(), "\"");
    }

    /**
     * Get a list of layer digests from docker manifest
     *
     * @param manifestContent
     * @return
     */
    public static List<String> getLayersDigests(String manifestContent) {
        List<String> dockerLayersDependencies = new ArrayList<String>();
        try {
            JsonNode manifest = Utils.mapper().readTree(manifestContent);
            JsonNode schemaVersion = manifest.get("schemaVersion");
            boolean isSchemeVersion1 = schemaVersion.asInt() == 1;
            JsonNode fsLayers = getFsLayers(manifest, isSchemeVersion1);
            for (JsonNode fsLayer : fsLayers) {
                JsonNode blobSum = getBlobSum(isSchemeVersion1, fsLayer);
                dockerLayersDependencies.add(blobSum.asText());
            }
            dockerLayersDependencies.add(StringUtils.remove(manifest.get("config").get("digest").toString(), "\""));

            //Add manifest sha1
            String manifestSha1 = Hashing.sha1().hashString(manifestContent, Charsets.UTF_8).toString();
            dockerLayersDependencies.add("sha1:" + manifestSha1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dockerLayersDependencies;
    }

    /**
     * return blob sum depend on scheme version
     *
     * @param isSchemeVersion1 - if true scheme version 1
     * @param manifest         - docker manifest
     * @return - layer element
     */
    private static JsonNode getFsLayers(JsonNode manifest, boolean isSchemeVersion1) {
        JsonNode fsLayers;
        if (isSchemeVersion1) {
            fsLayers = manifest.get("fsLayers");
        } else {
            fsLayers = manifest.get("layers");
        }
        return fsLayers;
    }

    /**
     * return blob sum depend on scheme version
     *
     * @param isSchemeVersion1 - if true scheme version 1
     * @param fsLayer          - docker layers
     * @return - manifest element
     */
    private static JsonNode getBlobSum(boolean isSchemeVersion1, JsonNode fsLayer) {
        JsonNode blobSum;
        if (isSchemeVersion1) {
            blobSum = fsLayer.get("blobSum");
        } else {
            blobSum = fsLayer.get("digest");
        }
        return blobSum;
    }

    /**
     * Get sha value from digest
     * example: sha256:abcabcabc12334 the value is abcabcabc12334
     *
     * @param digest
     * @return
     */
    public static String getShaValue(String digest) {
        return StringUtils.substring(digest, StringUtils.indexOf(digest, ":") + 1);
    }

    /**
     * Get sha value from digest
     * example: sha256:abcabcabc12334 the value is sha256
     *
     * @param digest
     * @return
     */
    public static String getShaVersion(String digest) {
        return StringUtils.substring(digest, 0, StringUtils.indexOf(digest, ":"));
    }

    /**
     * Parse imageTag and get the relative path of the pushed image.
     * example: url:8081/image:version to image/version
     *
     * @param imageTag
     * @return
     */
    public static String getImagePath(String imageTag) {
        int indexOfFirstSlash = imageTag.indexOf("/");
        int indexOfLastColon = imageTag.lastIndexOf(":");
        String imageName;
        String imageVersion;

        if (indexOfLastColon < 0) {
            imageName = imageTag.substring(indexOfFirstSlash + 1);
            imageVersion = "latest";
        } else {
            imageName = imageTag.substring(indexOfFirstSlash + 1, indexOfLastColon);
            imageVersion = imageTag.substring(indexOfLastColon + 1);
        }
        return imageName + "/" + imageVersion;
    }

    public static Boolean isImageVersioned(String imageTag) {
        int indexOfFirstSlash = imageTag.indexOf("/");
        int indexOfLastColon = imageTag.lastIndexOf(":");
        return indexOfFirstSlash < indexOfLastColon;
    }

    /**
     * layer file name to digest format
     *
     * @param fileName
     * @return
     */
    public static String fileNameToDigest(String fileName) {
        return StringUtils.replace(fileName, "__", ":");
    }

    /**
     * digest format to layer file name
     *
     * @param digest
     * @return
     */
    public static String digestToFileName(String digest) {
        if (StringUtils.startsWith(digest, "sha1")) {
            return "manifest.json";
        }
        return getShaVersion(digest) + "__" + getShaValue(digest);
    }

    /**
     * Returns number of dependencies layers in the image.
     *
     * @param imageContent
     * @return
     * @throws IOException
     */
    public static int getNumberOfDependentLayers(String imageContent) throws IOException {
        JsonNode history = Utils.mapper().readTree(imageContent).get("history");
        int layersNum = history.size();
        boolean newImageLayers = true;
        for (int i = history.size() - 1; i >= 0; i--) {

            if (newImageLayers) {
                layersNum--;
            }

            JsonNode layer = history.get(i);
            JsonNode emptyLayer = layer.get("empty_layer");
            if (!newImageLayers && emptyLayer != null) {
                layersNum--;
            }

            String createdBy = layer.get("created_by").textValue();
            if (createdBy.contains("ENTRYPOINT") || createdBy.contains("MAINTAINER")) {
                newImageLayers = false;
            }
        }
        return layersNum;
    }

}
