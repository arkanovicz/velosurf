#!/bin/sh

java -Djetty.home=jetty -Djetty.port=8081 -jar jetty/lib/start-6.0.1.jar jetty/etc/jetty.xml
