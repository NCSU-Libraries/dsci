package edu.ncsu.lib.dsci

import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.schema.SchemaRequest
import org.apache.solr.common.SolrException
import org.apache.solr.common.SolrInputDocument

@Slf4j
class SolrHandler implements Closeable {

    SolrClient client;

    SolrClient schemaClient;

    int documentCount =0

    int commitInterval = 100000;

    int logInterval = commitInterval;

    Set definedSolrFields = new HashSet()

    Map<String, Object> standardFieldDef = Collections.unmodifiableMap([ type: 'text_general', multiValued: true, stored: true, indexed: true ])

    SolrHandler(String solrUrl, String collection) {
        client = new ConcurrentUpdateSolrClient.Builder(solrUrl + "/${collection}").build()
        schemaClient = new HttpSolrClient.Builder(solrUrl + "/${collection}").build()
        findSolrFields()
    }

    def findSolrFields() {
        Set foundFields = new HashSet()
        def req = new SchemaRequest.Fields()
        def resp = client.request(req)
        foundFields.addAll( resp.fields.collect { it.name } )
        definedSolrFields = foundFields
    }

    @Subscribe
    public void addDocument(docMap) {
        SolrInputDocument solrDoc = new SolrInputDocument()
        Set unknownFields = new HashSet()
        docMap.each { k, v ->
            if ( ! definedSolrFields.contains( k ) ) {
                unknownFields << k
            }
            solrDoc.setField(k, v)
        }
        if ( !unknownFields.empty ) {
            log.info("issuing commit for existing documents in order to add fields")
            client.commit()
            unknownFields.each { f ->
                log.debug("Adding definition for previously unknown field ${f}")
                Map m = new HashMap(standardFieldDef)
                m.name = f
                def resp = schemaClient.request(new SchemaRequest.AddField(m))
                log.trace("Add field response: ${resp}")
                definedSolrFields.add(f)
            }
        }
        try {
            client.add(solrDoc)
        } catch( SolrException slrx ) {
            log.warn("Unable to add ${docMap}", slrx)
        }
        if ( ++documentCount % logInterval == 0 ) {
            log.info("Processed ${documentCount} records")
        }
        if ( documentCount % commitInterval == 0 ) {
            log.info("Committing ${documentCount}/${commitInterval}")
            client.commit()
        }
    }

    void close() {
        client.commit()
        client.close()
        log.info("Processed ${documentCount} records in total")
    }


}
