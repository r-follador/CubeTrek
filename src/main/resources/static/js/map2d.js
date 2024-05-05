import {KdTree} from "./kd-tree.js";
import eventBus from './EventBus.js';

export class Map2D {
    constructor(jsonData) {
        this.jsonData = jsonData;
        this.map = new maplibregl.Map({
            container: 'map2d',
            style: 'https://api.maptiler.com/maps/ch-swisstopo-lbm/style.json?key='+maptilerApiKey, // stylesheet location
            //style: 'https://demotiles.maplibre.org/style.json',
            bounds: [[jsonData.properties.bbox.boundingBoxW-0.005,jsonData.properties.bbox.boundingBoxS-0.005],[jsonData.properties.bbox.boundingBoxE+0.005,jsonData.properties.bbox.boundingBoxN+0.005]],
            touchPitch: false,
            maxPitch: 0,
            minZoom: 8,
            maxZoom: 16,
            attributionControl: false
        });

        this.map.on('load', () => {
            this.map.addSource('route', {
                    type: 'geojson',
                    data: jsonData
                }
            );
            this.map.addLayer({
                'id': 'route',
                'type': 'line',
                'source': 'route',
                'layout': {
                    'line-join': 'round',
                    'line-cap': 'round'
                },
                'paint': {
                    'line-color': '#ff8001',
                    'line-width': 3
                }
            });
        });

        this.map.on('mousemove', (e) => {
            this.findClosestTrackpoint(e.lngLat.lat, e.lngLat.lng);
        });

        var el = document.createElement('div');
        el.className = 'marker2d';
        this.marker= new maplibregl.Marker({element: el}).setLngLat([0,0]).addTo(this.map);
        this.kdtree = new KdTree(jsonData.geometry.coordinates[0]);
    }

    findClosestTrackpoint(lat, lon) {
        if (lat == null) {
            eventBus.emit('hideMarkers', {});
            return;
        }

        let closest = this.kdtree.nearest([lon, lat], 1, 0.00001);
        if (closest.length<1) {
            eventBus.emit('hideMarkers', {});
            return;
        }
        var index = this.jsonData.geometry.coordinates[0].indexOf(closest[0][0]);
        eventBus.emit('moveMarkers', {lon: closest[0][0][0], lat: closest[0][0][1], datasIndex: index});
    }

    hideMarker() {
        this.marker.setLngLat([0, 0]);
    }

    moveMarker(lat, lon) {
        this.marker.setLngLat([lon, lat]);
    }
}