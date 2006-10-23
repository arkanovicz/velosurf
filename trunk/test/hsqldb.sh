#!/bin/sh

java -classpath lib/hsqldb-1.8.0.5.jar org.hsqldb.Server -address 127.0.0.1 -port 9001 -database.0 mem:test -dbname.0 test

