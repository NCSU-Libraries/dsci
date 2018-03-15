package edu.ncsu.lib.dsci

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.charset.StandardCharsets

@CompileStatic
@Slf4j
class BatchHandler implements RecordHandler, Closeable {

    int fileCounter = 1;

    int batchSize = 50000;

    OutputStream outputStream;

    long recordCount = 0;

    JsonOutput jsonConverter = new JsonOutput()

    File outputDirectory;

    SolrHandler solrHandler

    Set uniqueIds = new HashSet()

    BatchHandler(File outputDirectory, int batchSize = 50000, boolean emptyFirst = false) {
        this.outputDirectory = outputDirectory;
        if ( outputDirectory.isDirectory() && emptyFirst ) {
            outputDirectory.eachFile {  file ->
                file.delete()
            }
        } else {
            outputDirectory.mkdirs()
        }
        this.batchSize = batchSize;
    }


    private File nextFile() {
        return new File(outputDirectory, "solr_${fileCounter++}.json")
    }


    @Override
    void accept(Map<String, ?> record) {
        if ( record.isEmpty() || !( 'id' in record ) ) {
            log.warn("Document has no id: ${record}")
            return
        }
        uniqueIds << record.id
        if (recordCount % batchSize == 0) {
            if (outputStream != null) {
                outputStream.flush()
                outputStream.close()
            }
            outputStream = nextFile().newOutputStream()
        }

        outputStream.write(jsonConverter.toJson(record).getBytes(StandardCharsets.UTF_8))

        recordCount++
        if ( solrHandler != null ) {
            solrHandler.addDocument(record)
        }
    }


    void close() {
        if (outputStream != null) {
            outputStream.flush()
            outputStream.close()
        }
        log.info("Processed ${uniqueIds.size()} unique documents")
    }
}
