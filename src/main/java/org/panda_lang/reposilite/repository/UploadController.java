/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.repository;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoHTTPD.ResponseException;
import org.apache.commons.io.FileUtils;
import org.panda_lang.reposilite.Configuration;
import org.panda_lang.reposilite.ReposiliteController;
import org.panda_lang.reposilite.ReposiliteHttpServer;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.auth.Authenticator;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.metadata.MetadataService;
import org.panda_lang.reposilite.utils.Result;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

final class UploadController implements ReposiliteController {

    private final Configuration configuration;
    private final Authenticator authenticator;
    private final MetadataService metadataService;

    public UploadController(Reposilite reposilite) {
        this.configuration = reposilite.getConfiguration();
        this.authenticator = reposilite.getAuthenticator();
        this.metadataService = reposilite.getMetadataService();
    }

    @Override
    public NanoHTTPD.Response serve(ReposiliteHttpServer server, NanoHTTPD.IHTTPSession httpSession) throws Exception {
        if (!configuration.isDeployEnabled()) {
            return response(Status.INTERNAL_ERROR, "Artifact deployment is disabled");
        }

        Result<Session, Response> authResult = this.authenticator.authUri(httpSession);

        if (authResult.getError().isDefined()) {
            return authResult.getError().get();
        }

        Session session = authResult.getValue().get();
        Map<String, String> files = new HashMap<>();

        try {
            httpSession.parseBody(files);
        } catch (IOException | ResponseException e) {
            return response(Status.BAD_REQUEST, "Cannot parse body");
        }

        ArtifactFile targetFile = ArtifactFile.fromURL(httpSession.getUri());

        for (Entry<String, String> entry : files.entrySet()){
            File tempFile = new File(entry.getValue());

            if (tempFile.getName().contains("maven-metadata")) {
                continue;
            }

            if (!session.hasPermission("/" + targetFile.getFile().getPath())) {
                response(Status.UNAUTHORIZED, "Unauthorized access");
            }

            FileUtils.forceMkdirParent(targetFile.getFile());
            Files.copy(tempFile.toPath(), targetFile.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        File metadataFile = new File(targetFile.getFile().getParentFile(), "maven-metadata.xml");
        metadataService.clearMetadata(metadataFile);

        return NanoHTTPD.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, "Success");
    }

    private Response response(Status status, String response) {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, response);
    }

}
