//Tons of Globals, a sign of shitty programming
var maxAltitude = 5000;
var minAltitude = 0;
var width_x = 1000;
var depth_y = 1000;
var zoomfactor = 12;
var meshes = [];
var jsonData;
const zscaling = 1.5;
const coordinateSystem = [];
const root = "../api/";
let metric = true;
const miles_per_km = 0.621371;
const feet_per_m = 3.28084;
var kdtree;
var graph;

var delayCreateScene = function() {
    var scene = new BABYLON.Scene(engine);
    var gl = new BABYLON.GlowLayer("glow", scene);
    var masterMesh;
    var particleSystem = new BABYLON.ParticleSystem("particles", 2000, scene);

    Promise.all([
        BABYLON.SceneLoader.ImportMeshAsync(null, root + "gltf/", trackid+".gltf", scene, function (evt) {
            var loadedPercent = 0;
            if (evt.lengthComputable) {
                loadedPercent = (evt.loaded * 100 / evt.total).toFixed();
            } else {
                var dlCount = evt.loaded / (1024 * 1024);
                loadedPercent = Math.floor(dlCount * 100.0) / 100.0;
            }
            document.getElementById("progressbar").setAttribute("style","width: "+loadedPercent+"%")
        }),
        getJSON(root + "geojson/"+trackid+".geojson")
    ]).catch(error => {
        document.getElementById("errormessage").innerText = "Error Loading Assets; Try again.";
        document.getElementById("errormessage").style.display = "block";
        document.getElementById("progressdiv").style.display = "none";
        console.error(error);
    }).then((values) => {
        let result = values[0]; //returned from ImportMeshAsync
        result.meshes.forEach(parentMesh => {
            parentMesh.getChildren().forEach(mesh => {
                mesh.scaling.z = zscaling;
                var normals = [];
                BABYLON.VertexData.ComputeNormals(mesh.getVerticesData(BABYLON.VertexBuffer.PositionKind), mesh.getIndices(), normals);
                mesh.setVerticesData(BABYLON.VertexBuffer.NormalKind, normals);
                if (mesh.getVerticesData(BABYLON.VertexBuffer.UVKind)) { //ignore the
                    meshes.push(mesh);
                }
                //mesh.forceSharedVertices();
            })
        })
        jsonData = values[1]; //returned from getJSON
        kdtree = new kdTree(jsonData.geometry.coordinates[0]);
        prepareMap2d(jsonData);
        coordinateSystem.centerLat = jsonData.properties.bbox.centerLat;
        coordinateSystem.centerLon = jsonData.properties.bbox.centerLon;
        coordinateSystem.metersPerDegreeLat = jsonData.properties.bbox.metersPerDegreeLat;
        coordinateSystem.metersPerDegreeLon = jsonData.properties.bbox.metersPerDegreeLon;
        width_y = (jsonData.properties.bbox.boundingBoxN-jsonData.properties.bbox.boundingBoxS)*jsonData.properties.bbox.metersPerDegreeLat;
        width_x = (jsonData.properties.bbox.boundingBoxE-jsonData.properties.bbox.boundingBoxW)*jsonData.properties.bbox.metersPerDegreeLon;
        zoomfactor = jsonData.properties.tileBBoxes[0].tile_zoom;

        var layer = new BABYLON.Layer('','../assets/bkgrd.png', scene, true);
        scene.clearColor = new BABYLON.Color3.FromHexString("#2e3c4d");

        var camera = new BABYLON.ArcRotateCamera("camera", Math.PI/4, Math.PI/4, 5000, new BABYLON.Vector3(0,0,1500), scene);
        camera.upVector = new BABYLON.Vector3(0, 0, 1);
        camera.zoomOn(masterMesh);
        camera.maxZ = 100000;
        camera.upperBetaLimit = Math.PI/2;
        camera.upperRadiusLimit = Math.min(100000, 10000*Math.pow(2,14-zoomfactor));
        camera.lowerRadiusLimit = 100;
        camera.wheelPrecision = 0.1; //Mouse wheel speed
        camera.zoomToMouseLocation = true;
        camera.panningSensibility = 2/Math.pow(2,14-zoomfactor);
        camera.multiTouchPanAndZoom = true;
        camera.useNaturalPinchZoom = true;

        camera.attachControl(canvas, true, true);
        camera.useAutoRotationBehavior = true;
        camera.autoRotationBehavior.idleRotationWaitTime = 60 * 1000;

        //showWorldAxis(5000);
        var helperLight = new BABYLON.HemisphericLight("DirectionalLightAbove", new BABYLON.Vector3(0,0, -1), scene);
        helperLight.intensity=0.3;
        var dirLight = new BABYLON.DirectionalLight("DirectionalLightSide", new BABYLON.Vector3(1, 1, 0), scene);
        dirLight.intensity = 2;
        scene.ambientColor = new BABYLON.Color3(1, 1, 1);

        camera.onViewMatrixChangedObservable.add(function(c) {
            c.target.z = Math.min(Math.max(c.target.z, (minAltitude-200)*zscaling), (maxAltitude+200)*zscaling);
            dirLight.direction = new BABYLON.Vector3(-1 * c.position.y, c.position.x, 0);
        })

        particleSystem.particleTexture = new BABYLON.Texture("../assets/flare.png", scene);
        particleSystem.emitter = new BABYLON.Vector3(0, 0, 0);
        particleSystem.minEmitPower = 100*Math.pow(2,14-zoomfactor);
        particleSystem.maxEmitPower = 300*Math.pow(2,14-zoomfactor);
        particleSystem.direction1 = new BABYLON.Vector3(0, 0, 1);
        particleSystem.direction2 = new BABYLON.Vector3(0, 0, 1);
        particleSystem.emitRate = 100;
        particleSystem.minLifeTime = 1;
        particleSystem.maxLifeTime = 1.5;
        particleSystem.color1 = new BABYLON.Color4(0.7, 0.8, 1.0, 1.0);
        particleSystem.color2 = new BABYLON.Color4(1.0, 0.5, 0.0, 1.0);
        particleSystem.colorDead = new BABYLON.Color4(0, 0, 0.2, 1.0);
        particleSystem.minSize = 10*Math.pow(2,14-zoomfactor);
        particleSystem.maxSize = 150*Math.pow(2,14-zoomfactor);
        particleSystem.emitter.z = -2000;

        const textureSize = 512;
        let textureContexts = [];
        for (let i =0; i < meshes.length; i++ ) {
            var myDynamicTexture = new BABYLON.DynamicTexture("bla"+i, textureSize, scene);
            var textureContext = myDynamicTexture.getContext();
            textureContexts.push(textureContext);
            meshes[i].material.emissiveTexture = myDynamicTexture; //pBR texture
            meshes[i].material.emissiveColor = new BABYLON.Color3(1, 1, 1);
            //textureContext.strokeStyle = "#2a84de";
            textureContext.strokeStyle = "#ff8001";
            textureContext.lineWidth = 3;
            textureContext.beginPath();
        }

        let tileIndex = -1;
        let px_perDegree_lat = 0;
        let px_perDegree_lon = 0;
        for (let i=0; i < jsonData.geometry.coordinates.length; i++) {
            let track = jsonData.geometry.coordinates[i];
            for (let j = 1; j < track.length; j++) {

                if (tileIndex===-1 || !(jsonData.properties.tileBBoxes[tileIndex].n_Bound > track[j][1] && jsonData.properties.tileBBoxes[tileIndex].s_Bound < track[j][1] && jsonData.properties.tileBBoxes[tileIndex].w_Bound < track[j][0] && jsonData.properties.tileBBoxes[tileIndex].e_Bound > track[j][0])) {
                    if (tileIndex !== -1) {//before tile switch; draw into void of old tile to keep lines correct
                        let x=(track[j][0]-jsonData.properties.tileBBoxes[tileIndex].w_Bound)*px_perDegree_lon;
                        let y=(track[j][1]-jsonData.properties.tileBBoxes[tileIndex].s_Bound)*px_perDegree_lat;
                        textureContexts[tileIndex].lineTo(x, y);
                    }
                    for (let k = 0; k < jsonData.properties.tileBBoxes.length; k++) {
                        if (jsonData.properties.tileBBoxes[k].n_Bound > track[j][1] && jsonData.properties.tileBBoxes[k].s_Bound < track[j][1] && jsonData.properties.tileBBoxes[k].w_Bound < track[j][0] && jsonData.properties.tileBBoxes[k].e_Bound > track[j][0]) {
                            tileIndex = k;
                            px_perDegree_lon = textureSize / jsonData.properties.tileBBoxes[tileIndex].widthLonDegree;
                            px_perDegree_lat = textureSize / jsonData.properties.tileBBoxes[tileIndex].widthLatDegree;

                            //after tile switch; move to previous point into void of new tile to keep lines correct
                            let x=(track[j-1][0]-jsonData.properties.tileBBoxes[tileIndex].w_Bound)*px_perDegree_lon;
                            let y=(track[j-1][1]-jsonData.properties.tileBBoxes[tileIndex].s_Bound)*px_perDegree_lat;
                            textureContexts[tileIndex].moveTo(x, y);
                            break;
                        }
                    }
                }

                let x=(track[j][0]-jsonData.properties.tileBBoxes[tileIndex].w_Bound)*px_perDegree_lon;
                let y=(track[j][1]-jsonData.properties.tileBBoxes[tileIndex].s_Bound)*px_perDegree_lat;
                textureContexts[tileIndex].lineTo(x, y);
            }
        }

        for (let i =0; i < meshes.length; i++ ) {
            textureContexts[i].stroke();
            meshes[i].material.emissiveTexture.update();
        }

        scene.onPointerObservable.add((pointerInfo) => {
            if (pointerInfo.type === BABYLON.PointerEventTypes.POINTERMOVE) {
                var hit = scene.pick(scene.pointerX, scene.pointerY)
                if (hit.hit) {
                    var lat = (hit.pickedPoint._y/coordinateSystem.metersPerDegreeLat)+coordinateSystem.centerLat;
                    var lon = coordinateSystem.centerLon-(hit.pickedPoint._x/coordinateSystem.metersPerDegreeLon);
                    findLine(lat,lon);
                } else {
                    findLine(null);
                }
            }
        });

        prepareGraph(jsonData);
        document.getElementById("progressdiv").style.display = "none";
        var n_bound = jsonData.properties.tileBBoxes[0].n_Bound;
        var w_bound = jsonData.properties.tileBBoxes[0].w_Bound;
        var e_bound = jsonData.properties.tileBBoxes[jsonData.properties.tileBBoxes.length-1].e_Bound;
        var s_bound = jsonData.properties.tileBBoxes[jsonData.properties.tileBBoxes.length-1].s_Bound;
        return getJSON(root+"peaks/nbound="+n_bound+"&sbound="+s_bound+"&wbound="+w_bound+"&ebound="+e_bound);
    }).then(function(result) { //peak list
        var font_size = 48;
        var font = font_size + "px Helvetica";
        var planeHeight = 100;
        var DTHeight = 1.5 * font_size;
        var ratio = planeHeight/DTHeight;
        var temp = new BABYLON.DynamicTexture("DynamicTexture", 64, scene);
        var tmpctx = temp.getContext();
        tmpctx.font = font;
        const f = new BABYLON.Vector4(0,0, 1, 1);
        const b = new BABYLON.Vector4(1,0, 0, 1);

        var mat2 = new BABYLON.StandardMaterial("mat", scene);
        mat2.ambientColor = new BABYLON.Color3(1, 1, 1);
        mat2.alpha = 0.7;
        for (let i=0; i < result.peaklist.length && i < 100; i++) {
            var text = result.peaklist[i].name;
            var DTWidth = tmpctx.measureText(text).width + 8;
            var planeWidth = DTWidth * ratio;
            var dynamicTexture = new BABYLON.DynamicTexture("DynamicTexture", {width:DTWidth, height:DTHeight}, scene, false);
            var mat = new BABYLON.StandardMaterial("mat", scene);
            mat.ambientTexture = dynamicTexture;
            mat.ambientColor = new BABYLON.Color3(1, 1, 1);
            mat.alpha = 0.7;
            dynamicTexture.drawText(text, null, null, font, "#000000", "#ffffff", true);
            var plane = BABYLON.MeshBuilder.CreatePlane("plane", {width:planeWidth, height:planeHeight, frontUVs: f, backUVs: b, sideOrientation: BABYLON.Mesh.DOUBLESIDE}, scene);
            plane.material = mat;

            var pos = getPosition(result.peaklist[i].lat, result.peaklist[i].lon);
            plane.position.x = pos.x;
            plane.position.y = pos.y;
            plane.position.z = pos.z+(planeHeight)+70;
            plane.rotation.x = Math.PI/2;
            plane.isPickable = false;
            var plane2 = BABYLON.MeshBuilder.CreatePlane("plane2", {width:20, height:140, sideOrientation: BABYLON.Mesh.DOUBLESIDE}, scene);
            plane2.position.x = pos.x;
            plane2.position.y = pos.y;
            plane2.position.z = pos.z+70;
            plane2.rotation.x = Math.PI/2;
            plane2.isPickable = false;
            plane2.material = mat2;
        }

    });

    settings();
    return scene;
};
var marker;

