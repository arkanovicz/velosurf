#!/bin/sh

java -Djetty.home=. -Djetty.port=8081 -jar lib/start-6.0.1.jar etc/jetty.xml
