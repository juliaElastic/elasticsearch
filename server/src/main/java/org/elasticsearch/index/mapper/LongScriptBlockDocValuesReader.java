/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.script.LongFieldScript;

import java.io.IOException;

/**
 * {@link BlockDocValuesReader} implementation for {@code long} scripts.
 */
public class LongScriptBlockDocValuesReader extends BlockDocValuesReader {
    static class LongScriptBlockLoader extends DocValuesBlockLoader {
        private final LongFieldScript.LeafFactory factory;

        LongScriptBlockLoader(LongFieldScript.LeafFactory factory) {
            this.factory = factory;
        }

        @Override
        public Builder builder(BlockFactory factory, int expectedCount) {
            return factory.longs(expectedCount);
        }

        @Override
        public AllReader reader(LeafReaderContext context) throws IOException {
            return new LongScriptBlockDocValuesReader(factory.newInstance(context));
        }
    }

    private final LongFieldScript script;
    private int docId;

    LongScriptBlockDocValuesReader(LongFieldScript script) {
        this.script = script;
    }

    @Override
    public int docId() {
        return docId;
    }

    @Override
    public BlockLoader.Block read(BlockLoader.BlockFactory factory, BlockLoader.Docs docs, int offset) throws IOException {
        // Note that we don't pre-sort our output so we can't use longsFromDocValues
        try (BlockLoader.LongBuilder builder = factory.longs(docs.count() - offset)) {
            for (int i = offset; i < docs.count(); i++) {
                read(docs.get(i), builder);
            }
            return builder.build();
        }
    }

    @Override
    public void read(int docId, BlockLoader.StoredFields storedFields, BlockLoader.Builder builder) throws IOException {
        this.docId = docId;
        read(docId, (BlockLoader.LongBuilder) builder);
    }

    private void read(int docId, BlockLoader.LongBuilder builder) {
        script.runForDoc(docId);
        switch (script.count()) {
            case 0 -> builder.appendNull();
            case 1 -> builder.appendLong(script.values()[0]);
            default -> {
                builder.beginPositionEntry();
                for (int i = 0; i < script.count(); i++) {
                    builder.appendLong(script.values()[i]);
                }
                builder.endPositionEntry();
            }
        }
    }

    @Override
    public String toString() {
        return "ScriptLongs";
    }
}
