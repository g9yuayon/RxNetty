/*
 * Copyright 2014 Netflix, Inc.
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
package io.reactivex.netty.protocol.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.server.ConnectionBasedServerBuilder;
import io.reactivex.netty.server.RxServer;

/**
 * A convenience builder to create instances of {@link HttpServer}
 *
 * @author Nitesh Kant
 */
public class HttpServerBuilder<I, O>
        extends ConnectionBasedServerBuilder<HttpServerRequest<I>, HttpServerResponse<O>, HttpServerBuilder<I, O>> {

    public HttpServerBuilder(int port, RequestHandler<I, O> requestHandler) {
        super(port, new HttpConnectionHandler<I, O>(requestHandler));
        pipelineConfigurator(PipelineConfigurators.<I, O>httpServerConfigurator());
    }

    public HttpServerBuilder(ServerBootstrap bootstrap, int port, RequestHandler<I, O> requestHandler) {
        super(port, new HttpConnectionHandler<I, O>(requestHandler), bootstrap);
        pipelineConfigurator(PipelineConfigurators.<I, O>httpServerConfigurator());
    }

    @Override
    public HttpServer<I, O> build() {
        return (HttpServer<I, O>) super.build();
    }

    @Override
    protected HttpServer<I, O> createServer() {
        return new HttpServer<I, O>(serverBootstrap, port, pipelineConfigurator,
                                    (HttpConnectionHandler<I, O>) connectionHandler);
    }
}
