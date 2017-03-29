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
package net.kpipes.lib.kafka.broker

import groovy.transform.CompileStatic
import kafka.metrics.KafkaMetricsReporter
import kafka.server.KafkaConfig
import kafka.server.KafkaServer
import scala.Option
import scala.collection.JavaConversions

import static org.apache.kafka.common.utils.Time.SYSTEM

@CompileStatic
class KafkaBroker {

    private final int port

    private final String zooKeeperHost

    private final int zooKeeperPort

    private final String dataDirectory

    private KafkaServer broker

    KafkaBroker(int port, String zooKeeperHost, int zooKeeperPort, String dataDirectory) {
        this.port = port
        this.zooKeeperHost = zooKeeperHost
        this.zooKeeperPort = zooKeeperPort
        this.dataDirectory = dataDirectory
    }

    // Life-cycle

    KafkaBroker start() {
        Properties kafkaConfig = new Properties()
        kafkaConfig.setProperty("zookeeper.connect", zooKeeperHost + ":" + zooKeeperPort)
        kafkaConfig.setProperty("broker.id", "1")
        kafkaConfig.setProperty("listeners", "PLAINTEXT://0.0.0.0:${port}")
        kafkaConfig.setProperty("port", port + "")
        kafkaConfig.setProperty("log.dir", dataDirectory)
        kafkaConfig.setProperty("log.flush.interval.messages", 1 + "")
        kafkaConfig.setProperty('num.partitions', 25 + '')

        broker = new KafkaServer(new KafkaConfig(kafkaConfig), SYSTEM, Option.<String>empty(), JavaConversions.<KafkaMetricsReporter>asScalaBuffer([]))
        broker.startup()
        this
    }

    KafkaBroker stop() {
        broker.shutdown()
        this
    }

}