function prepareMap2d(jsonData) {
    var map = new maplibregl.Map({
        container: 'map2d',
        style: 'https://api.maptiler.com/maps/ch-swisstopo-lbm/style.json?key=Nq5vDCKAnSrurDLNgtSI', // stylesheet location
        bounds: [[jsonData.properties.bbox.boundingBoxW-0.005,jsonData.properties.bbox.boundingBoxS-0.005],[jsonData.properties.bbox.boundingBoxE+0.005,jsonData.properties.bbox.boundingBoxN+0.005]],
        touchPitch: false,
        maxPitch: 0,
        minZoom: 8,
        maxZoom: 16,
        attributionControl: false
    });

    map.on('load', function () {
        map.addSource('route', {
                type: 'geojson',
                data: jsonData
            }
        );
        map.addLayer({
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

    map.on('mousemove', function (e) {
        findLine(e.lngLat.lat, e.lngLat.lng);
    });

    var el = document.createElement('div');
    el.className = 'marker2d';
    marker= new maplibregl.Marker(el).setLngLat([0,0]).addTo(map);
    map.on('load')
}

const datas = [];

class GraphAxis {
    // Create new instances of the same class as static attributes
    static Elevation = new GraphAxis("elevation");
    static ElapsedTime = new GraphAxis("elapsed_time");
    static MovingTime = new GraphAxis("moving_time");
    static Distance = new GraphAxis("distance");
    static HorizontalSpeed = new GraphAxis("horizontal_speed");
    static VerticalSpeed = new GraphAxis("vertical_speed");

    constructor(name) {
        this.name = name
    }
}

let graphXAxis = GraphAxis.Distance;
let graphYAxis = GraphAxis.Elevation;

function changeGraphX(type) {
    graphXAxis = type;
    graph = new drawGraph(graphYAxis, graphXAxis);
}

function changeGraphY(type) {
    graphYAxis = type;
    graph = new drawGraph(graphYAxis, graphXAxis);
}

let horizontal_average;
let vertical_down_average;
let vertical_up_average;

function prepareGraph(jsonData) {
    var parseDate = d3.timeParse("%Y-%m-%dT%H:%M:%S%Z");
    let previousTime = parseDate(jsonData.geometry.coordinates[0][0][3]);
    let previousDistance = jsonData.geometry.coordinates[0][0][4];
    let previousElevation = jsonData.geometry.coordinates[0][0][2];
    let movingTime = 0;

    let verticalDistSumUp = 0;
    let verticalTimeSumUp = 0;
    let verticalDistSumDown = 0;
    let verticalTimeSumDown = 0;

    for (var i=0; i<jsonData.geometry.coordinates[0].length; i++) {
        let time = parseDate(jsonData.geometry.coordinates[0][i][3]);
        let elevation = jsonData.geometry.coordinates[0][i][2];
        let distance = jsonData.geometry.coordinates[0][i][4];

        let time_diff_hour = (time-previousTime)/3600000;
        let verticalSpeed_m_per_h = (elevation-previousElevation)/time_diff_hour;
        if (isNaN(verticalSpeed_m_per_h))
            verticalSpeed_m_per_h = 0;
        let horizontalSpeed_km_per_h = ((distance-previousDistance)/1000)/(time_diff_hour);
        if (isNaN(horizontalSpeed_km_per_h))
            horizontalSpeed_km_per_h = 0;

        if ((time-previousTime)/1000 < (60) || Math.abs(elevation-previousElevation)>3) {
            movingTime += (time - previousTime);
            if (elevation-previousElevation > 0) {
                verticalDistSumUp += (elevation-previousElevation);
                verticalTimeSumUp += (time - previousTime);
            } else if (elevation-previousElevation < 0) {
                verticalDistSumDown += (previousElevation-elevation);
                verticalTimeSumDown += (time - previousTime);
            }
        }

        previousTime = time;
        previousElevation = elevation;
        previousDistance = distance;
        datas.push({'time' : time, 'altitude' : elevation, 'distance' : distance, 'vertical_speed' : verticalSpeed_m_per_h, 'horizontal_speed' : horizontalSpeed_km_per_h, 'moving_time' : movingTime});
    }

    horizontal_average = ((previousDistance)/(movingTime/3600));
    vertical_down_average = (verticalDistSumDown)/(verticalTimeSumDown/3600000);
    vertical_up_average = (verticalDistSumUp)/(verticalTimeSumUp/3600000);
    document.getElementById("value_horizontal_average").innerText=(horizontal_average*(metric?1:miles_per_km)).toFixed(1)+(metric?" km/h":" mph");
    document.getElementById("value_vertical_down_average").innerText=(vertical_down_average*(metric?1:feet_per_m)).toFixed(1)+(metric?" m/h":" ft/h");
    document.getElementById("value_vertical_up_average").innerText=(vertical_up_average*(metric?1:feet_per_m)).toFixed(1)+(metric?" m/h":" ft/h");

    maxAltitude = d3.max(datas, function(d) {return d.altitude});
    minAltitude = d3.min(datas, function(d) {return d.altitude});

    graph = new drawGraph(graphYAxis, graphXAxis);

    let movingTimeMinutes = movingTime/60000;
    document.getElementById("movingtime").innerText = Math.floor(movingTimeMinutes/60)+":"+Math.floor(movingTimeMinutes%60).toString().padStart(2,"0");
}

function drawGraph(yaxis, xaxis) {
    this.margingraph = {top: 10, right: 5, bottom: 25, left: 40};

    this.width = document.getElementById('graph').clientWidth-this.margingraph.left-this.margingraph.right,
        this.height = document.getElementById('graph').clientHeight-this.margingraph.top-this.margingraph.bottom;

// append the svg object to the body of the page
    d3.select("#graph").select("svg").remove(); //clear if exists already
    this.svg = d3.select("#graph")
        .append("svg")
        .attr("width", this.width + this.margingraph.left + this.margingraph.right)
        .attr("height", this.height + this.margingraph.top + this.margingraph.bottom)
        .append("g")
        .attr("transform",
            "translate(" + this.margingraph.left + "," + this.margingraph.top + ")");

    this.yScale;
    this.xScale;
    this.functionpath = d3.line();

    this.regressionGenerator = d3.regressionLoess().bandwidth(0.03);

    switch (yaxis) {
        case GraphAxis.Elevation:
            document.getElementById("dropdowngraphyaxis").innerText = "Elevation";
            this.yScale = d3.scaleLinear().domain(d3.extent(datas, function(d) { return (d.altitude*(metric?1:feet_per_m)); }));
            this.functionpath.y((d) => { return this.yScale(d.altitude*(metric?1:feet_per_m)) });
            this.regressionGenerator.y(d => d.altitude*(metric?1:feet_per_m));
            break;
        case GraphAxis.Distance:
            document.getElementById("dropdowngraphyaxis").innerText = "Distance";
            this.yScale = d3.scaleLinear().domain(d3.extent(datas, function(d) { return (d.distance*(metric?1/1000:miles_per_km/1000)); }));
            this.functionpath.y((d) => {return this.yScale(d.distance*(metric?1/1000:miles_per_km/1000)) });
            this.regressionGenerator.y(d => d.distance*(metric?1/1000:miles_per_km/1000));
            break;
        case GraphAxis.VerticalSpeed:
            document.getElementById("dropdowngraphyaxis").innerText = "Vertical Speed";
            this.yScale = d3.scaleLinear().domain(d3.extent(datas, function(d) { return (d.vertical_speed*(metric?1:feet_per_m)); }));
            this.functionpath.y((d) => {return this.yScale(d.vertical_speed*(metric?1:feet_per_m)) });
            this.regressionGenerator.y(d => d.vertical_speed*(metric?1:feet_per_m));
            break;
        case GraphAxis.HorizontalSpeed:
            document.getElementById("dropdowngraphyaxis").innerText = "Horizontal Speed";
            this.yScale = d3.scaleLinear().domain(d3.extent(datas, function(d) { return (d.horizontal_speed*(metric?1:miles_per_km)); }));
            this.functionpath.y((d) => { return this.yScale(d.horizontal_speed*(metric?1:miles_per_km)) });
            this.regressionGenerator.y(d => d.horizontal_speed*(metric?1:miles_per_km));
            break;
        default:
            break;
    }

    switch (xaxis) {
        case GraphAxis.ElapsedTime:
            document.getElementById("dropdowngraphxaxis").innerText = "Elapsed time";
            this.xScale = d3.scaleTime().domain(d3.extent(datas, function(d) { return d.time; }));
            this.functionpath.x((d) => { return this.xScale(d.time) });
            this.regressionGenerator.x(d => d.time);
            break;
        case GraphAxis.Distance:
            document.getElementById("dropdowngraphxaxis").innerText = "Distance";
            this.xScale = d3.scaleLinear().domain(d3.extent(datas, function(d) { return (d.distance*(metric?1/1000:miles_per_km/1000)); }));
            this.functionpath.x((d) => { return this.xScale(d.distance*(metric?1/1000:miles_per_km/1000)) });
            this.regressionGenerator.x(d => d.distance*(metric?1/1000:miles_per_km/1000));
            break;
        case GraphAxis.MovingTime:
            document.getElementById("dropdowngraphxaxis").innerText = "Moving Time";
            this.xScale = d3.scaleTime().domain(d3.extent(datas, function(d) { return d.moving_time; }));
            this.functionpath.x((d) => { return this.xScale(d.moving_time) });
            this.regressionGenerator.x(d => d.moving_time);
            break;
        default:
            break
    }

    this.xAxisLabel = d3.axisBottom(this.xScale);
    this.yAxisLabel = d3.axisLeft(this.yScale);

    this.yScale.range([this.height,0]);
    this.xScale.range([0, this.width]);

    if (xaxis === GraphAxis.MovingTime) {
        this.xAxisLabel.tickFormat(d3.utcFormat("%H:%M"));
    }

    this.svg.append("g")
        .attr("transform", "translate(0," + this.height + ")")
        .attr("class","myXaxis")
        .call(this.xAxisLabel);

    this.svg.append("g")
        .attr("class","myYaxis")
        .call(this.yAxisLabel);

    this.svg.append("path")
        .datum(datas)
        .attr("fill", "none")
        .attr("stroke", "#ff8001")
        .attr("stroke-width", 2)
        .attr("class", "datapath")
        .attr("d", this.functionpath)

    if (yaxis === GraphAxis.VerticalSpeed) {
        this.areaUp = d3.area()
            .y0(this.yScale(0))
            .y1((d) => { if (d.vertical_speed>0) {return this.yScale(d.vertical_speed*(metric?1:feet_per_m))}else{return this.yScale(0);}});

        this.areaDown = d3.area()
            .y0(this.yScale(0))
            .y1((d) => { if (d.vertical_speed<0) {return this.yScale(d.vertical_speed*(metric?1:feet_per_m))}else{return this.yScale(0);}});


        switch (xaxis) {
            case GraphAxis.MovingTime:
                this.areaUp.x((d) => { return this.xScale(d.moving_time); })
                this.areaDown.x((d) => { return this.xScale(d.moving_time); })
                break;
            case GraphAxis.ElapsedTime:
                this.areaUp.x((d) => { return this.xScale(d.time); })
                this.areaDown.x((d) => { return this.xScale(d.time); })
                break;
            case GraphAxis.Distance:
                this.areaUp.x((d) => { return this.xScale(d.distance*(metric?1/1000:miles_per_km/1000)); })
                this.areaDown.x((d) => { return this.xScale(d.distance*(metric?1/1000:miles_per_km/1000)); })
                break;
        }

        this.svg.append("path")
            .datum(datas)
            .attr("class","areaUp")
            .attr("fill", "#ffd3fe")
            .attr("d", this.areaUp)

        this.svg.append("path")
            .datum(datas)
            .attr("class","areaUp")
            .attr("fill", "#caf6b9")
            .attr("d", this.areaDown)
    }

    if (yaxis === GraphAxis.HorizontalSpeed || yaxis === GraphAxis.VerticalSpeed) {
        this.regressionpath = d3.line()
            .x(d => this.xScale(d[0]))
            .y(d => this.yScale(d[1]));

        d3.select(".datapath")
            .attr("stroke", "rgb(126,126,126)")
            .attr("stroke-width", 1);

        this.svg.append("path")
            .datum(this.regressionGenerator(datas))
            .attr("class","graphregression")
            .attr("fill", "none")
            .attr("stroke", "#ff8001")
            .attr("stroke-width", 2)
            .attr("d", this.regressionpath)
    }

    // This allows to find the closest X index of the mouse:
    this.bisect;
    switch (xaxis) {
        case GraphAxis.MovingTime:
            this.bisect = d3.bisector(function(d) { return d.moving_time; }).left;
            break;
        case GraphAxis.ElapsedTime:
            this.bisect = d3.bisector(function(d) { return d.time; }).left;
            break;
        case GraphAxis.Distance:
            this.bisect = d3.bisector(function(d) { return (d.distance*(metric?1/1000:miles_per_km/1000)); }).left;
            break;
    }
    // Create the circle that travels along the curve of chart
    this.focus = this.svg
        .append('g')
        .append('circle')
        .style("fill", "rgba(68,146,220,0.42)")
        .attr("stroke", "none")
        .attr('r', 8.5)
        .style("opacity", 0)

    // Create the text that travels along the curve of chart
    this.xfocusText = this.svg
        .append('g')
        .append('text')
        .style("opacity", 0)
        .attr("text-anchor", "left")
        .attr("alignment-baseline", "middle")
    this.yfocusText = this.svg
        .append('g')
        .append('text')
        .style("opacity", 0)
        .attr("text-anchor", "left")
        .attr("alignment-baseline", "middle")

    // What happens when the mouse move -> show the annotations at the right positions.

    var that = this;

    this.showGraphMarker = function(){
        that.focus.style("opacity", 1)
        that.xfocusText.style("opacity",1)
        that.yfocusText.style("opacity",1)
    }

    this.mouseover = function(){
        that.showGraphMarker();
    }

    this.mousemove = function() {
        // recover coordinate we need
        var x0 = that.xScale.invert(d3.pointer(event, this)[0]);
        var i = that.bisect(datas, x0, 1);
        selectedData = datas[i]
        if (selectedData=== undefined)
            return;

        that.moveGraphMarker(selectedData);
        moveMapMarker(jsonData.geometry.coordinates[0][i][1], jsonData.geometry.coordinates[0][i][0]);
    }

    this.moveGraphMarker = function(selectedData) {
        this.xtext;
        this.ytext
        this.xdata;
        this.ydata;

        switch (xaxis) {
            case GraphAxis.MovingTime:
                this.xdata = selectedData.moving_time;
                this.xtext = d3.utcFormat("%H:%M")(that.xdata) + " h";
                break;
            case GraphAxis.ElapsedTime:
                this.xdata = selectedData.time;
                this.xtext = d3.timeFormat("%H:%M")(that.xdata);
                break;
            case GraphAxis.Distance:
                this.xdata = (selectedData.distance*(metric?1/1000:miles_per_km/1000));
                this.xtext = (that.xdata).toFixed(2) + (metric?" km":" mi");
                break;
        }

        switch (yaxis) {
            case GraphAxis.Distance:
                this.ydata = (selectedData.distance*(metric?1/1000:miles_per_km/1000));
                this.ytext = (that.ydata).toFixed(2) + (metric?" km":" mi");
                break;
            case GraphAxis.Elevation:
                this.ydata = (selectedData.altitude*(metric?1:feet_per_m));
                this.ytext = that.ydata.toFixed(0) + (metric?" m":" ft");
                break;
            case GraphAxis.VerticalSpeed:
                this.ydata = (selectedData.vertical_speed*(metric?1:feet_per_m));
                this.ytext = that.ydata.toFixed(1) + (metric?" m/h":" ft/h");
                break;
            case GraphAxis.HorizontalSpeed:
                this.ydata = (selectedData.horizontal_speed*(metric?1:miles_per_km));
                this.ytext = that.ydata.toFixed(1) + (metric?" km/h":" mph");
                break;
        }

        that.focus
            .attr("cx", that.xScale(this.xdata))
            .attr("cy", that.yScale(this.ydata))
        that.xfocusText
            .html(this.xtext)
            .attr("x", that.xScale(this.xdata)+15)
            .attr("y", that.yScale(this.ydata))
        that.yfocusText
            .html(this.ytext)
            .attr("x", that.xScale(this.xdata)+15)
            .attr("y", that.yScale(this.ydata)+15)
    }

    this.hideGraphMarker = function() {
        that.focus.style("opacity", 0)
        that.xfocusText.style("opacity", 0)
        that.yfocusText.style("opacity", 0);
    }

    this.mouseout = function() {
        that.hideGraphMarker();
        hideMapMarker();
    }

    // Create a rect on top of the svg area: this rectangle recovers mouse position
    this.svg
        .append('rect')
        .style("fill", "none")
        .style("pointer-events", "all")
        .attr('width', this.width)
        .attr('height', this.height)
        .on('mouseover', this.mouseover)
        .on('mousemove', this.mousemove)
        .on('mouseout', this.mouseout);
}

var getJSON = function(url) {
    return new Promise(function(resolve, reject) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'json';
        xhr.onload = function() {
            var status = xhr.status;
            if (status === 200) {
                resolve(xhr.response);
            } else {
                reject(status, xhr.response);
            }
        };
        xhr.send();
    });
};

function settings() {
    if (localStorage.getItem("metric") === null) {
        localStorage.setItem("metric", true);
    } else {
        metric = (localStorage.getItem("metric")==="true");
    }

    if (metric) {
        document.getElementById("metricChecked").checked = true;
        setMetric();
    } else {
        document.getElementById("metricChecked").checked = false;
        setMetric();
    }
}

//RUN
const canvas = document.getElementById("renderCanvas"); // Get the canvas element
canvas.addEventListener("wheel", evt => evt.preventDefault());
const engine = new BABYLON.Engine(canvas, true); // Generate the BABYLON 3D engine
const scene = delayCreateScene(); //Call the createScene function

// Register a render loop to repeatedly render the scene
engine.runRenderLoop(function () {
    if (scene.activeCamera) {
        scene.render();
    }
});
// Watch for browser/canvas resize events
window.addEventListener("resize", function () {
    engine.resize();
    graph = new drawGraph(graphYAxis, graphXAxis);
});

function getPosition(lat, lon) {
    var x = (coordinateSystem.centerLon-lon)*coordinateSystem.metersPerDegreeLon;
    var y = (lat-coordinateSystem.centerLat)*coordinateSystem.metersPerDegreeLat;
    var ray = new BABYLON.Ray(new BABYLON.Vector3(x,y,20000), new BABYLON.Vector3(0,0,-1), 20100);
    var hit = scene.pickWithRay(ray);
    if (hit.hit) {
        return hit.pickedPoint;
    } else
        return false;
}

function findLine(lat, lon) {
    if (lat == null) {
        hideMapMarker();
        graph.hideGraphMarker();
        return;
    }
    var closest = kdtree.nearest([lon, lat], 1, 0.00001);
    if (closest.length<1) {
        hideMapMarker();
        graph.hideGraphMarker();
        return;
    }
    moveMapMarker(closest[0][0][1], closest[0][0][0]);
    var index = jsonData.geometry.coordinates[0].indexOf(closest[0][0]);
    graph.showGraphMarker();
    graph.moveGraphMarker(datas[index]);
}

function moveMapMarker(lat, lon) {
    marker.setLngLat([lon, lat]); //2D map
    var x = (coordinateSystem.centerLon-lon)*coordinateSystem.metersPerDegreeLon;
    var y = (lat-coordinateSystem.centerLat)*coordinateSystem.metersPerDegreeLat;
    scene.particleSystems[0].emitter.x=x;
    scene.particleSystems[0].emitter.y=y;
    var ray = new BABYLON.Ray(new BABYLON.Vector3(x,y,20000), new BABYLON.Vector3(0,0,-1), 20100);
    var hit = scene.pickWithRay(ray);
    if (hit.hit) {
        scene.particleSystems[0].emitter = hit.pickedPoint;
        scene.particleSystems[0].start();
        return true;
    }
    return false;
}

function hideMapMarker() {
    scene.particleSystems[0].stop();
    marker.setLngLat([0, 0]); //2D map
}

function mapstyle(style) {
    let styletype;
    switch(style) {
        case 'summer':
            styletype = 'standard';
            break;
        case 'winter':
            styletype = 'winter';
            break;
        case 'satellite':
            styletype = 'satellite';
            break;
        default:
            styletype = 'standard';
    }

    for (let i =0; i < meshes.length; i++ ) {
        meshes[i].material.albedoTexture.dispose();
        let url = root + `gltf/map/${styletype}/${jsonData.properties.tileBBoxes[i].tile_zoom}/${jsonData.properties.tileBBoxes[i].tile_x}/${jsonData.properties.tileBBoxes[i].tile_y}.png`;
        meshes[i].material.albedoTexture = new BABYLON.Texture(url, scene);
        meshes[i].material.albedoTexture.vScale = -1;
        jsonData.properties.tileBBoxes[i];
    }
}

function clickSettingsMetric() {
    metric = document.getElementById("metricChecked").checked;
    localStorage.setItem("metric",metric);
    setMetric();
}

function setMetric() {
    document.getElementById("metricCheckedLabel").innerText=(metric?"Metric Units":"Imperial Units");
    document.getElementById("value_distance").innerText = (distance/1000*(metric?1:miles_per_km)).toFixed(1) + (metric?" km":" mi");
    document.getElementById("value_elevation_up").innerText = (elevationUp*(metric?1:feet_per_m)).toFixed(0) + (metric?" m":" ft");
    document.getElementById("value_elevation_down").innerText = (elevationDown*(metric?1:feet_per_m)).toFixed(0) + (metric?" m":" ft");
    document.getElementById("value_highest_point").innerText = (highestElevationPoint*(metric?1:feet_per_m)).toFixed(0) + (metric?" m":" ft");
    document.getElementById("value_lowest_point").innerText = (lowestElevationPoint*(metric?1:feet_per_m)).toFixed(0) + (metric?" m":" ft");
    document.getElementById("value_horizontal_average").innerText=(horizontal_average*(metric?1:miles_per_km)).toFixed(1)+(metric?" km/h":" mph");
    document.getElementById("value_vertical_down_average").innerText=(vertical_down_average*(metric?1:feet_per_m)).toFixed(1)+(metric?" m/h":" ft/h");
    document.getElementById("value_vertical_up_average").innerText=(vertical_up_average*(metric?1:feet_per_m)).toFixed(1)+(metric?" m/h":" ft/h")

    graph = new drawGraph(graphYAxis, graphXAxis);
}

document.getElementById("inputTitle").addEventListener("keypress", function (event) {
    if (event.key === "Enter") {
        event.preventDefault();
        event.stopPropagation()
        event.stopImmediatePropagation();
        saveEdit();
    }
});

function saveEdit() {
    let editTitle = document.getElementById("inputTitle").value;
    let editType = document.getElementById("inputType").value;
    let editComment = document.getElementById("inputComment").value;

    let data = {index: trackid, title: editTitle, activitytype: editType, note: editComment};

    fetch(root+"modify", {
        method: "POST",
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(data)
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                document.getElementById("successmessageedit").innerText = "Changes saved";
                document.getElementById("successmessageedit").style.display = "block";
                location.reload();
            } else {
                document.getElementById("errormessageedit").innerText = response.response;
                document.getElementById("errormessageedit").style.display = "block";
            }
        });
    }).catch(error => {
        console.log("--error");
        console.log(error);
    });
}

