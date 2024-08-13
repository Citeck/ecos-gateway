package ru.citeck.ecos.gateway

import io.prometheus.client.exemplars.Exemplar
import io.prometheus.client.exemplars.ExemplarSampler
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component

@Component
class ExemplarSamplerBeanPostProcessor : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is ExemplarSampler) {
            return IgnoreCountersExemplarSampler(bean)
        }
        return bean
    }

    class IgnoreCountersExemplarSampler(
        private val delegate: ExemplarSampler
    ) : ExemplarSampler {

        override fun sample(increment: Double, previous: Exemplar?): Exemplar? {
            // Do not return exemplar for counter metrics to allow them to be scrapped by Prometheus 2.42 and below
            return null
        }

        override fun sample(value: Double, bucketFrom: Double, bucketTo: Double, previous: Exemplar?): Exemplar {
            return delegate.sample(value, bucketFrom, bucketTo, previous)
        }
    }

}
