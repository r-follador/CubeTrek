import {KdTree} from "./kd-tree.js";
import eventBus from './EventBus.js';

const zscaling = 1.5;

export class Map3D {

    constructor(geojson) {
        this.geojson = geojson;
        this.canvas = document.getElementById("map3dCanvas"); // Get the canvas element
        this.canvas.addEventListener("wheel", evt => evt.preventDefault());
        this.engine = new BABYLON.Engine(this.canvas, true);
        this.engine.setHardwareScalingLevel(0.7);

        this.coordinateSystem = [];

        //Get relevant measurements for calculations from the GEOJson
        this.coordinateSystem.centerLat = geojson.properties.bbox.centerLat;
        this.coordinateSystem.centerLon = geojson.properties.bbox.centerLon;
        this.coordinateSystem.metersPerDegreeLat = geojson.properties.bbox.metersPerDegreeLat;
        this.coordinateSystem.metersPerDegreeLon = geojson.properties.bbox.metersPerDegreeLon;
        this.width_y = (geojson.properties.bbox.boundingBoxN-geojson.properties.bbox.boundingBoxS)*geojson.properties.bbox.metersPerDegreeLat;
        this.width_x = (geojson.properties.bbox.boundingBoxE-geojson.properties.bbox.boundingBoxW)*geojson.properties.bbox.metersPerDegreeLon;
        this.zoomfactor = geojson.properties.tileBBoxes[0].tile_zoom;

        this.meshes = [];

        this.kdtree = new KdTree(geojson.geometry.coordinates);

        this.scene = this.delayCreateScene().then(scene => {
            this.scene = scene;
            this.engine.runRenderLoop(() => {
                if (this.scene.activeCamera) {
                    this.scene.render();
                }
            });
        })

        document.getElementById("btnradio1").addEventListener('click', () =>  {this.changeMapstyle('standard')});
        document.getElementById("btnradio3").addEventListener('click', () =>  {this.changeMapstyle('satellite')});
    }

    async delayCreateScene() {
        const scene = new BABYLON.Scene(this.engine);
        await this.loadMeshAndTexture(scene);

        //Set the camera
        var camera = new BABYLON.ArcRotateCamera("camera", Math.PI/4, Math.PI/4, 5000, new BABYLON.Vector3(0,0,1500), scene);
        camera.upVector = new BABYLON.Vector3(0, 0, 1);
        camera.setTarget(this.globalBoundingInfo.boundingBox.center);
        camera.radius = this.globalBoundingInfo.diagonalLength * 1.5; //
        camera.maxZ = 100000;
        camera.upperBetaLimit = Math.PI/2;
        camera.upperRadiusLimit = Math.min(100000, 10000*Math.pow(2,14-this.zoomfactor));
        camera.lowerRadiusLimit = 100;
        camera.wheelPrecision = 0.1; //Mouse wheel speed
        camera.zoomToMouseLocation = true;
        camera.panningSensibility = 2/Math.pow(2,14-this.zoomfactor);
        camera.multiTouchPanAndZoom = true;
        camera.useNaturalPinchZoom = true;
        camera.attachControl(this.canvas, true, true);
        camera.useAutoRotationBehavior = true;
        camera.autoRotationBehavior.idleRotationWaitTime = 60 * 1000;

        //Background
        var backgroundLayer = new BABYLON.Layer('','../assets/bkgrd.png', scene, true);
        scene.clearColor = new BABYLON.Color3.FromHexString("#2e3c4d");

        //Lighting
        this.helperLight = new BABYLON.HemisphericLight("DirectionalLightAbove", new BABYLON.Vector3(0,0, -1), scene);
        this.helperLight.intensity=0.6;
        var dirLight = new BABYLON.DirectionalLight("DirectionalLightSide", new BABYLON.Vector3(1, 1, 0), scene);
        dirLight.intensity = 2;
        scene.ambientColor = new BABYLON.Color3(1, 1, 1);

        //when camera is changed (rotation/zoom/pan...)
        camera.onViewMatrixChangedObservable.add(function(c) {
            //Clamp camera.target.z within a certain boundary
            //TODO: this could use some smarter way to prevent the camera from going too far off
            c.target.z = Math.min(Math.max(c.target.z, (sharedObjects.lowestElevationPoint - 200) * zscaling), (sharedObjects.highestElevationPoint + 200) * zscaling);
            //change light as to give the impression that the mesh rotates (instead of the camera)
            dirLight.direction = new BABYLON.Vector3(-1 * c.position.y, c.position.x, 0);
        });

        await this.loadPeaks(this.geojson, scene);

        scene.onPointerObservable.add((pointerInfo) => {
            if (pointerInfo.type === BABYLON.PointerEventTypes.POINTERMOVE) {
                var hit = scene.pick(scene.pointerX, scene.pointerY)
                if (hit.hit) {
                    var lat = (hit.pickedPoint._y/this.coordinateSystem.metersPerDegreeLat)+this.coordinateSystem.centerLat;
                    var lon = this.coordinateSystem.centerLon-(hit.pickedPoint._x/this.coordinateSystem.metersPerDegreeLon);
                    this.findClosestTrackpoint(lat,lon);
                } else {
                    this.findClosestTrackpoint(null);
                }
            }
        });


        //ParticleSystem is used as the position marker
        var particleSystem = new BABYLON.ParticleSystem("particles", 2000, scene);
        particleSystem.particleTexture = new BABYLON.Texture("../assets/flare.png", scene);
        particleSystem.emitter = new BABYLON.Vector3(0, 0, 0);
        particleSystem.minEmitPower = 100*Math.pow(2,14-this.zoomfactor);
        particleSystem.maxEmitPower = 300*Math.pow(2,14-this.zoomfactor);
        particleSystem.direction1 = new BABYLON.Vector3(0, 0, 1);
        particleSystem.direction2 = new BABYLON.Vector3(0, 0, 1);
        particleSystem.emitRate = 100;
        particleSystem.minLifeTime = 1;
        particleSystem.maxLifeTime = 1.5;
        particleSystem.color1 = new BABYLON.Color4(0.7, 0.8, 1.0, 1.0);
        particleSystem.color2 = new BABYLON.Color4(1.0, 0.5, 0.0, 1.0);
        particleSystem.colorDead = new BABYLON.Color4(0, 0, 0.2, 1.0);
        particleSystem.minSize = 10*Math.pow(2,14-this.zoomfactor);
        particleSystem.maxSize = 150*Math.pow(2,14-this.zoomfactor);
        particleSystem.emitter.z = -2000;

        return scene;
    }

