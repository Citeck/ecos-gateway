#!/bin/sh

echo "The application will start in ${ECOS_REGISTRY_SLEEP}s..." && sleep ${ECOS_REGISTRY_SLEEP}
exec java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar "${HOME}/app.war" "$@"
