# Create a development environment for CubeTrek

## 0. Getting Started
Clone the repo from Github onto your dev machine. It should not really matter, but I'm using IntelliJ IDEA as my
prefered IDE.

Look at the end of this file for building and running with docker.

## 1. Dependencies

Check out the [build.gradle file](../build.gradle) to get an overview of the dependencies.

There are two jars, which cannot be loaded from Gradle/Maven and you need to be downloaded manually:

- [TopoLibrary](https://github.com/r-follador/TopoLibrary/), get the JAR from [here](https://github.com/r-follador/TopoLibrary/releases/tag/snapshot)
- Garmin FIT SDK, get it [here](https://developer.garmin.com/fit/download/)

## 2. Database

- Install postgresql and [postgis](https://postgis.net/), e.g. `sudo apt-get install postgresql postgresql-contrib postgis postgresql-15-postgis-3`
- Create a database called cubetrek and a user and create the postgis extension in this database, e.g.
```
sudo -i -u postgres
creatdb cubetrek
createuser cubetrek_postgres

psql
ALTER USER postgres PASSWORD 'my-super-secure-password'
grant all privileges on database cubetrek to cubetrek_postgres;

\c cubetrek
CREATE EXTENSION postgis;
\q
```

- Modify following files:

| File                                                   | Modification                             |
| ------------------------------------------------------ | ---------------------------------------- |
| /etc/postgresql/15/main/postgresql.conf                | `listen_addresses = '0.0.0.0'`           |
| /etc/postgresql/15/main/pg_hba.conf (add as last line) | `host all cubetrek_postgres 0.0.0.0/0 md5` |

## 3. DEM data
An HGT file is a Shuttle Radar Topography Mission (SRTM) data file. See [openstreetmap.org/wiki/SRTM](https://openstreetmap.org/wiki/SRTM)
for more infos.

HGT files use a normalized file naming scheme denoting the SW corner (e.g N20E100.hgt contains data from
20°N to 21°N and from 100°E to 101°E.) One file contains an area of 1° x 1°. TopoLibrary can work with either 1
arcsecond (called 1DEM) or 3 arcsecond (called 3DEM) HGT files. 1DEM: 3601x3601 cells (1 arcsec per cell; corresponds to
approx. 30m at the equator, less everywhere else) 3DEM: 1201x1201 cells (3 arcsec per cell; corresponds to approx. 90m at
the equator, less everywhere else)

CubeTrek (using [TopoLibrary](https://github.com/r-follador/TopoLibrary/)) requires access to a folder/directory
containing all HGT files in the correct naming scheme. It picks and reads the required HGT file automatically and returns
a FileNotFoundException if this particular HGT file is missing.

For the development environment I recommend to only download the HGT files matching the geographical location of your
testing GPS tracks.

- 1DEM files: download from https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/?_ga=2.33740263.797586985.1703110939-19353304.1703110938; alternative: https://portal.opentopography.org/raster?opentopoID=OTSRTM.082015.4326.1
- 3DEM files: download from http://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm

## 4. External API keys

- **Most important** is an API key from [Maptiler](https://www.maptiler.com/) which provides the 2D maps and the textures for the 3D visualizations
- [Google map API key](https://developers.google.com/maps) is required for the 3D Replay mode, as well as the [Cesium Ion Access token](https://ion.cesium.com/signin/). It's not required if you don't want to run the Replay mode.
- Cloudflare Turnstyle is used as Captcha alternative for account sign ups. It doesn't really make sense in a dev environment
- Garmin, Polar, Suunto API (for syncing your watch/account with CubeTrek): this won't work in a dev environment, no need to get it


## 5. Spring boot properties files

- Check out the [application-dev.properties](../src/main/resources/application-dev.properties) file
- Minimally you need to change:
  - logging.file.name: where to put the log file on your dev machine
  - cubetrek.hgt.1dem: the location of the HGT files for the 1DEM resolution
  - cubetrek.hgt.3dem: the location of the HGT files for the 1DEM resolution

- You also need to create an application-secret.properties file in src/main/resources/ with following content:
```
spring.mail.username=not_important
spring.mail.password=not_important

maptiler.api.key=***get your own api key***
googlemap.api.key=***get your own api key***
cesium.ion.defaultAccessToken=***get your own api key***

cloudflare.turnstyle.secret=***get your own api key***

garmin.consumer.key=***get your own api key***
garmin.consumer.secret=***get your own api key***

polar.client.id=***get your own api key***
polar.client.secret=***get your own api key***
polar.webhook.signature_secret_key=***get your own api key***
polar.webhook.id=***get your own api key***

suunto.client.id=***get your own api key***
suunto.primary.key=***get your own api key***
suunto.client.secret=***get your own api key***

#Important Dev Postgresql; see point 2. above
spring.datasource.url.dev=jdbc:postgresql://localhost:5432/cubetrek
spring.datasource.username.dev=cubetrek_postgres
spring.datasource.password.dev=my-super-secure-password

thirdparty.clientkey.key=**key used to symmetrically encrypt garmin/polar user access tokens**
```


# Docker

! Disclaimer: this is for local development only, its not for public hosting! Currently it is a proof of concept with known issues. Use at your own risk !

#### Prerequisites:
- install docker (https://www.docker.com/get-started/)
- following is done on unix (Linux or MacOS, not tested on windows but should be similar)
- clone this repository


## 1. building postgres database docker image

- navigate to the `CubeTrek/docker/postgres.database` directory
- edit the `Dockerfile` to modify the Postgres password
```
# build the postgres docker image

cd docker/postgres.database

docker build -t cubetrekdb:local .

# go back to main repo dir
cd ../..
```

## 2. preparing build of cubetrek-dev docker iamge

- download the two external jars, which cannot be loaded from Gradle/Maven and you need to be downloaded manually:

- [TopoLibrary](https://github.com/r-follador/TopoLibrary/), get the JAR from [here](https://github.com/r-follador/TopoLibrary/releases/tag/snapshot)
- Garmin FIT SDK, get it [here](https://developer.garmin.com/fit/download/)

-> unpack the FIT_SDK_xxx and copy the `.../java/fit.jar` to `CubeTrek/docker/externaljars/fit.jar`
-> copy the `TopoLibrary-2.2-SNAPSHOT.jar` to `CubeTrek/docker/externaljars/TopoLibrary-2.2-SNAPSHOT.jar`
(the files must have these exact names!)


### 2.1  
- now edit the file `CubeTrek/src/main/resources/application-dev.properties` (replace content with following content)

```
spring.datasource.url=${spring.datasource.url.dev}
spring.datasource.username=${spring.datasource.username.dev}
spring.datasource.password=${spring.datasource.password.dev}
logging.level.root=INFO
spring.thymeleaf.cache=false
spring.thymeleaf.prefix=file:src/main/resources/templates/
logging.level.org.springframework.security=INFO
logging.level.org.hibernate.SQL=TRACE
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
spring.jpa.show-sql=false

logging.file.name=/logs/cubetrek-logging.log
cubetrek.hgt.1dem=/maps/HGT_1DEM/
cubetrek.hgt.3dem=/maps/HGT/

spring.mail.properties.mail.smtp.from=registration@localhost
spring.mail.host=smtp.localhost
spring.mail.port=587

cubetrek.address=http://localhost:8080

##coche control for web assets: 1 sec
spring.web.resources.cache.cachecontrol.max-age=1
spring.web.resources.cache.cachecontrol.cache-public=true

stripe.controller.enabled=false
stripe.api.key=default_key_value
stripe.price=default_key_value
stripe.endpoint.secret=default_key_value
stripe.endpoint.public=default_key_value

spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### 2.2
- create the file `CubeTrek/src/main/resources/application-secret.properties` with following content:
   - Get a api key for https://maptiler.com and paste it at `THIS_IS_REQUIRED`
   - change the `spring.datasource.password.dev`if you changed the postgres password in the Dockerfile in step 1 accordingly

```
spring.mail.username=not_important
spring.mail.password=not_important

maptiler.api.key=THIS_IS_REQUIRED
googlemap.api.key=***get your own api key***
cesium.ion.defaultAccessToken=***get your own api key***

cloudflare.turnstyle.secret=***get your own api key***

garmin.consumer.key=***get your own api key***
garmin.consumer.secret=***get your own api key***

polar.client.id=***get your own api key***
polar.client.secret=***get your own api key***
polar.webhook.signature_secret_key=***get your own api key***
polar.webhook.id=***get your own api key***

suunto.client.id=***get your own api key***
suunto.primary.key=***get your own api key***
suunto.client.secret=***get your own api key***

#Important Dev Postgresql; see point 2. above
spring.datasource.url.dev=jdbc:postgresql://cubetrekdb:5432/cubetrek
spring.datasource.username.dev=cubetrek_postgres
spring.datasource.password.dev=my-super-secure-password
spring.datasource.driver-class-name=org.postgresql.Driver

thirdparty.clientkey.key=**key used to symmetrically encrypt garmin/polar user access tokens**
```

### 2.3 
- for some reason the `application-secret.properties` doesnt get loaded completly, so the temporary workaround is to copy the secrets content also below the `CubeTrek/src/main/resources/application.properties` content like so (showing the full file) 
- this should be fixed by someone smarter than me, but works for now

```
###Change the profile here
spring.profiles.active=dev,secrets

# ===============================
# = DATA SOURCEconnect
# ===============================
# Set here configurations for the database connection
spring.datasource.driver-class-name=org.postgresql.Driver
# Keep the connection alive if idle for a long time (needed in production)
spring.datasource.tomcat.test-while-idle=true
spring.datasource.tomcat.validation-query=SELECT 1
spring.jpa.open-in-view=false
# ===============================
# = JPA / HIBERNATE
# ===============================
# Hibernate ddl auto (create, create-drop, update): with "create-drop" the database
# schema will be automatically created afresh for every start of application
spring.jpa.hibernate.ddl-auto=update


logging.pattern.dateformat=yyyy-MM-dd HH:mm:ss.SSS,Europe/Zurich


###JavaMailSender

#spring.mail.username=xxx
#spring.mail.password=xxx
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.ssl.protocols=TLSv1.2

#Execution Pool for Async Events
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=20
spring.task.execution.pool.queue-capacity=1000
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=10s
spring.task.execution.pool.allow-core-thread-timeout=false
spring.task.execution.thread-name-prefix=cubetrek-task-exec-


# Max file size.
spring.servlet.multipart.max-file-size=100MB
# Max request size.
spring.servlet.multipart.max-request-size=108MB
server.error.whitelabel.enabled=false


stripe.api.key.test=default_key_value
stripe.controller.enabled=false


# added secrets

spring.mail.username=not_important
spring.mail.password=not_important

maptiler.api.key=THIS_IS_REQUIRED
googlemap.api.key=***get your own api key***
cesium.ion.defaultAccessToken=***get your own api key***

cloudflare.turnstyle.secret=***get your own api key***

garmin.consumer.key=***get your own api key***
garmin.consumer.secret=***get your own api key***

polar.client.id=***get your own api key***
polar.client.secret=***get your own api key***
polar.webhook.signature_secret_key=***get your own api key***
polar.webhook.id=***get your own api key***

suunto.client.id=***get your own api key***
suunto.primary.key=***get your own api key***
suunto.client.secret=***get your own api key***

#Important Dev Postgresql; see point 2. above
spring.datasource.url.dev=jdbc:postgresql://cubetrekdb:5432/cubetrek
spring.datasource.username.dev=cubetrek_postgres
spring.datasource.password.dev=my-super-secure-password
spring.datasource.driver-class-name=org.postgresql.Driver

thirdparty.clientkey.key=**key used to symmetrically encrypt garmin/polar user access tokens**
```


### 2.4
- replace the file `CubeTrek/src/main/resources/templates/registration.html` with `CubeTrek/docker/modified.files/registration.html` to disable cloudflare captcha at registration  (version with disabled cloudflare captcha)

### 2.5 
- replace the file `CubeTrek/src/main/java/com/cubetrek/registration/RegistrationController.java` with `CubeTrek/docker/modified.files/RegistrationController.java` 
(version with disabled cloudflare captcha)


### 2.6
- edit the file `build.gradle` like following:

### replace:
```
    implementation files('/home/rainer/Software_Dev/IdeaProjects/TopoLibrary/build/libs/TopoLibrary-2.2-SNAPSHOT.jar')
    implementation files('/home/rainer/Software_Dev/IdeaProjects/TopoLibrary/libs/fit.jar')
```

### with:
```
    implementation files('./libs/TopoLibrary-2.2-SNAPSHOT.jar')
    implementation files('./libs/fit.jar')
    implementation 'org.postgresql:postgresql:42.6.0'
```

### 2.7 Download HGT files

For the development environment I recommend to only download the HGT files matching the geographical location of your
testing GPS tracks.

- 1DEM files: download from https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/?_ga=2.33740263.797586985.1703110939-19353304.1703110938; alternative: https://portal.opentopography.org/raster?opentopoID=OTSRTM.082015.4326.1
- 3DEM files: download from http://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm

-> place the downloaded files into the directories `CubeTrek/docker/volumes/cubetrek/maps/HGT` and `CubeTrek/docker/volumes/cubetrek/maps/HGT_1DEM`


---

## 3. building cubetrek docker image

- note: make sure you are in the `CubeTrek` parent/root directory
- note: arm64 and amd64 builds are working afaik
```
docker build -f docker/Dockerfile -t cubetrek-dev:local .
```


## Starting the database and webserver

finally, you almost made it, lets see if you missed something... :)

### start database container
```
docker compose -f docker/docker-compose.yml up -d cubetrekdb
```

### check logs of database for successfull init
```
docker logs cubetrekdb
```

### start cubetrek container
```
docker compose -f docker/docker-compose.yml up -d
```

### check logs of cubetrek container
```
docker logs -f cubetrek-dev
```


## stop and remove containers (optional)
```
docker compose -f docker/docker-compose.yml down
```

## Finally use it!

- open browser
- navigate to http://localhost:8080

- just register and login (you dont need to verify your email)

- remember to donate or subscribe via the official site at https://cubetrek.com to keep this awesome project alive