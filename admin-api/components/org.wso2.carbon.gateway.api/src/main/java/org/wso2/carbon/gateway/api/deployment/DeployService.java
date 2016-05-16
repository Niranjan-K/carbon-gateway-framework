/*
 * Copyright (c) 2016, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.gateway.api.deployment;

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.msf4j.HttpStreamHandler;
import org.wso2.msf4j.HttpStreamer;
import org.wso2.msf4j.Microservice;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

/**
 * MSF4J Based service for deploying artifacts.
 */
@Component(
        name = "org.wso2.carbon.gateway.api.deployment.DeployService",
        service = Microservice.class,
        immediate = true)
@Path("/deploy/")
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    /**
     * Takes a set of iFlow files as multipart and deploys them.
     */
    @POST
    @Path("/{fileName}")
    public void postFile(@Context HttpStreamer httpStreamer,
                         @PathParam("fileName") String fileName)
            throws IOException {
        httpStreamer.callback(new HttpStreamHandlerImpl(fileName));
    }

    private static class HttpStreamHandlerImpl implements HttpStreamHandler {
        private static final String MOUNT_PATH = "/home/ravi/";
        private FileChannel fileChannel = null;
        private org.wso2.msf4j.Response response;

        public HttpStreamHandlerImpl(String fileName) throws FileNotFoundException {
            File file = Paths.get(MOUNT_PATH.toString(), fileName).toFile();
            if (file.getParentFile().exists() || file.getParentFile().mkdirs()) {
                fileChannel = new FileOutputStream(file).getChannel();
            }
        }

        @Override
        public void init(org.wso2.msf4j.Response response) {
            this.response = response;
        }

        @Override
        public void end() throws Exception {
            fileChannel.close();
            response.setStatus(Response.Status.ACCEPTED.getStatusCode());
            response.send();
        }

        @Override
        public void chunk(ByteBuffer content) throws Exception {
            if (fileChannel == null) {
                throw new IOException("Unable to write file");
            }
            content.flip();
            fileChannel.write(content);
        }

        @Override
        public void error(Throwable cause) {
            try {
                if (fileChannel != null) {
                    fileChannel.close();
                }
            } catch (IOException e) {
                // Log if unable to close the output stream
                log.error("Unable to close file output stream", e);
            }
        }
    }


//    @POST
//    @Path("/iflow")
//    public Response deployIflow(MultipartBody body) {
//        for (Attachment at: body.getAllAttachments()) {
//            DataHandler dataHandler = at.getDataHandler();
//
//            try {
//                InputStream is = dataHandler.getInputStream();
//                String fileName = parseFileName(at.getHeaders());
//                OutputStream out = new FileOutputStream(new File(parseFileName(at.getHeaders())));
//                int read = 0;
//                byte[] bytes = new byte[1024];
//                while ((read = is.read(bytes)) != -1) {
//                    out.write(bytes, 0, read);
//                }
//                is.close();
//                out.flush();
//                out.close();
//            } catch (IOException e) {
//                log.error("There was an error while processing iflow upload.", e);
//                return Response.serverError().build();
//            }
//
//        }
//
//        return Response.accepted().build();
//    }
//
//
//    private String parseFileName(MultivaluedMap<String, String> header) {
//
//        String[] cdHeader = header.getFirst("Content-Disposition").split(";");
//
//        for (String cd : cdHeader) {
//            if ((cd.trim().startsWith("filename"))) {
//
//                String[] name = cd.split("=");
//
//                String fileName = name[1].trim().replaceAll("\"", "");
//
//                if (fileName.endsWith(".iflow")) {
//                    return fileName;
//                } else {
//                    return fileName + ".iflow";
//                }
//            }
//        }
//
//        return "unknown-filename-" + UUID.randomUUID() + ".iflow";
//    }
}
