#!/bin/sh

echo "The application will start in ${ECOS_INIT_DELAY:-JHIPSTER_SLEEP}s..." && sleep ${ECOS_INIT_DELAY:-JHIPSTER_SLEEP}
exec java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -cp /app/resources/:/app/classes/:/app/libs/* "ru.citeck.ecos.GatewayApp"  "$@"
