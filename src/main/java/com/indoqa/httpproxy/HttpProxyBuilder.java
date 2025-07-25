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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.message.BasicHeader;

public final class HttpProxyBuilder {

    private HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
    private RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
    private List<BasicHeader> defaultHeaders = new ArrayList<>();

    private ProxyPathCreator proxyPathCreator = (pathAfterProxyMount, request) -> pathAfterProxyMount;

    private final String proxyMountPath;
    private final String targetBaseUrl;
    private Supplier<String> alternativeAuthorizationSupplier;
    private Supplier<String> alternativeHostSupplier;

    public HttpProxyBuilder(String proxyMountPath, String targetBaseUrl) {
        this.proxyMountPath = proxyMountPath;
        this.targetBaseUrl = targetBaseUrl;
    }

    public HttpProxyBuilder addDefaultHeader(String name, String value) {
        this.defaultHeaders.add(new BasicHeader(name, value));
        return this;
    }

    @SuppressWarnings("resource")
    public HttpProxy build() {
        CloseableHttpClient httpClient = this.httpClientBuilder
            .setDefaultHeaders(this.defaultHeaders)
            .setConnectionManager(new PoolingHttpClientConnectionManager())
            .setDefaultRequestConfig(this.requestConfigBuilder.build())
            .build();

        return new HttpClientProxy(
            this.proxyMountPath,
            this.targetBaseUrl,
            httpClient,
            this.proxyPathCreator,
            this.alternativeAuthorizationSupplier,
            this.alternativeHostSupplier);
    }

    public HttpProxyBuilder setAlternativeAuthorizationSupplier(Supplier<String> alternativeAuthorizationSupplier) {
        this.alternativeAuthorizationSupplier = alternativeAuthorizationSupplier;
        return this;
    }

    public HttpProxyBuilder setAlternativeHostSupplier(Supplier<String> alternativeHostSupplier) {
        this.alternativeHostSupplier = alternativeHostSupplier;
        return this;
    }

    public HttpProxyBuilder setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.requestConfigBuilder.setConnectionRequestTimeout(connectionRequestTimeout, TimeUnit.MILLISECONDS);
        return this;
    }

    public HttpProxyBuilder setConnectTimeout(int connectTimeout) {
        this.requestConfigBuilder.setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
        return this;
    }

    public HttpProxyBuilder setProxyPathCreator(ProxyPathCreator proxyPathCreator) {
        this.proxyPathCreator = proxyPathCreator;
        return this;
    }
}
