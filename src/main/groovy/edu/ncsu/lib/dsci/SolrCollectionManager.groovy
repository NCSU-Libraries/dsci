package edu.ncsu.lib.dsci

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.CollectionAdminRequest
import org.apache.solr.client.solrj.request.schema.SchemaRequest

import javax.annotation.PostConstruct

@CompileStatic
@Slf4j
class SolrCollectionManager {

    String baseUrl

    String collectionName = "dsci"

    Collection knownFields = new HashSet()

    Map<String, ?> standardFieldDef = Collections.unmodifiableMap([ type: 'text_general', multiValued: true, stored: true, indexed: true ])

    SolrCollectionManager(String solrUrl = "http://localhost:8983/solr") {
        this.baseUrl = solrUrl
    }

    private void deleteCollection(SolrClient client) {
        log.info("Collection ${collectionName} already exists, deleting")
        CollectionAdminRequest.Delete delRequest = CollectionAdminRequest.deleteCollection(collectionName)
        def resp = client.request(delRequest)
        log.info("Response: ${resp}")
    }

    private void createCollection(SolrClient client) {
        def createRequest = CollectionAdminRequest.createCollection(collectionName, 1, 1)
        def createResponse = client.request(createRequest)
        log.info("Create collection response: ${createResponse}")
    }

    /**
     * Adds all field names in the 'knownFields' collection using a
     * standard definition.  Assumes none of them already exist ...
     */
    private void addKnownFields() {
        // lets make a concurrent update version to speed this up, eh?
        ConcurrentUpdateSolrClient updateClient = new ConcurrentUpdateSolrClient.Builder(this.baseUrl).build()
        if ( !knownFields.empty ) {
            log.info("Found fields definitions, will create")
            knownFields.toSorted().each { fieldName ->
                if ( log.traceEnabled ) {
                    log.trace("Adding definition for ${fieldName}")
                } else {
                    print("#")
                }
                def fieldDef = new HashMap(standardFieldDef)
                fieldDef.name = fieldName
                def addRequest = new SchemaRequest.AddField(fieldDef)
                updateClient.request(addRequest, collectionName)
            }
            if ( !log.traceEnabled ) {
                println("")
            }
            log.info("Done creating fields")
        }
        log.info("Waiting for all field updates to complete ...")
        updateClient.blockUntilFinished()
        updateClient.close()
        log.info("done.")
    }

    @PostConstruct
    void init() {
        SolrClient client = new HttpSolrClient.Builder(baseUrl).allowCompression(true).build()
        CollectionAdminRequest.listCollections(client)
        if ( collectionName in CollectionAdminRequest.listCollections(client) ) {
            deleteCollection(client)
        }
        createCollection(client)
        addKnownFields()
    }
}
