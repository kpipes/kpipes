package net.kpipes.function.geo.fencing

import net.kpipes.core.function.Event
import net.kpipes.function.geo.geofencing.GeoFencingDistanceFunction
import org.junit.Test

import static org.assertj.core.api.Assertions.assertThat

class GeoFencingTest {

    @Test
    void shouldCalculateDistanceFromCircleFence() {
        def function = new GeoFencingDistanceFunction()
        def fenceConfig = [fence: [center: [lat: 49.820813, lng: 19.054982], radius: 25]]

        // When
        def result = function.onEvent(new Event('tenant.topic', 'key', [lat: 49.820829, lng: 19.056378], fenceConfig, null))

        // Then
        def response = result['response.geo.fencing.distance']
        assertThat(response.distance as double).isGreaterThan(75d)
    }

    @Test
    void shouldCalculateDistanceFromPolygonFence() {
        def function = new GeoFencingDistanceFunction()
        def fenceConfig = [fence: [polygon: [[49.821152, 19.054440], [49.820680, 19.054596], [49.820644, 19.055739]]]]

        // When
        def result = function.onEvent(new Event('tenant.topic', 'key', [lat: 49.8209418, lng: 19.0551721], fenceConfig, null))

        // Then
        def response = result['response.geo.fencing.distance']
        assertThat(response.distance as double).isBetween(50.0d, 51.0d)
    }

    @Test
    void shouldReturnZeroForPointWithinPolygon() {
        def function = new GeoFencingDistanceFunction()
        def fenceConfig = [fence: [polygon: [[49.821152, 19.054440], [49.820680, 19.054596], [49.820644, 19.055739]]]]

        // When
        def result = function.onEvent(new Event('tenant.topic', 'key', [lat: 49.820888, lng: 19.054588], fenceConfig, null))

        // Then
        def response = result['response.geo.fencing.distance']
        assertThat(response.distance as double).isEqualTo(0.0d)
    }

}
