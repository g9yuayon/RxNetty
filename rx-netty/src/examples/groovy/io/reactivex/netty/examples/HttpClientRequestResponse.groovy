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
package io.reactivex.netty.examples


import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf
import io.reactivex.netty.RxNetty
import io.reactivex.netty.protocol.http.client.HttpClientRequest
import io.reactivex.netty.protocol.http.client.HttpClientResponse

public class HttpClientRequestResponse {

    public static void main(String[] args) {

        RxNetty.createHttpClient("localhost", 8080)
                .submit(HttpClientRequest.createGet("/hello"))
                .flatMap({ HttpClientResponse<ByteBuf> response ->
                    println("Status: " + response.getStatus());
                    return response.getContent().map({
                        println(it.toString(Charset.defaultCharset()))
                    });
                }).toBlockingObservable().last();
    }
}
