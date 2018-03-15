package edu.ncsu.lib.dsci;

import org.marc4j.marc.Record;

public class DefaultIDExtractor implements IDExtractor {
    @Override
    public String apply(Record record) {
        return record.getControlNumber();
    }
}
