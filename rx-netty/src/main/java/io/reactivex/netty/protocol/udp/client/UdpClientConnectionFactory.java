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
package io.reactivex.netty.protocol.udp.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.channel.ObservableConnectionFactory;

import java.net.InetSocketAddress;

/**
 * A factory to create {@link UdpClientConnection}
 *
 * @author Nitesh Kant
 */
class UdpClientConnectionFactory<I, O> implements ObservableConnectionFactory<I, O> {

    private final InetSocketAddress receiverAddress;

    /**
     *
     * @param receiverAddress The default address for the {@link DatagramPacket} sent on the connections created by this
     *                        factory.
     */
    UdpClientConnectionFactory(InetSocketAddress receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    @Override
    public ObservableConnection<I, O> newConnection(ChannelHandlerContext ctx) {
        return new UdpClientConnection<I, O>(ctx, receiverAddress);
    }
}
