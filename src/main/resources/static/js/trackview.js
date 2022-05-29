var delayCreateScene = function() {
    var scene = new BABYLON.Scene(engine);
    var gl = new BABYLON.GlowLayer("glow", scene);
    var masterMesh;
    var meshes = [];
    var jsonData;
    var particleSystem = new BABYLON.ParticleSystem("particles", 2000, scene);
    Promise.all([
        BABYLON.SceneLoader.ImportMeshAsync(null, "../api/gltf/", trackname+".gltf", scene, function (evt) {
            var loadedPercent = 0;
            if (evt.lengthComputable) {
                loadedPercent = (evt.loaded * 100 / evt.total).toFixed();
            } else {
                var dlCount = evt.loaded / (1024 * 1024);
                loadedPercent = Math.floor(dlCount * 100.0) / 100.0;
            }
            document.getElementById("progressbar").setAttribute("style","width: "+loadedPercent+"%")
        }),
        getJSON("../api/simplifiedtrack/"+trackname+".geojson")
    ]).catch(error => {
        document.getElementById("activity_title").innerText = "Error Loading Assets"
        console.error(error);
    }).then((values) => {
        let result = values[0]; //returned from ImportMeshAsync
        result.meshes.forEach(parentMesh => {
            parentMesh.getChildren().forEach(mesh => {
                mesh.scaling.z = 1.5;
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

        var layer = new BABYLON.Layer('','../assets/bkgrd.png', scene, true);
        scene.clearColor = new BABYLON.Color3.FromHexString("#2e3c4d");

        var camera = new BABYLON.ArcRotateCamera("camera", Math.PI/4, Math.PI/4, 5000, new BABYLON.Vector3(0,0,1500), scene);
        camera.upVector = new BABYLON.Vector3(0, 0, 1);
        camera.zoomOn(masterMesh);
        camera.maxZ = 25000;
        camera.upperBetaLimit = Math.PI/2;
        camera.upperRadiusLimit = 20000;
        camera.lowerRadiusLimit = 100;
        camera.wheelPrecision = 0.1; //Mouse wheel speed
        camera.wheelPrecision = 0.1; //Mouse wheel speed
        camera.zoomToMouseLocation = true;
        camera.panningSensibility = 1;
        camera.attachControl(canvas, true, true);
        camera.useAutoRotationBehavior = true;
        camera.autoRotationBehavior.idleRotationWaitTime = 60 * 1000;

        //showWorldAxis(5000);
        var helperLight = new BABYLON.HemisphericLight("DirectionalLightAbove", new BABYLON.Vector3(0,0, -1), scene);
        helperLight.intensity=0.3;
        var dirLight = new BABYLON.DirectionalLight("DirectionalLightSide", new BABYLON.Vector3(1, 1, 0), scene);
        dirLight.intensity = 2;

        camera.onViewMatrixChangedObservable.add(function(c) {
            c.target.z = 1500;
            dirLight.direction = new BABYLON.Vector3(-1 * c.position.y, c.position.x, 0);
        })

        coordinateSystem.centerLat = jsonData.properties.bbox.centerLat;
        coordinateSystem.centerLon = jsonData.properties.bbox.centerLon;
        coordinateSystem.metersPerDegreeLat = jsonData.properties.bbox.metersPerDegreeLat;
        coordinateSystem.metersPerDegreeLon = jsonData.properties.bbox.metersPerDegreeLon;

        particleSystem.particleTexture = new BABYLON.Texture("../assets/flare.png", scene);
        particleSystem.emitter = new BABYLON.Vector3(0, 0, 0);
        particleSystem.minEmitPower = 100;
        particleSystem.maxEmitPower = 300;
        particleSystem.direction1 = new BABYLON.Vector3(0, 0, 1);
        particleSystem.direction2 = new BABYLON.Vector3(0, 0, 1);
        particleSystem.emitRate = 100;
        particleSystem.minLifeTime = 1;
        particleSystem.maxLifeTime = 1.5;
        particleSystem.color1 = new BABYLON.Color4(0.7, 0.8, 1.0, 1.0);
        particleSystem.color2 = new BABYLON.Color4(1.0, 0.5, 0.0, 1.0);
        particleSystem.colorDead = new BABYLON.Color4(0, 0, 0.2, 1.0);
        particleSystem.minSize = 10;
        particleSystem.maxSize = 150;
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
        drawGraphs(jsonData);
        document.getElementById("progressdiv").style.display = "none";
    });
    return scene;
};


function drawGraphs(jsonData) {
    const datas = [];
    var parseDate = d3.timeParse("%Y-%m-%dT%H:%M:%S%Z");
    for (var i=0; i<jsonData.geometry.coordinates[0].length; i++) {
        datas.push({'time' : parseDate(jsonData.geometry.coordinates[0][i][3]), 'altitude' : jsonData.geometry.coordinates[0][i][2]})
    }

    var margin = {top: 10, right: 30, bottom: 30, left: 60},
        width = 700 - margin.left - margin.right,
        height = 200 - margin.top - margin.bottom;

// append the svg object to the body of the page
    const svg = d3.select("#graph")
        .append("svg")
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.top + margin.bottom)
        .append("g")
        .attr("transform",
            "translate(" + margin.left + "," + margin.top + ")");

    const yScale = d3.scaleLinear()
        .range([height,0])
        .domain(d3.extent(datas, function(d) { return d.altitude; }))

    const xScale = d3.scaleTime()
        .range([0, width])
        .domain(d3.extent(datas, function(d) { return d.time; }))

    svg.append("g")
        .attr("transform", "translate(0," + height + ")")
        .call(d3.axisBottom(xScale));

    svg.append("g")
        .call(d3.axisLeft(yScale));


    svg.append("path")
        .datum(datas)
        .attr("fill", "none")
        .attr("stroke", "steelblue")
        .attr("stroke-width", 1.5)
        .attr("d", d3.line()
            .x(function(d) { return xScale(d.time) })
            .y(function(d) { return yScale(d.altitude) })
        )

    ///
    // This allows to find the closest X index of the mouse:
    var bisect = d3.bisector(function(d) { return d.time; }).left;

    // Create the circle that travels along the curve of chart
    var focus = svg
        .append('g')
        .append('circle')
        .style("fill", "none")
        .attr("stroke", "black")
        .attr('r', 8.5)
        .style("opacity", 0)

    // Create the text that travels along the curve of chart
    var focusText = svg
        .append('g')
        .append('text')
        .style("opacity", 0)
        .attr("text-anchor", "left")
        .attr("alignment-baseline", "middle")


    // Create a rect on top of the svg area: this rectangle recovers mouse position
    svg
        .append('rect')
        .style("fill", "none")
        .style("pointer-events", "all")
        .attr('width', width)
        .attr('height', height)
        .on('mouseover', mouseover)
        .on('mousemove', mousemove)
        .on('mouseout', mouseout);


    // What happens when the mouse move -> show the annotations at the right positions.
    function mouseover() {
        focus.style("opacity", 1)
        focusText.style("opacity",1)
    }

    function mousemove() {
        // recover coordinate we need
        var x0 = xScale.invert(d3.pointer(event, this)[0]);
        var i = bisect(datas, x0, 1);
        selectedData = datas[i]
        focus
            .attr("cx", xScale(selectedData.time))
            .attr("cy", yScale(selectedData.altitude))
        focusText
            .html(selectedData.altitude)
            .attr("x", xScale(selectedData.time)+15)
            .attr("y", yScale(selectedData.altitude))

        moveMarker(jsonData.geometry.coordinates[0][i][1], jsonData.geometry.coordinates[0][i][0]);
    }
    function mouseout() {
        focus.style("opacity", 0)
        focusText.style("opacity", 0)
        hideMarker();
    }

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

const coordinateSystem = [];
const canvas = document.getElementById("renderCanvas"); // Get the canvas element
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
});

function moveMarker(lat, lon) {
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

function hideMarker() {
    scene.particleSystems[0].stop();
}


function showWorldAxis(size) {
    var makeTextPlane = function(text, color, size) {
        var dynamicTexture = new BABYLON.DynamicTexture("DynamicTexture", 50, scene, true);
        dynamicTexture.hasAlpha = true;
        dynamicTexture.drawText(text, 5, 40, "bold 36px Arial", color , "transparent", true);
        var plane = BABYLON.Mesh.CreatePlane("TextPlane", size, scene, true);
        plane.material = new BABYLON.StandardMaterial("TextPlaneMaterial", scene);
        plane.material.backFaceCulling = false;
        plane.material.specularColor = new BABYLON.Color3(0, 0, 0);
        plane.material.diffuseTexture = dynamicTexture;
        return plane;
    };
    var axisX = BABYLON.Mesh.CreateLines("axisX", [
        BABYLON.Vector3.Zero(), new BABYLON.Vector3(size, 0, 0), new BABYLON.Vector3(size * 0.95, 0.05 * size, 0),
        new BABYLON.Vector3(size, 0, 0), new BABYLON.Vector3(size * 0.95, -0.05 * size, 0)
    ], scene);
    axisX.color = new BABYLON.Color3(1, 0, 0);
    var xChar = makeTextPlane("X", "red", size / 10);
    xChar.position = new BABYLON.Vector3(0.9 * size, -0.05 * size, 0);
    var axisY = BABYLON.Mesh.CreateLines("axisY", [
        BABYLON.Vector3.Zero(), new BABYLON.Vector3(0, size, 0), new BABYLON.Vector3( -0.05 * size, size * 0.95, 0),
        new BABYLON.Vector3(0, size, 0), new BABYLON.Vector3( 0.05 * size, size * 0.95, 0)
    ], scene);
    axisY.color = new BABYLON.Color3(0, 1, 0);
    var yChar = makeTextPlane("Y", "green", size / 10);
    yChar.position = new BABYLON.Vector3(0, 0.9 * size, -0.05 * size);
    var axisZ = BABYLON.Mesh.CreateLines("axisZ", [
        BABYLON.Vector3.Zero(), new BABYLON.Vector3(0, 0, size), new BABYLON.Vector3( 0 , -0.05 * size, size * 0.95),
        new BABYLON.Vector3(0, 0, size), new BABYLON.Vector3( 0, 0.05 * size, size * 0.95)
    ], scene);
    axisZ.color = new BABYLON.Color3(0, 0, 1);
    var zChar = makeTextPlane("Z", "blue", size / 10);
    zChar.position = new BABYLON.Vector3(0, 0.05 * size, 0.9 * size);
}