/**
 * Licensed to the KPipes under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The licenses this file to You under the Apache License, Version 2.0
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
package net.kpipes.adapter.websockets

import com.fasterxml.jackson.databind.ObjectMapper
import net.kpipes.core.KPipesContext
import net.kpipes.core.adapter.AbstractAdapter
import net.kpipes.lib.kafka.client.BrokerAdmin
import net.kpipes.lib.kafka.client.KafkaConsumerBuilder
import net.kpipes.lib.kafka.client.executor.KafkaConsumerTemplate
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.utils.Bytes

import static com.google.common.base.MoreObjects.firstNonNull
import static io.vertx.core.Vertx.vertx
import static io.vertx.core.buffer.Buffer.buffer
import static net.kpipes.lib.commons.Uuids.uuid

class WebSocketsAdapter extends AbstractAdapter {

    private final KafkaConsumerTemplate kafkaConsumerTemplate

    private final KafkaProducer kafkaProducer

    private final BrokerAdmin brokerAdmin

    private final Authenticator authenticator

    // Configuration members

    private final int kafkaPort

    private final int httpPort

    // Constructors

    WebSocketsAdapter(KPipesContext kpipesContext, KafkaConsumerTemplate kafkaConsumerTemplate, KafkaProducer kafkaProducer, BrokerAdmin brokerAdmin, Authenticator authenticator,
                      int httpPort, int kafkaPort) {
        super(kpipesContext)
        this.kafkaConsumerTemplate = kafkaConsumerTemplate
        this.kafkaProducer = kafkaProducer
        this.brokerAdmin = brokerAdmin
        this.authenticator = authenticator
        this.httpPort = httpPort
        this.kafkaPort = kafkaPort
    }

    // Life-cycle

    void start() {
        vertx().createHttpServer().websocketHandler { socket ->
            def authentication = authenticator.authenticate(socket.headers().collectEntries())
            if(!authentication.present) {
                socket.reject()
            }

            def uri = socket.uri()
            if(uri == '/operation') {
                socket.handler { message ->
                    try {
                        socket.write(buffer(invokeOperation(authentication.get().tenant, message.bytes)))
                    } catch (Exception e) {
                        socket.write(buffer(new ObjectMapper().writeValueAsBytes([response: e.message, error: true])))
                    }
                }
            } else if(uri.startsWith('/notification/')) {
                def channelName = uri.replaceFirst(/\/notification\//, '')
                def channel = "${authentication.get().tenant()}.notification.${channelName}"
                def historyMode = firstNonNull(socket.headers().get('history'), 'latest')
                if(historyMode == 'all') {
                    historyMode = 'earliest'
                }
                def kafkaConsumer = new KafkaConsumerBuilder<>(uuid()).port(kafkaPort).offsetReset(historyMode).build()
                kafkaConsumerTemplate.subscribe(kafkaConsumer, channel) {
                    socket.write(buffer((it.value() as Bytes).get()))
                }
                socket.closeHandler {
                    kafkaConsumer.wakeup()
                }
            } else {
                socket.reject()
            }
        }.listen(httpPort)
    }

}
