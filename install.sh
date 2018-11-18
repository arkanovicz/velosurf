#!/bin/sh

mvn -DskipTests install:install-file -Dfile=velosurf-2.4.35.jar -DpomFile=pom.xml