function clickfavorite() {
    fetch(root+"modify/id="+trackid+"&favorite="+(!favorite), {
        method: "GET"
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                favorite = !favorite;
                if (favorite)
                    document.getElementById("favoritestar").src="../assets/icon_favorite_select.svg";
                else
                    document.getElementById("favoritestar").src="../assets/icon_favorite_unselect.svg";

                document.getElementById("favoriteChecked").checked = favorite;
                if (hidden)
                    location.reload();
            } else {
                //not good
            }
        });
    }).catch(error => {
        console.log("--error");
        console.log(error);
    });
}

function clickhide(confirmed) {
    if (!hidden && !confirmed) {
        bootstrap.Modal.getOrCreateInstance(document.getElementById('confirmhidden')).show();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('settingsModal')).hide();
        document.getElementById("hiddenChecked").checked = false;
        return;
    }
    fetch(root+"modify/id="+trackid+"&hidden="+(!hidden), {
        method: "GET"
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                location.reload();
            } else {
                //not good
            }
        });
    }).catch(error => {
        console.log("--error");
        console.log(error);
    });
}

function clickdelete(confirmed) {
    if (!confirmed) {
        bootstrap.Modal.getOrCreateInstance(document.getElementById('confirmdelete')).show();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('settingsModal')).hide();
        return;
    }
    fetch(root + "modify/id=" + trackid, {
        method: "DELETE"
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                location.href="../../dashboard";
            } else {
                //not good
            }
        });
    }).catch(error => {
        console.log("--error");
        console.log(error);
    });
}

