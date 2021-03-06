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
package net.kpipes.lib.kafka.client

import groovy.transform.CompileStatic
import kafka.admin.AdminUtils
import kafka.admin.RackAwareMode
import kafka.utils.ZKStringSerializer$
import kafka.utils.ZkUtils
import org.I0Itec.zkclient.ZkClient
import org.apache.kafka.common.errors.TopicExistsException
import org.slf4j.Logger
import scala.collection.JavaConversions

import static kafka.admin.AdminUtils.fetchAllTopicConfigs
import static org.slf4j.LoggerFactory.getLogger

@CompileStatic
class BrokerAdmin {

    private static final Logger LOG = getLogger(BrokerAdmin)

    private final String zooKeeperHost

    private final int zooKeeperPort

    private final int defaultPartitionsNumber

    BrokerAdmin(String zooKeeperHost, int zooKeeperPort, int defaultPartitionsNumber) {
        this.zooKeeperHost = zooKeeperHost
        this.zooKeeperPort = zooKeeperPort
        this.defaultPartitionsNumber = defaultPartitionsNumber
    }

    void ensureTopicExists(Set<String> topics) {
        def zkClient = new ZkClient("${zooKeeperHost}:${zooKeeperPort}", Integer.MAX_VALUE, 10000, ZKStringSerializer$.MODULE$)
        ZkUtils zooKeeperUtils = ZkUtils.apply(zkClient, false)

        boolean topicCreated = false
        topics.each { topic ->
            try {
                if (!AdminUtils.topicExists(zooKeeperUtils, topic)) {
                    RackAwareMode mode = RackAwareMode.Disabled$.MODULE$
                    AdminUtils.createTopic(zooKeeperUtils, topic, defaultPartitionsNumber, 1, new Properties(), mode)
                    topicCreated = true
                }
            } catch (TopicExistsException e) {
                LOG.debug(e.message)
            }
        }
        if(topicCreated) {
            Thread.sleep(4000)
        }
    }

    void ensureTopicExists(String... topics) {
        ensureTopicExists(topics.toList().toSet())
    }

    List<String> topics() {
        def zkClient = new ZkClient("${zooKeeperHost}:${zooKeeperPort}", Integer.MAX_VALUE, 10000, ZKStringSerializer$.MODULE$)
        ZkUtils zooKeeperUtils = ZkUtils.apply(zkClient, false)
        JavaConversions.mapAsJavaMap(fetchAllTopicConfigs(zooKeeperUtils)).collect { it.key }
    }

    List<String> eventTopics() {
        topics().minus(['__consumer_offsets', 'kpipes.pipeDefinitions'])
    }

}