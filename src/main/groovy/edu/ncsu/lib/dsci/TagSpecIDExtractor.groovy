package edu.ncsu.lib.dsci

import org.marc4j.marc.ControlField
import org.marc4j.marc.DataField
import org.marc4j.marc.Record

class TagSpecIDExtractor implements IDExtractor {

    String specString

    Closure extractor

    TagSpecIDExtractor(String idSpec) {
        this.specString = idSpec
        if ( idSpec =~ /^\d\d\d$/ ) {
            extractor = {
                Record rec ->
                    def field = rec.getVariableField(idSpec)
                    if ( field instanceof ControlField ) {
                        return field?.data
                    }
                    return ((DataField)field)?.toString()
            }
        } else {
            def m = idSpec =~ /^(\d\d\d)\$(.)$/
            if (m) {
                def tag = m[0][1]
                def subfield = m[0][2].charAt(0)
                extractor = { Record rec ->
                    ((DataField) rec.getVariableField(tag))?.getSubfield(subfield)?.data
                }
            }
        }
        if ( extractor == null ) {
            throw new IllegalArgumentException("idspec '${idSpec}' is not in the proper format (### or ###\$X)")
        }
    }

    @Override
    String apply(Record record) {
        extractor.call(record)
    }
}
