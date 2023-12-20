# Create a development environment for CubeTrek

## 0. Getting Started
Clone the repo from Github onto your dev machine. It should not really matter, but I'm using IntelliJ IDEA as my
prefered IDE.

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
```