    async loadMeshAndTexture(scene) {
        try {
            const rootPath = sharedObjects.root + "gltf/";
            const fileName = sharedObjects.trackid + ".gltf";

            // Progress callback
            const onProgress = (evt) => {
                let loadedPercent = 0;
                if (evt.lengthComputable) {
                    loadedPercent = (evt.loaded * 100 / evt.total).toFixed();
                } else {
                    const dlCount = evt.loaded / (1024 * 1024); // Convert bytes to MB
                    loadedPercent = Math.floor(dlCount * 100.0) / 100.0;
                }
                document.getElementById("progressbar").setAttribute("style", "width: " + loadedPercent + "%");
            };
            const result = await BABYLON.SceneLoader.ImportMeshAsync(null, rootPath, fileName, scene, onProgress);
            document.getElementById("progressdiv").style.display = "none";

            //Loop through all meshes, Z-scale them, compute the normals (they are not properly set in the GLTF),
            // and add the relevent meshes to the this.meshes list
            let bounds = result.meshes[0].getHierarchyBoundingVectors();
            this.globalBoundingInfo = new BABYLON.BoundingInfo(bounds.min, bounds.max);
            result.meshes.forEach(parentMesh => {
                parentMesh.getChildren().forEach(mesh => {
                    mesh.scaling.z = zscaling;
                    var normals = [];
                    BABYLON.VertexData.ComputeNormals(mesh.getVerticesData(BABYLON.VertexBuffer.PositionKind), mesh.getIndices(), normals);
                    mesh.setVerticesData(BABYLON.VertexBuffer.NormalKind, normals);
                    if (mesh.getVerticesData(BABYLON.VertexBuffer.UVKind)) {
                        this.meshes.push(mesh);
                    }
                    //mesh.forceSharedVertices();
                })
            })

            //Draw the GPS Path onto emissive DynamicTextures

            //for each mesh, add a DynamicTexture with emissive property (for the path drawing)
            const textureSize = 512;
            let textureContexts = [];
            const strokeStyle = "#ff8001";
            const lineWidth = 3;

            //Create an emissive, dynamic texture for each mesh
            for (let i =0; i < this.meshes.length; i++ ) {
                var myDynamicTexture = new BABYLON.DynamicTexture("dtx"+i, textureSize, scene);
                var textureContext = myDynamicTexture.getContext();
                textureContexts.push(textureContext);
                this.meshes[i].material.emissiveTexture = myDynamicTexture; //pBR texture
                this.meshes[i].material.emissiveColor = new BABYLON.Color3(1, 1, 1);
                //textureContext.strokeStyle = "#2a84de";
                textureContext.strokeStyle = strokeStyle;
                textureContext.lineWidth = lineWidth;
                textureContext.beginPath();
            }

            //Use the GeoJson to draw the path onto the DynamicTexture
            let tileIndex = -1;
            let px_perDegree_lat = 0;
            let px_perDegree_lon = 0;
            for (let i=0; i < this.geojson.geometry.coordinates.length; i++) {
                let track = this.geojson.geometry.coordinates[i];
                //track is an array of latitudes/longitudes of the gps track
                //track[j][0] is longitude, track[j][1] is latitude
                for (let j = 1; j < track.length; j++) {

                    if (tileIndex===-1 || !(this.geojson.properties.tileBBoxes[tileIndex].n_Bound > track[j][1] && this.geojson.properties.tileBBoxes[tileIndex].s_Bound < track[j][1] && this.geojson.properties.tileBBoxes[tileIndex].w_Bound < track[j][0] && this.geojson.properties.tileBBoxes[tileIndex].e_Bound > track[j][0])) {
                        if (tileIndex !== -1) {//before tile switch; draw into void of old tile to keep lines correct
                            let x=(track[j][0]-this.geojson.properties.tileBBoxes[tileIndex].w_Bound)*px_perDegree_lon;
                            let y=(track[j][1]-this.geojson.properties.tileBBoxes[tileIndex].s_Bound)*px_perDegree_lat;
                            textureContexts[tileIndex].lineTo(x, y);
                        }
                        for (let k = 0; k < this.geojson.properties.tileBBoxes.length; k++) {
                            if (this.geojson.properties.tileBBoxes[k].n_Bound > track[j][1] && this.geojson.properties.tileBBoxes[k].s_Bound < track[j][1] && this.geojson.properties.tileBBoxes[k].w_Bound < track[j][0] && this.geojson.properties.tileBBoxes[k].e_Bound > track[j][0]) {
                                tileIndex = k;
                                px_perDegree_lon = textureSize / this.geojson.properties.tileBBoxes[tileIndex].widthLonDegree;
                                px_perDegree_lat = textureSize / this.geojson.properties.tileBBoxes[tileIndex].widthLatDegree;

                                //after tile switch; move to previous point into void of new tile to keep lines correct
                                let x=(track[j-1][0]-this.geojson.properties.tileBBoxes[tileIndex].w_Bound)*px_perDegree_lon;
                                let y=(track[j-1][1]-this.geojson.properties.tileBBoxes[tileIndex].s_Bound)*px_perDegree_lat;
                                textureContexts[tileIndex].moveTo(x, y);
                                break;
                            }
                        }
                    }

                    let x=(track[j][0]-this.geojson.properties.tileBBoxes[tileIndex].w_Bound)*px_perDegree_lon;
                    let y=(track[j][1]-this.geojson.properties.tileBBoxes[tileIndex].s_Bound)*px_perDegree_lat;
                    textureContexts[tileIndex].lineTo(x, y);
                }
            }

            //Update all the textures
            for (let i =0; i < this.meshes.length; i++ ) {
                textureContexts[i].stroke();
                this.meshes[i].material.emissiveTexture.update();
            }

        } catch (error) {
            document.getElementById("errormessage").innerText = "Error Loading Assets; Try again.";
            document.getElementById("errormessage").style.display = "block";
            document.getElementById("progressdiv").style.display = "none";
            console.error(error);
        }
    }

