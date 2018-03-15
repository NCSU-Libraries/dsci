package edu.ncsu.lib.dsci

import groovy.util.logging.Slf4j
import org.marc4j.marc.ControlField
import org.marc4j.marc.DataField
import org.marc4j.marc.Record
import org.marc4j.marc.Subfield

@Slf4j
class RecordMapper {

    IDExtractor idExtractor = new DefaultIDExtractor()


    static char toChar(String str) {
        return str.charAt(0)
    }

    public def seenFields = new HashSet()

    def mapRecord(Record record) {

        def result = [:].withDefault { key ->
            []
        }

        result.id = null

        try {
            result.id = idExtractor.apply(record)
        } catch( Exception e ) {
            log.warn("Unable to extract ID", e)
            log.warn(record.toString())
        }

        if ( result.id == null ) {
            return Collections.emptyMap()
        }
        result['leader'] = record.getLeader().toString()
        result['record_type'] = record.getLeader().getTypeOfRecord();

        record.getControlFields().each {
            ControlField cf ->
                result.tags << cf.tag
                result['field_' + cf.tag ] << cf.data
        }
        record.getDataFields().each { DataField df ->
            result.tags << df.tag
            String fieldSpec = "field_${df.tag}"
            if (df.getSubfields().isEmpty() ) {
                result[fieldSpec] << df.toString()
            }
            df.subfields.each { Subfield sf ->
                if ( sf.code.isLetterOrDigit() ) {
                    result["${fieldSpec}_${String.valueOf(sf.code)}"] << sf.data
                }
            }
        }
        if ( result.hasProperty('tags') ) {
            result.tags = result.tags.unique().sort()
        }
        result.keySet().each { key -> seenFields << key }
        result
    }
}
