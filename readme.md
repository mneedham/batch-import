# Neo4j CSV Batch Importer

The Neo4j Batch Importer is a tool used to load large amounts of data (i.e. millions of nodes and relationships) into a [neo4j](http://www.neo4j.org/) database.

## Installation

Download the [batch-import tool](http://dist.neo4j.org.s3.amazonaws.com/jexp/batch-import/batch-import-full-1.9.jar) into your import project:

    mkdir my-import-project && cd my-import-project
    curl http://dist.neo4j.org.s3.amazonaws.com/jexp/batch-import/batch-import-full-1.9.jar -o batch-import-full-1.9.jar

## Usage

1. Ensure that your neo4j server is stopped (the batch import deals with store files directly so your database will get corrupted if you don't follow this step)
2. Create a nodes.csv file which contains a list of the nodes to import. The first row should be a header which describes the properties of the nodes.

    ```
    echo -e "name:string:users,age,works_on\nb8bd1c77-2732-4687-96b3-fa2c9f25e303,Michael,37,neo4j\nac80bc1f-d8e8-40f0-9b53-af731c635796,Selina,,14" > nodes.csv
    ```

    ```
    cat nodes.csv
    userId:string:users,name,age,works_on
    b8bd1c77-2732-4687-96b3-fa2c9f25e303,Michael,37,neo4j
    ac80bc1f-d8e8-40f0-9b53-af731c635796,Selina,,14
    ```

    We include one field which is a bit different than the others *userId:string:users* for which an index named *users* with key *userId* is created. Each node in the file will have an entry in the index keyed on their *userId* value. This type of field is particularly useful when we want to reference nodes using identifiers from other systems.

3. Create a relationships.csv file which contains relationships between nodes. The first row should be a header and the first 3 fields describe *from node*, *to node* and *relationship type*. Any other fields are treated as properties on the relationship.

    ```
    echo -e "userId:string:users,userId:string:users,type,since,counter:int\nb8bd1c77-2732-4687-96b3-fa2c9f25e303,ac80bc1f-d8e8-40f0-9b53-af731c635796,FATHER_OF,1998-07-10,1" > relationships.csv
    ```

    ```
    cat relationships.csv
    userId:string:users,userId:string:users,type,since,counter:int
    b8bd1c77-2732-4687-96b3-fa2c9f25e303,ac80bc1f-d8e8-40f0-9b53-af731c635796,FATHER_OF,1998-07-10,1
    ````

4. Run the batch importer tool against our store with these nodes and relationships files:

    ````
    java -jar batch-import-full-1.9.jar /path/to/neo4j/data/graph.db nodes.csv relationships.csv
    ````

## For even quicker import



# Other details

## Building Manually

batch-import uses Maven which you can use to generate the latest version:

    git clone git@github.com:jexp/batch-import.git
    cd batch-import
    mvn clean compile assembly:single

That will generate a JAR file in the 'target' directory:

    $ ls -alh target/batch-import-jar-with-dependencies.jar
    -rw-r--r--  1 markneedham  staff    23M 30 Jul 14:49 target/batch-import-jar-with-dependencies.jar

You can then use that JAR as per the [usage section](#usage).

## Examples

There is also a `sample` directory, please run from the main directory `sh sample/import.sh`

### nodes.csv

    name    age works_on
    Michael 37  neo4j
    Selina  14
    Rana    6
    Selma   4

### rels.csv

    start   end type        since   counter:int
    1     2   FATHER_OF 1998-07-10  1
    1     3   FATHER_OF 2007-09-15  2
    1     4   FATHER_OF 2008-05-03  3
    3     4   SISTER_OF 2008-05-03  5
    2     3   SISTER_OF 2007-09-15  7

## File format

* **tab separated** csv files
* Property names in first row.
* If only one file is initially imported, the row number corresponds to the node-id (node 0 is the reference node)
* Property values not listed will not be set on the nodes or relationships.
* Optionally property fields can have a type (defaults to String) indicated with name:type where type is one of (int, long, float, double, boolean, byte, short, char, string). The string value is then converted to that type. Conversion failure will result in abort of the import operation.
* Property fields may also be arrays by adding "_array" to the types above and separating the data with commas.
* for non-ascii characters make sure to add `-Dfile.encoding=UTF-8` to the commandline arguments
* Optionally automatic indexing of properties can be configured with a header like `name:string:users` and a configured index in `batch.properties` like `batch_import.node_index=exact`
  then the property `name` will be indexed in the `users` index for each row with a value there
* multiple files for nodes and rels, comma separated, without spaces like "node1.csv,node2.csv"
* csv files can be zipped individually as *.gz or *.zip

## Parameters

*First parameter* MIGHT be the property-file name then it has to end with .properties, then this file will be used and all other parameters are consumed as usual

*First parameter* is the graph database directory, a new db will be created in the directory except when `batch_import.keep_db=true` is set in `batch.properties`.

*Second parameter* supply a comma separated list of nodes-files

*Third parameter* supply a comma separated list of relationship-files

It is also possible to specifiy those two file-lists in the config:

````
batch_import.nodes_files=nodes1.csv[,nodes2.csv]
batch_import.rels_files=rels1.csv[,rels2.csv]
````

*Fourth parameter set* of 4 values: `node_index users fulltext nodes_index.csv` or more generally: `node-or-rel-index index-name index-type index-file`
This parameter set can be repeatedly used, see below. It is also possible to configure this in the config (`batch.properties`)

````
batch_import.node_index.users=exact
````


## Indexing

### Automatic Indexing

You can automatically index properties of nodes and relationships by adding ":indexName" to the property-header.
Just configure the indexes in `batch.properties` like so:

````
batch_import.node_index.users=exact
````


````
name:string:users    age works_on
Michael 37  neo4j
Selina  14
Rana    6
Selma   4
````

In the relationships-file you can optionally specify that the start and end-node should be looked up from the index in the same way

````
name:string:users	name:string:users	type	    since   counter:int
Michael     Selina   FATHER_OF	1998-07-10  1
Michael     Rana   FATHER_OF 2007-09-15  2
Michael     Selma   FATHER_OF 2008-05-03  3
Rana     Selma   SISTER_OF 2008-05-03  5
Selina     Rana   SISTER_OF 2007-09-15  7
````

### Explicit Indexing

Optionally you can add nodes and relationships to indexes.

Add four arguments per each index to command line:

To create a full text node index called users using nodes_index.csv:

````
node_index users fulltext nodes_index.csv
````

To create an exact relationship index called worked using rels_index.csv:

````
rel_index worked exact rels_index.csv
````

Example command line:

````
java -server -Xmx4G -jar ../batch-import/target/batch-import-jar-with-dependencies.jar neo4j/data/graph.db nodes.csv rels.csv node_index users fulltext nodes_index.csv rel_index worked exact rels_index.csv
````
## Examples

### nodes_index.csv

````
id	name	language
1	Victor Richards	West Frisian
2	Virginia Shaw	Korean
3	Lois Simpson	Belarusian
4	Randy Bishop	Hiri Motu
5	Lori Mendoza	Tok Pisin
````

### rels_index.csv

````
id	property1	property2
0	cwqbnxrv	rpyqdwhk
1	qthnrret	tzjmmhta
2	dtztaqpy	pbmcdqyc
````

## Configuration

The Importer uses a supplied `batch.properties` file to be configured:

#### Memory Mapping I/O Config

Most important is the memory config, you should try to have enough RAM map as much of your store-files to memory as possible.

At least the node-store and large parts of the relationship-store should be mapped. The property- and string-stores are mostly
append only so don't need that much RAM. Below is an example for about 6GB RAM, to leave room for the heap and also OS and OS caches.

````
cache_type=none
use_memory_mapped_buffers=true
# 9 bytes per node
neostore.nodestore.db.mapped_memory=200M
# 33 bytes per relationships
neostore.relationshipstore.db.mapped_memory=3G
# 38 bytes per property
neostore.propertystore.db.mapped_memory=500M
# 60 bytes per long-string block
neostore.propertystore.db.strings.mapped_memory=500M
neostore.propertystore.db.index.keys.mapped_memory=5M
neostore.propertystore.db.index.mapped_memory=5M
````

#### Indexes (experimental)

````
batch_import.node_index.users=exact
batch_import.node_index.articles=fulltext
batch_import.relationship_index.friends=exact
````

#### CSV (experimental)

````
batch_import.csv.quotes=true
batch_import.csv.delim=,
````

##### Index-Cache (experimental)

````
batch_import.mapdb_cache.disable=true
````

##### Keep Database (experimental)

````
batch_import.keep_db=true
````

# Parallel Batch Inserter with Neo4j

Uses the [LMAX Disruptor](http://lmax-exchange.github.com/disruptor/) to parallelize operations during batch-insertion.

## The 6 operations are:

1. property encoding
2. property-record creation
3. relationship-id creation and forward handling of reverse relationship chains
4. writing node-records
5. writing relationship-records
6. writing property-records

## Dependencies:

    (1)<--(2)<--(6)
    (2)<--(5)-->(3)   
    (2)<--(4)-->(3)   

It uses the above dependency setup of disruptor handlers to execute the different concerns in parallel. A ringbuffer of about 2^18 elements is used and a heap size of 5-20G, MMIO configuration within the heap limits.

## Execution:

   MAVEN_OPTS="-Xmx5G -Xms5G -server -d64 -XX:NewRatio=5"  mvn clean test-compile exec:java -Dexec.mainClass=org.neo4j.batchimport.DisruptorTest -Dexec.classpathScope=test

## current limitations, constraints:

* have to know max # of rels per node, properties per node and relationship
* relationships have to be pre-sorted by min(start,end)

## measurements

We successfully imported 2bn nodes (2 properties) and 20bn relationships (1 property) in 11 hours on an EC2 high-IO instance,
with 35 ECU, 60GB RAM, 2TB SSD writing up to 500MB/s, resulting in a store of 1.4 TB. That makes around 500k elements per second.

## future improvements:

* stripe writes across store-files (i.e. strip the relationship-record file over 10 handlers, according to CPUs)
* parallelize writing to dynamic string and arraystore too
* change relationship-record updates for backwards pointers to run in a separate handler that is
  RandomAccessFile-based (or nio2) and just writes the 2 int values directly at file-pos
* add a csv analyser / sorter that
* add support & parallelize index addition
* good support for index based lookup for relationship construction (kv-store, better in-memory structure, e.g. a collection of long[])
* use id-compression internally to save memory in structs (write a CompressedLongArray)
* reuse PropertyBlock, PropertyRecords, RelationshipRecords, NodeRecords, probably subclass them and override getId() etc. or copy the code
  from the Store's to work with interfaces

## Licensing Information

This software is licensed under the [GPLv3](http://www.gnu.org/licenses/gpl-3.0.en.html) for now. 
You can ask [Neo Technology](http://neotechnology.com) about a different licensing agreement.  