    //loadPeaks gets a list of peaks within the area (defined by [n/w/e/s]_bound)
    //peaks are displayed as a signpost on top of the mesh
    async loadPeaks(geojson, scene) {
        let n_bound = geojson.properties.tileBBoxes[0].n_Bound;
        let w_bound = geojson.properties.tileBBoxes[0].w_Bound;
        let e_bound = geojson.properties.tileBBoxes[geojson.properties.tileBBoxes.length-1].e_Bound;
        let s_bound = geojson.properties.tileBBoxes[geojson.properties.tileBBoxes.length-1].s_Bound;
        const url = sharedObjects.root+"peaks/nbound="+n_bound+"&sbound="+s_bound+"&wbound="+w_bound+"&ebound="+e_bound;
        const response = await fetch(url);
        const peaklistjson = await response.json();
        const font_size = 48;
        const font = font_size + "px Helvetica";
        const planeHeight = 100;
        const DTHeight = 1.5 * font_size;
        const ratio = planeHeight/DTHeight;
        let temp = new BABYLON.DynamicTexture("DynamicTexture", 64, scene);
        let tmpctx = temp.getContext();
        tmpctx.font = font;
        const f = new BABYLON.Vector4(0,0, 1, 1);
        const b = new BABYLON.Vector4(1,0, 0, 1);

        //mat2 is the plain material for the post of the signpost
        let mat2 = new BABYLON.StandardMaterial("mat", scene);
        mat2.ambientColor = new BABYLON.Color3(1, 1, 1);
        mat2.alpha = 0.7;
        for (let i=0; i < peaklistjson.peaklist.length && i < 100; i++) {

            //Create mat, a Babylon.StandardMaterial with peak name
            let text = peaklistjson.peaklist[i].name;
            let DTWidth = tmpctx.measureText(text).width + 8;
            let planeWidth = DTWidth * ratio;
            let dynamicTexture = new BABYLON.DynamicTexture("DynamicTexture", {width:DTWidth, height:DTHeight}, scene, false);
            let mat = new BABYLON.StandardMaterial("mat", scene);
            mat.ambientTexture = dynamicTexture;
            mat.ambientColor = new BABYLON.Color3(1, 1, 1);
            mat.alpha = 0.7;
            dynamicTexture.drawText(text, null, null, font, "#000000", "#ffffff", true);

            let pos = this.getPosition(peaklistjson.peaklist[i].lat, peaklistjson.peaklist[i].lon, scene);

            //plane is the sign of the signpost
            let plane = BABYLON.MeshBuilder.CreatePlane("plane", {width:planeWidth, height:planeHeight, frontUVs: f, backUVs: b, sideOrientation: BABYLON.Mesh.DOUBLESIDE}, scene);
            plane.material = mat;
            plane.position.x = pos.x;
            plane.position.y = pos.y;
            plane.position.z = (pos.z*zscaling)+(planeHeight)+70;
            plane.rotation.x = Math.PI/2;
            plane.isPickable = false;

            //plane2 is the post of the signpost
            let plane2 = BABYLON.MeshBuilder.CreatePlane("plane2", {width:20, height:140, sideOrientation: BABYLON.Mesh.DOUBLESIDE}, scene);
            plane2.position.x = pos.x;
            plane2.position.y = pos.y;
            plane2.position.z = (pos.z*zscaling)+70;
            plane2.rotation.x = Math.PI/2;
            plane2.isPickable = false;
            plane2.material = mat2;
        }
    }

