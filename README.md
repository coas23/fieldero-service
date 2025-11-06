# Fieldero-Service

This is the REST backend (Java8-Spring Boot) of the web
application.
We would be very happy to have new contributors join us.
**And please star the repo**.

## How to run locally ?

Set the environment variables not starting with `REACT_APP` from [here](../README.MD#set-environment-variables)

Without docker, you should first install and use JDK 8 then create a Postgres database. After that go
to [src/main/resources/application-dev.yml](src/main/resources/application-dev.yml), change the url, username and
password.

```shell
set -a
source .env
set +a
env | grep DB_
JAVA_HOME=$(/usr/libexec/java_home -v 1.8) \
PATH="$JAVA_HOME/bin:$PATH" \
mvn spring-boot:run
```
## Expo Push Notifications

When using Expo project-bound push tokens the backend must supply an Expo Push Access Token.
Set `EXPO_ACCESS_TOKEN` (env or JVM property) to a valid token obtained via `npx expo push:access-token`.
