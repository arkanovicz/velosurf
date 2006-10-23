#!/bin/sh

#java -Djetty.home=. -Djetty.port=8081 -Dorg.mortbay.log.class=WebappLogger -Dvelosurf.test.webapp.log.file=../webapp/WEB-INF/log/error.log -Dvelosurf.test.webapp.log.debug=on -jar lib/start-6.0.1.jar etc/jetty.xml
java -Djetty.home=jetty -Djetty.port=8081 -jar jetty/lib/start-6.0.1.jar jetty/etc/jetty.xml