    //shoots a BABYLON.Ray from the top down to query the intersection (pickWithRay)
    //the idea is to get the height of the mesh at the given lat/lon to place the marker and sign posts
    getPosition(lat, lon, scene) {
        let x = (this.coordinateSystem.centerLon-lon)*this.coordinateSystem.metersPerDegreeLon;
        let y = (lat-this.coordinateSystem.centerLat)*this.coordinateSystem.metersPerDegreeLat;
        let ray = new BABYLON.Ray(new BABYLON.Vector3(x,y,20000), new BABYLON.Vector3(0,0,-1), 20100);
        let hit = scene.pickWithRay(ray);
        if (hit.hit) {
            return hit.pickedPoint;
        } else
            return false;
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

        for (; outerIndex < this.geojson.geometry.coordinates.length; outerIndex++) {
            let index = this.geojson.geometry.coordinates[outerIndex].indexOf(closest[0][0]);
            if (index !== -1) {
                innerIndex = index;
                absoluteIndex += innerIndex;
                break;
            }
            absoluteIndex += this.geojson.geometry.coordinates[outerIndex].length;
        }
        eventBus.emit('moveMarkers', {lon: closest[0][0][0], lat: closest[0][0][1], datasIndex: absoluteIndex});
    }

    hideMarker() {
        this.scene.particleSystems[0].stop();
    }

    moveMarker(lat, lon) {
        let hit = this.getPosition(lat,lon, this.scene);
        if (hit) {
            this.scene.particleSystems[0].emitter = hit;
            this.scene.particleSystems[0].start();
            return true;
        }
        return false;
    }

    changeMapstyle(style) {
        let styletype;
        switch(style) {
            case 'standard':
                styletype = 'standard';
                this.helperLight.intensity=0.6;
                break;
            case 'satellite':
                styletype = 'satellite';
                this.helperLight.intensity=0.8;
                break;
            default:
                this.styletype = 'standard';
                this.helperLight.intensity=0.6;
        }

        //exchange texture for each mesh
        for (let i =0; i < this.meshes.length; i++ ) {
            this.meshes[i].material.albedoTexture.dispose();
            let url = sharedObjects.root + `gltf/map/${styletype}/${this.geojson.properties.tileBBoxes[i].tile_zoom}/${this.geojson.properties.tileBBoxes[i].tile_x}/${this.geojson.properties.tileBBoxes[i].tile_y}.png`;
            this.meshes[i].material.albedoTexture = new BABYLON.Texture(url, this.scene);
            this.meshes[i].material.albedoTexture.vScale = -1;
        }
    }
}