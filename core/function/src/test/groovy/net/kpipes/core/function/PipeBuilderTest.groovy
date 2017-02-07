package net.kpipes.core.function

import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.kpipes.core.event.Event
import net.kpipes.core.event.EventSerializer
import net.kpipes.lib.kafka.client.KafkaConsumerBuilder
import net.kpipes.lib.kafka.client.KafkaProducerBuilder
import net.kpipes.lib.kafka.client.executor.KafkaConsumerTemplate
import net.kpipes.lib.testing.KPipesTest
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.utils.Bytes
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.Callable

import static com.jayway.awaitility.Awaitility.await
import static net.kpipes.core.function.FunctionBinding.functionBinding
import static net.kpipes.lib.commons.Uuids.uuid
import static org.assertj.core.api.Assertions.assertThat

@RunWith(VertxUnitRunner)
class PipeBuilderTest {

    static def kpipesTest = new KPipesTest().start()

    static def kpipes = kpipesTest.kpipes()

    static def kafkaPort = kpipesTest.kafkaPort()

    @BeforeClass
    static void beforeClass() {
        functionBinding(kpipes, 'hello.world') { it.body().hello = it.body().name; it }.start()
    }

    @Test(timeout = 30000L)
    void pipeShouldInvokeFunction(TestContext context) {
        def async = context.async()
        def serializer = new EventSerializer()
        kpipes.service(PipeBuilder).build('source | hello.world | results')

        // When
        def producer = new KafkaProducerBuilder().port(kafkaPort).build()
        producer.send(new ProducerRecord('source', 'key', new Bytes(serializer.serialize(new Event([:], [:], [name: 'henry'])))))

        // Then
        def resultsConsumer = new KafkaConsumerBuilder<String, Bytes>(uuid()).port(kafkaPort).build()
        await().until({ resultsConsumer.partitionsFor('results').size() > 0 } as Callable<Boolean>)
        resultsConsumer.subscribe(['results'])
        kpipes.service(KafkaConsumerTemplate).consumeRecord(resultsConsumer) {
            def event = serializer.deserialize(it.value().get())
            assertThat(event.body().hello).isEqualTo('henry')
            async.complete()
        }
    }

}
