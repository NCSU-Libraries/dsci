package edu.ncsu.lib.dsci

import groovy.util.logging.Slf4j
import org.marc4j.marc.Record

@Slf4j
class ClosureExtractor implements IDExtractor {

    Closure closure

    ClosureExtractor(Closure c) {
        this.closure = c
    }

    @Override
    String apply(Record record) {
        try {
            closure.call(record)
        } catch (NullPointerException npx) {
            log.warn("Null pointer trying to extract ID from ${record.toString()}", npx)
        }
    }
}
