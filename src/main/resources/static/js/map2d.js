import {KdTree} from "./kd-tree.js";
import eventBus from './EventBus.js';

export class Map2D {
    constructor(jsonData) {
        this.jsonData = jsonData;

        this.mapstyle = new Map();
        this.mapstyle.set("standard",'https://api.maptiler.com/maps/ch-swisstopo-lbm/style.json?key='+maptilerApiKey);
        this.mapstyle.set("satellite",'https://api.maptiler.com/maps/satellite/style.json?key='+maptilerApiKey);
        this.mapstyle.set("topo",'https://api.maptiler.com/maps/topo-v2/style.json?key='+maptilerApiKey);
        this.map = new maplibregl.Map({
            container: 'map2d',
            style: this.mapstyle.get("standard"),
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
                    data: this.jsonData
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
        this.kdtree = new KdTree(jsonData.geometry.coordinates);

        if (document.getElementById("map2dBtnradioStandard"))
            document.getElementById("map2dBtnradioStandard").addEventListener('click', () =>  {this.changeMapstyle('standard')});
        if (document.getElementById("map2dBtnradioTopo"))
            document.getElementById("map2dBtnradioTopo").addEventListener('click', () =>  {this.changeMapstyle('topo')});
        if (document.getElementById("map2dBtnradioSat"))
            document.getElementById("map2dBtnradioSat").addEventListener('click', () =>  {this.changeMapstyle('satellite')});

        document.mymap = this.map;
    }

    findClosestTrackpoint(lat, lon) {
        if (lat == null) {
            eventBus.emit('hideMarkers', {});
            return;
        }

        let closest = this.kdtree.nearest([lon, lat], 1, 0.0001);
        if (closest.length<1) {
            eventBus.emit('hideMarkers', {});
            return;
        }
        let outerIndex = 0;
        let innerIndex = 0;
        let absoluteIndex = 0;

        for (; outerIndex < this.jsonData.geometry.coordinates.length; outerIndex++) {
            let index = this.jsonData.geometry.coordinates[outerIndex].indexOf(closest[0][0]);
            if (index !== -1) {
                innerIndex = index;
                absoluteIndex += innerIndex;
                break;
            }
            absoluteIndex += this.jsonData.geometry.coordinates[outerIndex].length;
        }
        eventBus.emit('moveMarkers', {lon: closest[0][0][0], lat: closest[0][0][1], datasIndex: absoluteIndex});
    }

    hideMarker() {
        this.marker.setLngLat([0, 0]);
    }

    moveMarker(lat, lon) {
        this.marker.setLngLat([lon, lat]);
    }

    changeMapstyle(style) {


        let style_source;

        switch(style) {
            case 'standard':
                style_source = this.mapstyle.get("standard");
                break;
            case 'topo':
                style_source = this.mapstyle.get("topo");
                break;
            case 'satellite':
                style_source = this.mapstyle.get("satellite");
                break;
            default:
                style_source = this.mapstyle.get("standard");
        }

        this.map.setStyle(style_source, {
            transformStyle: (previousStyle, nextStyle) => ({
                ...nextStyle,
                sources: {
                    ...nextStyle.sources,
                    'route': previousStyle.sources.route
                },
                layers: [
                    // background layer
                    ...nextStyle.layers,
                    previousStyle.layers.find(obj => obj.id === 'route')
                ]
            })
        });

    }
}