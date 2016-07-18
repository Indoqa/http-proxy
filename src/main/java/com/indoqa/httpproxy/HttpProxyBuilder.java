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

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;

public final class HttpProxyBuilder {

    private HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
    private RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
    private List<BasicHeader> defaultHeaders = new ArrayList<>();

    private ProxyPathCreator proxyPathCreator = (pathAfterProxyMount, request) -> pathAfterProxyMount;

    private final String proxyMountPath;
    private final String targetBaseUrl;

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

        return new HttpClientProxy(this.proxyMountPath, this.targetBaseUrl, httpClient, this.proxyPathCreator);
    }

    public HttpProxyBuilder setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.requestConfigBuilder.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    public HttpProxyBuilder setConnectTimeout(int connectTimeout) {
        this.requestConfigBuilder.setConnectTimeout(connectTimeout);
        return this;
    }

    public HttpProxyBuilder setProxyPathCreator(ProxyPathCreator proxyPathCreator) {
        this.proxyPathCreator = proxyPathCreator;
        return this;
    }

    public HttpProxyBuilder setSocketTimeout(int socketTimeout) {
        this.requestConfigBuilder.setSocketTimeout(socketTimeout);
        return this;
    }

}
