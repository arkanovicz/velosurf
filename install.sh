#!/bin/sh

mvn -DskipTests install:install-file -Dfile=velosurf-2.4.4.jar -DpomFile=pom.xml
