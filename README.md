# Dead Simple Catalog Indexer

Do you have MARC records, perhaps stored in an ILS or similar system?  Have you
ever been asked "Do we have any records where the 856 has a subfield z with the
word 'Yoink!' in it?" or similar questions?  Does your ILS not seem to support
this kind of query?  Then you might find this useful.

## What it Does

Takes a pile of MARC records, and converts the fields into a sort of key-value
JSON document, then throws the results into Solr.  Oh yes, and it will manage
the Solr index creation for you.  When it's done, you should be able to query
the resulting index to answer various low-level collection-spanning questions
such as the above.

## What It Does (This Time With More Detail)

Each MARC record is converted into a document that has the rough structure:

```json
{
    "id" : "12345",
    "leader" : "...",
    "001" : "...",
    "003" : "...",
    "009" : "...",
    "field_010_a" : [ "...", "...", "..." ],
    "json_doesn't support comments": "...",
    "tags": [ "001", "...", "999" ]
}
```

That is, for each document, it outputs an `id` value (which has to be
extractable from the MARC record), the textual value of the leader and any of
the control fields (tags 001-009) as simple values.

Data fields (tag 010-999) get mapped out into their subfields into field names that follow the pattern:

"field_{tag}_{subfield}"

Additionally, the record will have a `tags` field which is simply an array of
all the tags that were found in the record.

It assumes that you're using the 'data driven schema' configuration (which is the default in the most recent
versions of Solr), which is fine for general uses but clashes a bit with the field names emitted by the indexer,
for example the default schema says any field with a name ending in `_i` should be understood to be an integer, `_f` is
a floating point number, etc.  We can get around this without having to create a schema ourselves by ensuring
that each field we encounter is given an explicit definition.  It adds a bit of overhead at runtime.

All fields, with the exception of the ID field, will be automatically created for you as they are encountered
in documents sent to Solr. They will be tagged as type `text_general`,
`stored`, `indexed`, and `multiValued`.

When these documents are inserted into Solr, you can do things like:

    $ # find all the documents with no 001
    $ curl http://localhost:8983/solr/dsci/select?q=tag:\(NOT 001\)

    $ # the goofy query from the intro
    $ curl http://localhost:8983/solr/dsci/select?q=field_856_a:Yoink\!
    
    $ # find documents with a 510 that doesn't have a subfield b
    $ curl http://localhost:8984/solr/dsci/select?q=tags:510+AND+-\(field_510_b:\*\)


