package edu.ncsu.lib.dsci;

import org.marc4j.marc.Record;

public interface IDExtractor {
    String apply(Record record);
}
