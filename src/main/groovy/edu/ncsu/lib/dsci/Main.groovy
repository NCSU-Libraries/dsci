package edu.ncsu.lib.dsci

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.CollectionAdminRequest
import org.marc4j.MarcPermissiveStreamReader
import org.marc4j.MarcReader
import org.marc4j.MarcXmlReader

@Slf4j
class Main {

    static CliBuilder getCLI() {

        def specDesc = """Specify method for extracting IDs from records.

    Allowable 'idtype' values:
    
    spec=<spec> use value of field[\$subfield], e.g. '001' => use 001
                '919\$e' -- use 918 subfield e
                
    class=<class> name of a class (must be available on classpath) that extracts the ID. See README.md for more info
    
    closure=<closure file> path to a file containing the source of a Groovy closure that can be used to extract IDs (see README.md)
    
    If the 'id' option is not specified, use value from 001 for ID.
"""
        CliBuilder cli = new CliBuilder(usage: 'dsci.jar [options] [files]')
        cli.h(longOpt:'help', 'Show this message')

        cli.u(longOpt: 'solr', args: 1, argName: 'url', 'URL to a (running) Solr instance (minus any collection name)')
        cli.id(args:2, valueSeparator: '=', argName: 'idtype=value', specDesc)
        cli.f(longOpt:'format', args:1, argName: 'format', 'Force interpretation of input files as having format "marc21" or "marcxml"')
        cli._(longOpt: 'json', args: 1, argName: 'outputDir', 'Does not write to Solr directly, only creates JSON files in <outputDir>')
        cli._(longOpt: 'reset', "Creates the 'dsci' collection")
        cli.stopAtNonOption
        cli.expandArgumentFiles
        cli
    }

    static Closure compileClosureFromString(String closureSource) {
        (Closure)new GroovyShell(Main.class.classLoader).evaluate(closureSource)
    }

    static def loadIDExtractor(idType, value) {
        if ( idType == 'spec' ) {
            log.debug("Using spec '${value} for ID")
            new TagSpecIDExtractor(value)
        } else if ( idType == 'class' ) {
            log.debug("Using ${value} class for ID")
            Class.forName(value, true, this.getClass().getClassLoader()).newInstance()
        } else if ( idType == 'closure' ) {
            log.debug("Using groovy closure from ${value} for ID")
            compileClosureFromString(File(value).text) as IDExtractor
        } else {
            System.err.println("Don't understand id type '${idType}")
            null
        }
    }

    static Collection getKnownFields(File knownFields) {
        def slurper = new JsonSlurper().parse(knownFields)
        slurper['add-field'].findAll { it.name }.collect { it.name }
    }

    static String detectFormat(OptionAccessor options, File f) {
        if ( options.f ) {
            return options.f
        }

        if ( f.name.endsWith(".xml") ) {
            return 'xml'
        }
        return 'marc21'
    }

    static boolean collectionExists(String solrUrl) {
        try {
            SolrClient cl = new HttpSolrClient.Builder(solrUrl).build()
            return CollectionAdminRequest.listCollections(cl).contains("dsci")
        } catch( Exception x ) {
            log.error("Error communicating with solr.  Is it running and listening at ${solrUrl}?")
            log.error(x.message)
            System.exit(1)
        }
        false

    }

    static void main(args) {
        def cli = getCLI()
        OptionAccessor options = cli.parse(args)
        if ( options.help ) {
            cli.usage()
            System.exit(0)
        }
        def mapper = new RecordMapper()

        if ( options.ids ) {
            def (idType, value) = options.ids
            mapper.idExtractor = loadIDExtractor(idType, value)
        } else {
            mapper.idExtractor = new DefaultIDExtractor()
        }

        println options.u
        def solrUrl = options.u ? options.u : 'http://localhost:8983/solr'
        if ( solrUrl == null ) {
            solrUrl = 'http://localhost:8983/solr'
        }


        if ( options.reset || !collectionExists(solrUrl)) {
            if ( options.reset ) {
                log.info("'reset' option was specified, so we're going to create the collection")
            } else {
                log.info("'dsci' collection was not found in Solr, so we will create it.")
            }
            SolrCollectionManager collectionManager = new SolrCollectionManager(solrUrl)

            File knownFieldsFile = new File("solr", "add-fields.json")

            if (knownFieldsFile.exists()) {
                log.info("Using add-fields from previous run to create known fields")
                collectionManager.knownFields = getKnownFields(knownFieldsFile)
            } else {
                log.info("Hold on tight, we'll be creating fields as we go!")
            }

            collectionManager.init()
        }

        def handler = new BatchHandler( new File("solr") );
        SolrHandler solrHandler

        if ( !options.json ) {
            solrHandler = new SolrHandler(solrUrl, "dsci")
            handler.solrHandler = solrHandler
        }

        if ( options.arguments().empty ) {
            System.err.println("no filenames provided!")
            cli.usage()
            System.exit(1)
        }
        int fileCount = 0

        def files = options.arguments().collect{ new File(it) }.findAll { it.exists() }

        files.each {
            File marcFile ->
                fileCount++
                def fmt = Main.detectFormat(options, marcFile)
                log.info("[${fileCount}] processing ${marcFile.name} (fmt:${fmt})")

                long recFileCount = 0
                marcFile.withInputStream { inputStream ->
                    MarcReader reader = fmt.endsWith("xml") ? new MarcXmlReader(inputStream) : new MarcPermissiveStreamReader(inputStream,true, true)
                    try {
                        while (reader.hasNext()) {
                            handler.accept(mapper.mapRecord(reader.next()))
                            recFileCount++
                        }
                    } catch ( Exception e ) {
                        log.error("Unhandled exception processing ${marcFile.name}", e)
                    }

                }
                log.info("Finished ${marcFile.name}.  Processed ${recFileCount} records from ${marcFile.length()} bytes")
        }
        handler.close()
        solrHandler?.close()
        new File("solr", "add-fields.json").newWriter("utf-8").withWriter {
            new FieldModifier(mapper.seenFields, it)
        }

    }

}
