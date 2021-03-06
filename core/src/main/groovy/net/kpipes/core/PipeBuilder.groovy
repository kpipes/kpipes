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
package net.kpipes.core

import com.google.common.io.Files
import groovy.transform.CompileStatic
import net.kpipes.core.function.FunctionBuilder
import net.kpipes.core.function.SimpleFunctionBuilder

import net.kpipes.core.store.ViewMaterializer
import net.kpipes.lib.commons.KPipesConfig
import net.kpipes.lib.kafka.client.BrokerAdmin
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.kstream.KTable
import org.slf4j.Logger
import org.springframework.beans.factory.NoSuchBeanDefinitionException

import static net.kpipes.core.PipeDefinitionEncoder.decodePipe
import static org.apache.kafka.streams.StreamsConfig.*
import static org.slf4j.LoggerFactory.getLogger

@CompileStatic
class PipeBuilder {

    // Logger

    private static final Logger LOG = getLogger(PipeBuilder)

    // Collaborators

    private final ServiceRegistry serviceRegistry

    private KafkaStreams kafkaStreams

    // Members

    private final KPipesConfig config

    // Internal state

    private final Set<String> topics = new HashSet<>()

    private KStreamBuilder builder = new KStreamBuilder()

    private Map<String, KStream> sourceStreams = new HashMap<>()

    private Map<String, KTable> sourceTables = new HashMap<>()

    // Constructor

    PipeBuilder(KPipesConfig kPipesConfig, ServiceRegistry serviceRegistry) {
        this.config = kPipesConfig
        this.serviceRegistry = serviceRegistry
    }

    // Operations

    void build(String tenant, String pipeDefinition) {
        build(decodePipe(tenant, pipeDefinition))
    }

    void build(PipeDefinition pipeDefinition) {
        try {
            topics << pipeDefinition.effectiveFrom()
            if (pipeDefinition.effectiveTo().isPresent()) {
                topics << pipeDefinition.effectiveTo().get()
            }

            def functionBuilders = serviceRegistry.services(FunctionBuilder)
            def function = serviceRegistry.service(pipeDefinition.functionAddress())
            def functionBuilder = functionBuilders.find { it.supports(function) } as SimpleFunctionBuilder
            functionBuilder.build(serviceRegistry.service(KPipes), pipeDefinition, function)
        } catch (NoSuchBeanDefinitionException e) {
            throw new RuntimeException("Cannot startPipes pipe. Reason: ${e.message}", e)
        }
    }

    void start() {
        LOG.debug('Ensuring that all topics involved in a pipe exist.')
        serviceRegistry.service(BrokerAdmin).ensureTopicExists(topics)

        serviceRegistry.service(ViewMaterializer).materialize('kpipes_pipeDefinitions', 'kpipes.pipeDefinitions')

        def streamsConfiguration = new Properties()
        streamsConfiguration.put(APPLICATION_ID_CONFIG, config.applicationId());
        streamsConfiguration.put(BOOTSTRAP_SERVERS_CONFIG, "${config.kafkaHost}:${config.kafkaPort()}" as String);
        streamsConfiguration.put(ZOOKEEPER_CONNECT_CONFIG, "${config.zooKeeperHost}:${config.zooKeeperPort}" as String);
        streamsConfiguration.put(KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(VALUE_SERDE_CLASS_CONFIG, Serdes.Bytes().getClass().getName());
        streamsConfiguration.put(STATE_DIR_CONFIG, Files.createTempDir().absolutePath)
        streamsConfiguration.put(COMMIT_INTERVAL_MS_CONFIG, "5000");

        kafkaStreams = new KafkaStreams(builder, streamsConfiguration)
        kafkaStreams.start()
    }

    void stop() {
        kafkaStreams.close()
    }

    KafkaStreams kafkaStreams() {
        kafkaStreams
    }

}