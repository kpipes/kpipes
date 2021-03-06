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
package net.kpipes.cmd

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import net.kpipes.core.KPipesApplication
import org.junit.Test

import static com.google.common.io.Files.createTempDir
import static io.vertx.core.buffer.Buffer.buffer
import static net.kpipes.cmd.Cmd.parseArguments
import static net.kpipes.cmd.Cmd.parseOptions
import static net.kpipes.lib.commons.Mavens.kpipesVersion
import static net.kpipes.lib.commons.Networks.availableTcpPort
import static net.kpipes.lib.commons.Uuids.uuid
import static org.assertj.core.api.Assertions.assertThat

class CmdTest {

    @Test
    void shouldHandleStoppedServer() {
        def response = new Cmd('localhost', availableTcpPort()).executeCommand(['kpipes', 'version']) as String
        assertThat(response).startsWith('Cannot connect to KPipes server')
    }

    @Test
    void shouldExtractArgumentsFromCommand() {
        def arguments = parseArguments(['service', 'operation', 'argument1', 'argument2'])
        assertThat(arguments).isEqualTo(['argument1', 'argument2'])
    }

    @Test
    void shouldParseMapArgument() {
        def arguments = parseArguments(['service', 'operation', 'argument1', "[foo: 'bar']"])
        assertThat(arguments).isEqualTo(['argument1', [foo: 'bar']])
    }

    @Test
    void shouldParseResponse() {
        int port = availableTcpPort()
        Vertx.vertx().createHttpServer(new HttpServerOptions().setPort(port)).websocketHandler { webSocket ->
            webSocket.handler {
                webSocket.write(buffer(new ObjectMapper().writeValueAsBytes([response: 'hello'])))
            }
        }.listen()

        Thread.sleep(1000)
        def response = new Cmd('localhost', port).executeCommand(['kpipes', 'version']) as String
        assertThat(response).isEqualTo('hello')
    }

    @Test
    void shouldParseHostOption() {
        def options = parseOptions(['--host=myhost', 'service', 'operation', 'argument1', "[foo: 'bar']"])
        assertThat(options).containsEntry('host', 'myhost')
    }

    @Test
    void shouldIndentMap() {
        def map = Cmd.displayMap(0, [a: 'b', c: [c1: 'd1', c2: 'd2']])
        assertThat(map).contains('c\n\tc1')
    }

    @Test
    void shouldIndentList() {
        def map = Cmd.displayMap(0, [a: 'b', c: [1,2,3]])
        println map
        assertThat(map).contains('c\n\t1')
    }

}
