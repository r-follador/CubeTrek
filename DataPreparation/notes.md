
##Extract OSM Data

Data download http://download.geofabrik.de/ : PBF files

*Imposm*

Install according to https://imposm.org/docs/imposm3/latest/install.html

```
rm -r /tmp/imposm*
/home/rainer/software/imposm-0.11.1-linux-x86-64/imposm import -config /home/rainer/Software_Dev/IdeaProjects/Cubetrek/DataPreparation/imposm-config.txt -read /home/rainer/Downloads/switzerland-latest.osm.pbf -write -deployproduction -srid 4326

#Remove peaks without name
PGPASSWORD=xxxx psql -U postgres -h localhost -d verticaltrack -c "DELETE FROM osm_peaks WHERE (name = '')"
#replace comma by period for height
#PGPASSWORD=xxxx psql -U postgres -h localhost -d verticaltrack -c "UPDATE osm_peaks SET ele =  REPLACE(ele, ',','.')"
#Change ele from string to numerics
#PGPASSWORD=xxxx psql -U postgres -h localhost -d verticaltrack -c "ALTER TABLE osm_peaks ALTER COLUMN ele TYPE numeric USING NULLIF(ele,'')::numeric"

PGPASSWORD=xxxx psql -U postgres -h localhost -d verticaltrack -c "VACUUM FULL VERBOSE osm_peaks"
```


*Osmium*

`sudo apt-get install osmium-tools`

See manuals:  https://osmcode.org/osmium-tool/manual.html

osmium tags-filter -o peaks.pbf switzerland-latest.osm.pbf n/natural=peak
osmium export peaks.pbf -o peaks.txt


