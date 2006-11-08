#!/bin/sh

if [ "$0" == "./demo.sh" ] ; then

  pushd .
  cd ../../build
  ant download
  ant test-download
  ant start-hsqldb
  popd

  if [ ! -e lib/commons-collections-3.2.jar ]; then cp ../../lib/commons-collections-3.2.jar lib; fi
  if [ ! -e lib/commons-lang-2.2.jar ]; then cp ../../lib/commons-lang-2.2.jar lib; fi
  if [ ! -e lib/crimson-1.1.3.jar ]; then cp ../../lib/crimson-1.1.3.jar lib; fi
  if [ ! -e lib/hsqldb-1.8.0.5.jar ]; then cp ../../test/lib/hsqldb-1.8.0.5.jar lib; fi
  if [ ! -e lib/jdom-1.0.jar ]; then cp ../../lib/jdom-1.0.jar lib; fi
  if [ ! -e lib/velocity-1.4.jar ]; then cp ../../lib/velocity-1.4.jar lib; fi
  if [ ! -e lib/oro-2.0.8.jar ]; then
    echo Downloading oro-2.0.8.jar....
    wget http://www.ibiblio.org/maven/oro/jars/oro-2.0.8.jar -O lib/oro-2.0.8.jar;
  fi
 
  cp ../../velosurf-*.jar lib

  OLDCLASSPATH=$CLASSPATH
  for lib in lib/*.jar; do CLASSPATH=$CLASSPATH:$lib; done;
  echo CLASSPATH=$CLASSPATH
  export CLASSPATH=$CLASSPATH
  ant demo
  CLASSPATH=$OLDCLASSPATH
  export CLASSPATH

  pushd .
  cd ../../build
  ant stop-hsqldb
  popd

else
  echo "demo.sh must be launched in its directory!"
fi

