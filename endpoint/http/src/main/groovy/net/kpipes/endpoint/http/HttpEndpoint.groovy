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
package net.kpipes.endpoint.http

import com.fasterxml.jackson.databind.ObjectMapper
import net.kpipes.lib.kafka.client.BrokerAdmin
import net.kpipes.lib.kafka.client.KafkaConsumerBuilder
import net.kpipes.lib.kafka.client.executor.KafkaConsumerTemplate
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.utils.Bytes

import static com.google.common.base.MoreObjects.firstNonNull
import static io.vertx.core.Vertx.vertx
import static io.vertx.core.buffer.Buffer.buffer
import static net.kpipes.lib.commons.Uuids.uuid

class HttpEndpoint {

    private final KafkaConsumerTemplate kafkaConsumerTemplate

    private final KafkaProducer kafkaProducer

    private final BrokerAdmin brokerAdmin

    private final Authenticator authenticator

    // Configuration members

    private final int kafkaPort

    private final int httpPort

    // Constructors

    HttpEndpoint(KafkaConsumerTemplate kafkaConsumerTemplate, KafkaProducer kafkaProducer, BrokerAdmin brokerAdmin, Authenticator authenticator,
                 int httpPort, int kafkaPort) {
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
            if(uri == '/service') {
                def clientId = uuid()
                def responseTopic = "${authentication.get().tenant}.service.response.${clientId}"
                def requestTopic = "${authentication.get().tenant}.service.request.${clientId}"
                brokerAdmin.ensureTopicExists(requestTopic, responseTopic)
                def responseTaskId = "websocket-client-response-${clientId}"
                kafkaConsumerTemplate.subscribe(new KafkaConsumerBuilder<>(uuid()).port(kafkaPort).build(), responseTaskId, responseTopic) {
                    socket.write(buffer((it.value() as Bytes).get()))
                }

                socket.handler { message ->
                    try {
                        def requestId = uuid()
                        kafkaProducer.send(new ProducerRecord(requestTopic, requestId, new Bytes(message.bytes)))
                    } catch (Exception e) {
                        def messageText = e.message ?: 'Problem invoking operation.'
                        socket.write(buffer(new ObjectMapper().writeValueAsBytes([response: messageText, error: true])))
                    }
                }

                socket.closeHandler {
                    kafkaConsumerTemplate.stopTask(responseTaskId)
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
