/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.streams.kstream.internals;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.streams.kstream.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * KTable repartition map functions are not exposed to public APIs, but only used for keyed aggregations.
 *
 * Given the input, it can output at most two records (one mapped from old value and one mapped from new value).
 */
public class KTableRepartitionMap<K1, V1, K2, V2> implements KTableProcessorSupplier<K1, V1, KeyValue<K2, V2>> {

    private final KTableImpl<K1, ?, V1> parent;
    private final KeyValueMapper<K1, V1, KeyValue<K2, V2>> mapper;

    public KTableRepartitionMap(KTableImpl<K1, ?, V1> parent, KeyValueMapper<K1, V1, KeyValue<K2, V2>> mapper) {
        this.parent = parent;
        this.mapper = mapper;
    }

    @Override
    public Processor<K1, Change<V1>> get() {
        return new KTableMapProcessor();
    }

    @Override
    public KTableValueGetterSupplier<K1, KeyValue<K2, V2>> view() {
        final KTableValueGetterSupplier<K1, V1> parentValueGetterSupplier = parent.valueGetterSupplier();

        return new KTableValueGetterSupplier<K1, KeyValue<K2, V2>>() {

            public KTableValueGetter<K1, KeyValue<K2, V2>> get() {
                return new KTableMapValueGetter(parentValueGetterSupplier.get());
            }

        };
    }

    @Override
    public void enableSendingOldValues() {
        // this should never be called
        throw new KafkaException("KTableRepartitionMap should always require sending old values.");
    }

    private KeyValue<K2, V2> computeValue(K1 key, V1 value) {
        KeyValue<K2, V2> newValue = null;

        if (key != null || value != null)
            newValue = mapper.apply(key, value);

        return newValue;
    }

    private class KTableMapProcessor extends AbstractProcessor<K1, Change<V1>> {

        @Override
        public void process(K1 key, Change<V1> change) {
            KeyValue<K2, V2> newPair = computeValue(key, change.newValue);

            context().forward(newPair.key, new Change<>(newPair.value, null));

            if (change.oldValue != null) {
                KeyValue<K2, V2> oldPair = computeValue(key, change.oldValue);
                context().forward(oldPair.key, new Change<>(null, oldPair.value));
            }
        }
    }

    private class KTableMapValueGetter implements KTableValueGetter<K1, KeyValue<K2, V2>> {

        private final KTableValueGetter<K1, V1> parentGetter;

        public KTableMapValueGetter(KTableValueGetter<K1, V1> parentGetter) {
            this.parentGetter = parentGetter;
        }

        @Override
        public void init(ProcessorContext context) {
            parentGetter.init(context);
        }

        @Override
        public KeyValue<K2, V2> get(K1 key) {
            return computeValue(key, parentGetter.get(key));
        }

    }

}
