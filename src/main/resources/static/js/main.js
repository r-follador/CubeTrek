import  eventBus from './EventBus.js';
import { settings } from "./utilities.js";
import { Map2D } from "./map2d.js";
import { GraphCube } from "./graph.js";
import { Map3D } from "./map3d.js";


var map2d;
var graph;
var map3d;
let map3dIsUsed = false;

settings();
init();

function init() {

    if (document.getElementById("map3dCanvas"))
        map3dIsUsed = true;

    fetchGeoJSON().then(geojson => {
        map2d = new Map2D(geojson);
        graph = new GraphCube(geojson);
        if (map3dIsUsed)
            map3d = new Map3D(geojson);
        else
            document.getElementById("progressdiv").style.display = "none";
    });
}

function hideMarkers(data) {
    map2d.hideMarker();
    graph.hideMarker();
    if (map3dIsUsed)
        map3d.hideMarker();
}

function moveMarkers(data) {
    map2d.moveMarker(data.lat, data.lon);
    graph.moveMarker(data.datasIndex);
    if (map3dIsUsed)
        map3d.moveMarker(data.lat, data.lon);
}

// Subscribe to the sync events
eventBus.on('hideMarkers', hideMarkers);
eventBus.on('moveMarkers', moveMarkers);

async function fetchGeoJSON() {
    return fetch(sharedObjects.root + "geojson/"+sharedObjects.trackid+".geojson")
        .then(response => {
            if (!response.ok) {
                document.getElementById("errormessage").innerText = "Error Loading Assets; Try again.";
                document.getElementById("errormessage").style.display = "block";
                document.getElementById("progressdiv").style.display = "none";
                console.error(response.statusText);
            }
            return response.json();
        });
}