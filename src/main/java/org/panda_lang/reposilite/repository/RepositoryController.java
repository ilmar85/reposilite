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
import fi.iki.elonen.NanoHTTPD.Response.Status;
import io.vavr.control.Option;
import org.jetbrains.annotations.Nullable;
import org.panda_lang.reposilite.ReposiliteController;
import org.panda_lang.reposilite.ReposiliteHttpServer;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.utilities.commons.IOUtils;

import java.io.ByteArrayInputStream;

public final class RepositoryController implements ReposiliteController {

    private final DownloadController get;
    private final UploadController put;
    private @Nullable Throwable latestError;

    public RepositoryController(Reposilite reposilite) {
        this.get = new DownloadController(reposilite);
        this.put = new UploadController(reposilite);
    }

    @Override
    public NanoHTTPD.Response serve(ReposiliteHttpServer server, NanoHTTPD.IHTTPSession session) {
        System.out.println(session.getUri() + " " + session.getMethod().name());

        try {
            return serveOrCatch(server, session);
        } catch (Throwable e) {
            e.printStackTrace();

            this.latestError = e;
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_HTML, "Cannot serve request");
        }
    }

    public NanoHTTPD.Response serveOrCatch(ReposiliteHttpServer server, NanoHTTPD.IHTTPSession session) throws Exception {
        switch (session.getMethod()) {
            case GET:
                return get.serve(server, session);
            case HEAD:
                return toHeadResponse(get.serve(server, session));
            case PUT:
                return put.serve(server, session);
            default:
                Reposilite.getLogger().error("Unknown method: " + session.getUri());
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_HTML, "Unknown method");
        }
    }

    private NanoHTTPD.Response toHeadResponse(NanoHTTPD.Response response) {
        IOUtils.close(response.getData());
        response.setData(new ByteArrayInputStream(new byte[0]));
        response.setRequestMethod(NanoHTTPD.Method.HEAD);
        return response;
    }

    public Option<Throwable> getLatestError() {
        return Option.of(latestError);
    }

}
