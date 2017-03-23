package net.kpipes.function.view.materialize.keyvalue.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.io.Files
import net.kpipes.core.PipeBuilder
import net.kpipes.function.view.materialize.keyvalue.FileSystemKeyValueStore
import net.kpipes.lib.testing.KPipesTest
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.utils.Bytes
import org.junit.Before
import org.junit.Test

import static net.kpipes.core.KPipesFactory.kpipes
import static net.kpipes.lib.commons.Uuids.uuid
import static org.assertj.core.api.Assertions.assertThat

class MaterializeKeyValueViewFunctionTest extends KPipesTest {

    PipeBuilder pipeBuilder

    def home = Files.createTempDir()

    def applicationId = uuid()

    def nodeId = uuid()

    @Before
    void before() {
        System.setProperty('kipes.home', home.absolutePath)
        kpipes = kpipes(applicationId, nodeId)
        pipeBuilder = kpipes.pipeBuilder()
    }

    // Tests

    @Test
    void shouldMaterializeTopicAsView() {
        // Given
        pipeBuilder.build(tenant, "${source} | view.materialize.keyvalue")
        kpipes.start()

        // When
        kafkaProducer.send(new ProducerRecord(effectiveSource, key, new Bytes(new ObjectMapper().writeValueAsBytes([foo: 'baz']))))
        Thread.sleep(1000)

        // Then
        def savedBinaries = new FileSystemKeyValueStore(new File(home, applicationId + '/store/fileSystemKeyValue')).read(effectiveSource, key)
        def savedValue = new ObjectMapper().readValue(savedBinaries, Map).foo as String
        assertThat(savedValue).isEqualTo('baz')
    }

}