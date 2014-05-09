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
package io.reactivex.netty.protocol.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.ClientChannelAbstractFactory;
import io.reactivex.netty.client.ConnectionPool;
import io.reactivex.netty.client.RxClientImpl;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

public class HttpClientImpl<I, O> extends RxClientImpl<HttpClientRequest<I>, HttpClientResponse<O>> implements HttpClient<I, O> {

    public HttpClientImpl(ServerInfo serverInfo, Bootstrap clientBootstrap,
            PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
            ClientConfig clientConfig, ConnectionPool<HttpClientResponse<O>, HttpClientRequest<I>> pool,
            ClientChannelAbstractFactory<HttpClientResponse<O>, HttpClientRequest<I>> clientChannelAbstractFactory) {
        super(serverInfo, clientBootstrap, pipelineConfigurator, clientConfig, clientChannelAbstractFactory, pool);
    }

    public HttpClientImpl(ServerInfo serverInfo, Bootstrap clientBootstrap,
                          PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> pipelineConfigurator,
                          ClientConfig clientConfig) {
        this(serverInfo, clientBootstrap, pipelineConfigurator, clientConfig, null,
             new HttpClientChannelAbstractFactory<I, O>());
    }

    @Override
    public Observable<HttpClientResponse<O>> submit(HttpClientRequest<I> request) {
        return submit(request, connect());
    }

    @Override
    public Observable<HttpClientResponse<O>> submit(HttpClientRequest<I> request, ClientConfig config) {
        return submit(request, connect(), config);
    }

    protected Observable<HttpClientResponse<O>> submit(final HttpClientRequest<I> request,
                                                       Observable<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>> connectionObservable) {
        return submit(request, connectionObservable, null == clientConfig
                                                     ? HttpClientConfig.Builder.newDefaultConfig() : clientConfig);
    }
    
    protected boolean shouldFollowRedirectForRequest(ClientConfig config, HttpClientRequest<I> request) {
        Boolean followRedirect;
        if (config instanceof HttpClientConfig) {
            followRedirect = ((HttpClientConfig) config).getFollowRedirect();
        } else {
            followRedirect = Boolean.FALSE;
        }
        if (followRedirect == Boolean.TRUE) {
            // caller has explicitly set follow redirect, we will follow redirect for all requests
            return true;
        } else if (followRedirect == null && (request.getMethod() == HttpMethod.HEAD || request.getMethod() == HttpMethod.GET)) {
            // no option is set, we should follow GET and HEAD
            return true;
        } else {
            return false;
        }
    }

    protected Observable<HttpClientResponse<O>> submitWithoutRedirect(final HttpClientRequest<I> request,
            final Observable<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>> connectionObservable,
            final ClientConfig config) {
        enrichRequest(request, config);

        // Here we do not map the connection Observable and return because the onComplete() of connectionObservable,
        // does not indicate onComplete of the request processing.
        return Observable.create(new Observable.OnSubscribe<HttpClientResponse<O>>() {
            @Override
            public void call(final Subscriber<? super HttpClientResponse<O>> subscriber) {
                final ConnectObserver<I, O> connectObserver = new ConnectObserver<I, O>(request, subscriber);
                final Subscription connectSubscription = connectionObservable.subscribe(connectObserver);
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        connectSubscription.unsubscribe();
                        connectObserver.cancel(); // Also closes the connection.
                    }
                }));
            }
        });
    }
    
    protected Observable<HttpClientResponse<O>> submit(final HttpClientRequest<I> request,
                                                 final Observable<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>> connectionObservable,
                                                 final ClientConfig config) {
        if (shouldFollowRedirectForRequest(config, request)) {
            FollowRedirectHttpClientImpl<I, O> redirectClient =
                    new FollowRedirectHttpClientImpl<I, O>(serverInfo, clientBootstrap, originalPipelineConfigurator,
                                                           clientConfig, pool, clientChannelAbstractFactory);
            return redirectClient.submit(request, connectionObservable, config);
        } else {
            return submitWithoutRedirect(request, connectionObservable, config);
        }
    }

    /*visible for testing*/ ConnectionPool<HttpClientResponse<O>, HttpClientRequest<I>> getConnectionPool() {
        return pool;
    }

    private void enrichRequest(HttpClientRequest<I> request, ClientConfig config) {
        if(!request.getHeaders().contains(HttpHeaders.Names.HOST)) {
            request.getHeaders().add(HttpHeaders.Names.HOST, serverInfo.getHost());
        }

        if (config instanceof HttpClientConfig) {
            HttpClientConfig httpClientConfig = (HttpClientConfig) config;
            if (httpClientConfig.getUserAgent() != null && request.getHeaders().get(HttpHeaders.Names.USER_AGENT) == null) {
                request.getHeaders().set(HttpHeaders.Names.USER_AGENT, httpClientConfig.getUserAgent());
            }
        }
    }

    private class ConnectObserver<I, O> extends CompositeObserver<ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>>> {

        private final HttpClientRequest<I> request;
        private final Observer<? super HttpClientResponse<O>> requestProcessingObserver;
        private ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>> connection; // Nullable

        public ConnectObserver(HttpClientRequest<I> request, Observer<? super HttpClientResponse<O>> requestProcessingObserver) {
            super(requestProcessingObserver);
            this.request = request;
            this.requestProcessingObserver = requestProcessingObserver;
        }

        @Override
        public void onCompleted() {
            // We do not want an onComplete() call to Request Processing Observer on onComplete of connection observable.
            // If we do not override this, super.onCompleted() will invoke onCompleted on requestProcessingObserver.
        }

        @Override
        public void onNext(ObservableConnection<HttpClientResponse<O>, HttpClientRequest<I>> newConnection) {
            connection = newConnection;
            ClientRequestResponseConverter converter =
                    connection.getChannelHandlerContext().pipeline().get(ClientRequestResponseConverter.class);
            if (null != converter) {
                converter.setRequestProcessingObserver(requestProcessingObserver);
            }
            connection.getInput().subscribe(requestProcessingObserver);
            connection.writeAndFlush(request).subscribe(new Observer<Void>() {
                @Override
                public void onCompleted() {
                    // Completion and onNext are managed by the observer itself.
                }

                @Override
                public void onError(Throwable throwable) {
                    // If the write fails, the response should get the error. Completion & onNext are managed by
                    // the response observable itself.
                    requestProcessingObserver.onError(throwable);
                }

                @Override
                public void onNext(Void aVoid) {
                    // Completion and onNext are managed by the observer itself.
                }
            });
        }

        Observable<Void> cancel() {
            if (null != connection) {
                return connection.close(); // Also cancels any pending writes.
            } else {
                return Observable.empty();
            }
        }
    }
}