Note the above have been escaped for direct use in the shell.  The backslash
characters (`\`) should be removed if you want to paste the URLs into a browser
window.

## Wait, What, Solr?  Don't I need a System Administrator to Set That Up?

Probably, if you want to do things in production.  But this is aimed at *D*ead
*S*implicity, and I am willing to cut a few corners in pursuit of that goal.  
So here's all you may need to do:

1. Download [Apache Solr]([https://lucene.apache.org/solr/)
2. Unpack Solr
3. Start Solr in "Cloud" mode:
    
    
    $ cd $SOLR_DIR
    $ bin/solr -c start
    

4. Build and run the application
    
    
    $ cd /path/where/you/cloned/this/respository/at
    $ ./gradlew shadowJar # or ./gradlew.bat on Windows
    $ java -jar  build/libs/dsci.jar -c marc/*.xml

Sorry about the paths if you're on Windows, but I hope you can figure it out.

If you have Ruby installed on your system, you can use TRLN's [solrtasks](https://github.com/trln/solrtasks) gem to install solr and start it.  Or not, whatever's simpler for you!

### "Pile of MARC"?

After all the options, come a bunch of filenames which are understood as containing MARC;
if the filename ends in `.xml` then it will be processed as if it were MARCXML, otherwise
it will be processed as MARC21 (binary) format.  You can override the extension-based detection
by passing the `-f xml` (or `-f marc21`) parameter.

If it breaks here (and it might because it assumes MARC21 means MARC-8, but it might not), you might look
into using `yaz-marcdump` (part of the [yaz](https://github.com/indexdata/yaz) toolkit by IndexData) to 
convert to UTF-8 encoded MARCXML. 

### Wait, What is this project written in? Java?  Groovy?

You'll need it to run Solr anyhow.  But other than having the `java`
executable on your PATH, you shouldn't need anything other than the contents of this
repository.  Make sure you use a
*Java Development* Kit (JDK) installation, as that's what's needed to compile things; a suitable
location to download this is from http://openjdk.java.net/.  

The JRE (Java Runtime Environment) you might have lying around to run Java in
your browser (which you should probably delete anyway) is not sufficient.


When building the project, use the 'gradle wrapper' (`gradlew` or `gradlew.bat` in the
root directory) to run your builds, and it will download all the stuff you need.

### OK, IDs, where did those IDs come from?

Document IDs are special in Solr -- they must be unique, and if you 'add' a
document which has the same ID of a document already in the index, you're
really overwriting it.  So they're kind of important.

By default, the process will look in the `001` field and use that as the ID.
Maybe your records aren't like that, though, and you keep them in a `9xx` field
or maybe you have some more complex logic (e.g. "look in the `001`, if that's
not there, concatenate the `912$d` and the `915$z`, but remove any character
that aren't digits"). 

If you store them in other fields you can pass in the `-id spec=<value>`
parameter with a value in the form `tag` or `tag$subfield`, and it will extract
IDs based on those, to wit:

    $ java -jar build/lib/dsci.jar -id spec=001 marc/*.mrc

Will give you the default behavior, i.e. it will use whatever it finds in the
001 field, also known as the "Control Number" for the record, as the document
ID.

    $ java -jar build/lib/dsci.jar -id spec='918$a' marc/*.mrc

Will extract the ID from the `918` subfield `a`.  Note you MUST use the quotes
around your specifier if it contains the `$` character.

### But my IDs *are* more complex than that!

Whether it's that the values of your IDs have to be matched case-insensitively
and you want to lowercase them before indexing them, or like the crazy example,
there is an escape hatch; there are two escape hatches, actually.

The simplest form is to write a Groovy closure, put it in a file, and use an option like this:

    $ java -jar build/libs/dsci.jar -id closure=id.groovy marc/*.mrc

And, when the application is run, it will compile whatever's in the `id.groovy`
file to a closure, and call that closure on each record to get the ID.  An
example, matching the 'lower case' idea above, would be:

```groovy
// id.groovy
{ rec -> 
    rec.getVariableField('915')?.getSubfield('a'.charAt(0))?.data?.toLowerCase()
}
```

There's a lot of syntax there, but the idea is to take the `915$a` and
lowercase that.  The `?` in there are for null-safety -- in general you should try to make your closures deal with null values safely, returning null instead of throwing an exception.  There's default handling for this, but its necessarily generic and sort of ugly.

The above is roughly equivalent to:

```groovy
// id.groovy
{ rec ->
    try { 
        rec.getVariableField('915').getSubfield('a'.charAt(0)).data
    } catch (NullPointerException npe) {
      return null
    }
}
```

The argument to the closure will always be an [`org.marc4j.marc.Record`](https://github.com/marc4j/marc4j/blob/master/src/org/marc4j/marc/Record.java)
 object, and will never be `null`.

If you like more type-safety, or don't want to use Groovy (or you do, but want
to use a proper class), you can use the bells-and-whistles form: `-id
class=<class name>`.  I'll just provide hints for this, because it's advanced
usage and to use this you really have to know a bit about how Java/Groovy
projects are structured in order to use it.

#. Create a directory in `src/main/groovy` to hold your class (or put it in
`edu/ncsu/lib/dsci`, your call), 
#. write a class that implements the
`edu.ncsu.lib.dsci.IDExtractor` interface.  Your class must have a no-argument
constructor.
#. Rebuild the project with ./gradlew shadowJar

This is all *advanced usage*, so sorry if your eyes glazed over.  I hope the 'spec' thing covers most cases.

## Files!  JSON!

A run of the indexer will also create a passel of files named `solr_NNNNN.json`
in the `solr` subdirectory of the current directory.  This is sort of as a
backup, but these files are 'concatenated' json that match what's sent to Solr.

Currently there is no way to disable creation of these files.  But they're useful to look at
to help you learn what's going on.

A side effect of the creation of these files is that we have learned all the
fields that would be created, so if you want to take these files and create a
Solr index on a different Solr instance and ingest them manually, you can do
that.  The `add-files.json` file can be POSTed to your Solr instance's Schema
Admin API URL ($SOLR_URL/$COLLECTION_NAME/schema) and it will add all the
fields at one go. 

## A Note About Field Creation

If the Solr server doesn't have a collection named `dsci`, it will be created
on startup.  It will also be recreated if 
you specify the `--reset` option on the command line.  If the collection is
being recreated and the 'solr/add-fields.json` file exists, it will be used to
add all the fields found in the documents up front, which can take a while.

## More Info?

   $ java -jar build/libs/dsci.jar -help

Will tell you a bit about how you can run the application, including specifying the Solr URL if it's different from the default, 
how to reset the collection, etc., and how to just generate the JSON, if you don't want to index directly.

After you've build the application with

    $ ./gradlew shadowJar

You can copy `build/libs/dsci.jar` and run it from wherever with

    $ java -jar dsci.jar [filenames]

### Neat, I Guess, But I Want To ...

Feature requests in the form of issues filed on this repository will be entertained, but please keep in mind:

*D*ead *S*imple Catalog Indexer.  

It's actually complicated enough under the hood.  You should feel free to fork, if it's not working out for you.

MIT License.


