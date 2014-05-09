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
package io.reactivex.netty.pipeline;

import io.netty.channel.ChannelPipeline;
import io.reactivex.netty.channel.ObservableConnectionFactory;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.server.ErrorHandler;
import rx.Observable;

/**
 * An implementation of {@link PipelineConfigurator} which is ALWAYS added at the end of the pipeline. This
 * pipeline configurator brides between netty's pipeline processing and Rx {@link Observable}
 *
 * @param <I> Input type for the pipeline. This is the type one writes to this pipeline.
 * @param <O> Output type of the emitted observable.  This is the type one reads from this pipeline.
 *
 * @author Nitesh Kant
 */
public class RxRequiredConfigurator<I, O> implements PipelineConfigurator<I, O> {

    public static final String CONN_LIFECYCLE_HANDLER_NAME = "conn_lifecycle_handler";
    public static final String NETTY_OBSERVABLE_ADAPTER_NAME = "netty_observable_adapter";

    private final ConnectionHandler<I, O> connectionHandler;
    private final ObservableConnectionFactory<I, O> connectionFactory;
    private final ErrorHandler errorHandler;

    public RxRequiredConfigurator(final ConnectionHandler<I, O> connectionHandler, ObservableConnectionFactory<I, O> connectionFactory) {
        this(connectionHandler, connectionFactory, null);
    }

    public RxRequiredConfigurator(final ConnectionHandler<I, O> connectionHandler,
                                  ObservableConnectionFactory<I, O> connectionFactory, ErrorHandler errorHandler) {
        this.connectionHandler = connectionHandler;
        this.connectionFactory = connectionFactory;
        this.errorHandler = errorHandler;
    }

    @Override
    public void configureNewPipeline(ChannelPipeline pipeline) {

        /**
         * This method is called for each new connection & the following two channel handlers are not shareable, so
         * we need to create a new instance every time.
         */
        ObservableAdapter observableAdapter = new ObservableAdapter();
        ConnectionLifecycleHandler<I, O> lifecycleHandler =
                new ConnectionLifecycleHandler<I, O>(connectionHandler, connectionFactory, errorHandler);
        pipeline.addLast(CONN_LIFECYCLE_HANDLER_NAME, lifecycleHandler);
        pipeline.addLast(NETTY_OBSERVABLE_ADAPTER_NAME, observableAdapter);
    }
}
