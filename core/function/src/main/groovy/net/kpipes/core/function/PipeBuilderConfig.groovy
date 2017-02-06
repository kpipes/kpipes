package net.kpipes.core.function

import net.kpipes.core.starter.KPipes
import net.kpipes.core.starter.spi.Service

class PipeBuilderConfig {

    @Service
    PipeBuilder pipeBuilder(KPipes kpipes) {
        new PipeBuilder(kpipes)
    }

}
