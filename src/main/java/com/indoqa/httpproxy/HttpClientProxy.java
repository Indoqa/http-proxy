/*
 * Licensed to the Indoqa Software Design und Beratung GmbH (Indoqa) under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Indoqa licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.indoqa.httpproxy;

import static org.apache.http.HttpHeaders.*;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.function.Supplier;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.InputStreamEntity;

/*default*/ class HttpClientProxy implements HttpProxy {

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";

    private static final int STREAMCOPY_BUFFER_SIZE = 1024 * 4;
    private static final int STREAMCOPY_EOF = -1;

    private final String proxyMountPath;
    private final String targetBaseUri;

    private final HttpClient httpClient;
    private final ProxyPathCreator proxyPathCreator;

    private final Supplier<String> alternativeAuthorizationSupplier;

    protected HttpClientProxy(String proxyMountPath, String baseUri, HttpClient httpClient, ProxyPathCreator proxyPathModifier,
            Supplier<String> alternativeAuthorizationSupplier) {
        this.proxyMountPath = proxyMountPath;
        this.targetBaseUri = baseUri;
        this.httpClient = httpClient;
        this.proxyPathCreator = proxyPathModifier;
        this.alternativeAuthorizationSupplier = alternativeAuthorizationSupplier;
    }

    @Override
    public void proxy(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpUriRequest proxyRequest = this.createProxyRequest(request);
            HttpResponse proxyResponse = this.executeProxyRequest(proxyRequest);

            this.writeProxyResponse(response, proxyResponse);
        } catch (IOException e) {
            this.writeProxyErrorResponse(response, e);
        }
    }

    private void copyRequestBody(HttpServletRequest request, HttpUriRequest proxyRequest) throws IOException {
        if (!(proxyRequest instanceof HttpEntityEnclosingRequest)) {
            return;
        }

        HttpEntityEnclosingRequest httpEntityEnclosingRequest = (HttpEntityEnclosingRequest) proxyRequest;
        httpEntityEnclosingRequest.setEntity(new InputStreamEntity(request.getInputStream()));
    }

    @SuppressWarnings("unchecked")
    private void copyRequestHeaders(HttpServletRequest request, HttpUriRequest proxyRequest) {
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            if (CONTENT_LENGTH.equalsIgnoreCase(headerName)) {
                // content length header will be implicitly set by setEntity() in copyRequestBody()
                continue;
            }

            if (this.alternativeAuthorizationSupplier != null && AUTHORIZATION.equals(headerName)) {
                // we'll use the alternativeAuthorizationSupplier later
                continue;
            }

            proxyRequest.setHeader(headerName, request.getHeader(headerName));
        }

        if (this.alternativeAuthorizationSupplier != null) {
            proxyRequest.setHeader(AUTHORIZATION, this.alternativeAuthorizationSupplier.get());
        }
    }

    private String createProxyPath(HttpServletRequest request) {
        String requestURI = request.getRequestURI();

        int proxyMountPathStartPosition = requestURI.indexOf(this.proxyMountPath);

        if (proxyMountPathStartPosition == -1) {
            throw new IllegalArgumentException("Proxy request path needs to start with defined proxyMountPath!");
        }

        StringBuilder pathBuilder = new StringBuilder();

        String pathAfterProxyMount = requestURI.substring(proxyMountPathStartPosition + this.proxyMountPath.length());
        pathBuilder.append(this.proxyPathCreator.createPath(pathAfterProxyMount, request));

        return pathBuilder.toString();
    }

    private HttpUriRequest createProxyRequest(HttpServletRequest request) throws IOException {
        String url = this.createProxyUrl(request);
        String method = request.getMethod();

        HttpUriRequest proxyRequest = this.createProxyRequest(method, url);

        this.copyRequestHeaders(request, proxyRequest);
        this.copyRequestBody(request, proxyRequest);

        return proxyRequest;
    }

    private HttpUriRequest createProxyRequest(String method, String url) {
        switch (method) {
            case METHOD_GET:
                return new HttpGet(url);
            case METHOD_POST:
                return new HttpPost(url);
            case METHOD_PUT:
                return new HttpPut(url);
            case METHOD_DELETE:
                return new HttpDelete(url);
            default:
                throw new IllegalArgumentException("Proxy doesn't support method: " + method);
        }
    }

    private String createProxyUrl(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder(this.targetBaseUri);

        builder.append(this.createProxyPath(request));

        String queryString = request.getQueryString();
        if (queryString != null) {
            builder.append("?");
            builder.append(queryString);
        }

        return builder.toString();
    }

    private HttpResponse executeProxyRequest(HttpUriRequest proxyRequest) throws IOException {
        return this.httpClient.execute(proxyRequest);
    }

    private void writeProxyErrorResponse(HttpServletResponse response, IOException exception) {
        try {
            response.sendError(SC_INTERNAL_SERVER_ERROR, exception.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(exception);
        }
    }

    @SuppressWarnings("resource")
    private void writeProxyResponse(HttpServletResponse response, HttpResponse proxyResponse) throws IOException {
        this.writeResponseStatus(response, proxyResponse);
        this.writeResponseHeaders(response, proxyResponse);

        HttpEntity entity = proxyResponse.getEntity();

        if (entity == null) {
            return;
        }

        InputStream responseBody = entity.getContent();
        ServletOutputStream outputStream = response.getOutputStream();

        this.writeResponseBody(responseBody, outputStream);

        response.flushBuffer();
    }

    private void writeResponseBody(InputStream responseBody, ServletOutputStream outputStream) throws IOException {
        try {
            byte[] buffer = new byte[STREAMCOPY_BUFFER_SIZE];
            int n;

            while ((n = responseBody.read(buffer)) > STREAMCOPY_EOF) {
                outputStream.write(buffer, 0, n);
            }
        } finally {
            responseBody.close();
            outputStream.close();
        }
    }

    private void writeResponseHeaders(HttpServletResponse response, HttpResponse proxyResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            response.addHeader(header.getName(), header.getValue());
        }
    }

    private void writeResponseStatus(HttpServletResponse response, HttpResponse proxyResponse) {
        response.setStatus(proxyResponse.getStatusLine().getStatusCode());
    }
}
