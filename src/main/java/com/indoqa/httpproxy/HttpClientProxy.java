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

import static org.apache.hc.core5.http.HttpHeaders.*;
import static org.apache.hc.core5.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;

/*default*/ class HttpClientProxy implements HttpProxy {

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";

    private final String proxyMountPath;
    private final String targetBaseUri;

    private final HttpClient httpClient;
    private final ProxyPathCreator proxyPathCreator;

    private final Optional<Supplier<String>> alternativeAuthorizationSupplier;
    private final Optional<Supplier<String>> alternativeHostSupplier;

    protected HttpClientProxy(String proxyMountPath, String baseUri, HttpClient httpClient, ProxyPathCreator proxyPathModifier,
            Supplier<String> alternativeAuthorizationSupplier, Supplier<String> alternativeHostSupplier) {
        this.proxyMountPath = proxyMountPath;
        this.targetBaseUri = baseUri;
        this.httpClient = httpClient;
        this.proxyPathCreator = proxyPathModifier;
        this.alternativeAuthorizationSupplier = Optional.ofNullable(alternativeAuthorizationSupplier);
        this.alternativeHostSupplier = Optional.ofNullable(alternativeHostSupplier);
    }

    @Override
    public void proxy(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpUriRequest proxyRequest = this.createProxyRequest(request);
            this.executeProxyRequest(proxyRequest, response);
        } catch (IOException e) {
            this.writeProxyErrorResponse(response, e);
        }
    }

    protected Void writeProxyResponse(HttpServletResponse response, ClassicHttpResponse proxyResponse) throws IOException {
        this.writeResponseStatus(response, proxyResponse);
        this.writeResponseHeaders(response, proxyResponse);
        this.writeResponseBody(response, proxyResponse);

        response.flushBuffer();
        return null;
    }

    @SuppressWarnings("resource")
    private void copyRequestBody(HttpServletRequest request, HttpUriRequest proxyRequest) throws IOException {
        if (request.getContentLength() == 0) {
            return;
        }

        proxyRequest.setEntity(new InputStreamEntity(request.getInputStream(), ContentType.parse(request.getContentType())));
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpUriRequest proxyRequest) {
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            if (CONTENT_LENGTH.equalsIgnoreCase(headerName)) {
                // content length header will be implicitly set by setEntity() in copyRequestBody()
                continue;
            }

            if (this.alternativeAuthorizationSupplier.isPresent() && AUTHORIZATION.equals(headerName)) {
                // we'll use the alternativeAuthorizationSupplier later
                continue;
            }

            if (this.alternativeHostSupplier.isPresent() && HOST.equals(headerName)) {
                // we'll use the alternativeHostSupplier later
                continue;
            }

            proxyRequest.setHeader(headerName, request.getHeader(headerName));
        }

        this.alternativeAuthorizationSupplier.map(Supplier::get).ifPresent(value -> proxyRequest.setHeader(AUTHORIZATION, value));
        this.alternativeHostSupplier.map(Supplier::get).ifPresent(value -> proxyRequest.setHeader(HOST, value));
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

    private void executeProxyRequest(HttpUriRequest proxyRequest, HttpServletResponse httpResponse) throws IOException {
        this.httpClient.execute(proxyRequest, proxyResponse -> this.writeProxyResponse(httpResponse, proxyResponse));
    }

    private void writeProxyErrorResponse(HttpServletResponse response, IOException exception) {
        try {
            response.sendError(SC_INTERNAL_SERVER_ERROR, exception.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(exception);
        }
    }

    @SuppressWarnings("resource")
    private void writeResponseBody(HttpServletResponse response, ClassicHttpResponse proxyResponse) throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        if (entity == null) {
            return;
        }

        entity.writeTo(response.getOutputStream());
    }

    private void writeResponseHeaders(HttpServletResponse response, ClassicHttpResponse proxyResponse) {
        for (Header header : proxyResponse.getHeaders()) {
            response.addHeader(header.getName(), header.getValue());
        }
    }

    private void writeResponseStatus(HttpServletResponse response, ClassicHttpResponse proxyResponse) {
        response.setStatus(proxyResponse.getCode());
    }
}