function clickshare() {
    bootstrap.Modal.getOrCreateInstance(document.getElementById('sharemodal')).show();
    document.getElementById("sharelink").value = window.location.href;
    document.getElementById("sharelinkalert").style.visibility = 'hidden';
}

function copylinktoclipboard() {
    var copyText = document.getElementById("sharelink").value;
    navigator.clipboard.writeText(copyText).then(() => {
        document.getElementById("sharelinkalert").style.visibility = 'visible';
    });
}

function setTrackShare(state) {
    if (typeof state === 'undefined') {
        if (sharing === "PUBLIC")
            setTrackShare("PRIVATE");
        else
            setTrackShare("PUBLIC");
        return;
    }
    fetch(root+"modify/id="+trackid+"&sharing="+state, {
        method: "GET"
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                sharing = state;
                if (state === "PUBLIC") {
                    document.getElementById("publicSet").style.display = "block";
                    document.getElementById("privateSet").style.display = "none";
                    document.getElementById("publicicon").style.display = "inline";
                    document.getElementById("publicChecked").checked = true;
                    document.getElementById("publicCheckedLabel").innerText = "Public, anyone with the link can view this track";
                } else {
                    document.getElementById("publicSet").style.display = "none";
                    document.getElementById("privateSet").style.display = "block";
                    document.getElementById("publicicon").style.display = "none";
                    document.getElementById("publicChecked").checked = false;
                    document.getElementById("publicCheckedLabel").innerText = "Private, only you can view this track";
                }
            } else {
                //not good
            }
        });
    }).catch(error => {
        console.log("--error");
        console.log(error);
    });
}