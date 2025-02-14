import eventBus from './EventBus.js';

const miles_per_km = 0.621371;
const feet_per_m = 3.28084;

export class ConvertedDatas {
    constructor(jsonData) {
        this.datas = [];
        this.movingTime = 0;
        this.verticalDistSumUp = 0;
        this.verticalTimeSumUp = 0;
        this.verticalDistSumDown = 0;
        this.verticalTimeSumDown = 0;
        this.distance_offset = 0;
        this.hasHeartrate = false;

        var parseDate = d3.timeParse("%Y-%m-%dT%H:%M:%S%Z");

        for (let j=0;j<jsonData.geometry.coordinates.length;j++) {
            let previousTime = parseDate(jsonData.geometry.coordinates[j][0][3]);
            let previousDistance = jsonData.geometry.coordinates[j][0][4];
            let previousElevation = jsonData.geometry.coordinates[j][0][2];

            for (let i = 0; i < jsonData.geometry.coordinates[j].length; i++) {
                let time = parseDate(jsonData.geometry.coordinates[j][i][3]);
                let elevation = jsonData.geometry.coordinates[j][i][2];
                let distance = jsonData.geometry.coordinates[j][i][4];

                let time_diff_hour = (time - previousTime) / 3600000;
                let verticalSpeed_m_per_h = (elevation - previousElevation) / time_diff_hour;
                if (isNaN(verticalSpeed_m_per_h))
                    verticalSpeed_m_per_h = 0;
                let horizontalSpeed_km_per_h = ((distance - previousDistance) / 1000) / (time_diff_hour);
                if (isNaN(horizontalSpeed_km_per_h))
                    horizontalSpeed_km_per_h = 0;

                if ((time - previousTime) / 1000 < (60) || Math.abs(elevation - previousElevation) > 3) {
                    this.movingTime += (time - previousTime);
                    if (elevation - previousElevation > 0) {
                        this.verticalDistSumUp += (elevation - previousElevation);
                        this.verticalTimeSumUp += (time - previousTime);
                    } else if (elevation - previousElevation < 0) {
                        this.verticalDistSumDown += (previousElevation - elevation);
                        this.verticalTimeSumDown += (time - previousTime);
                    }
                }

                previousTime = time;
                previousElevation = elevation;
                previousDistance = distance;

                let hasHeartrate = (jsonData.geometry.coordinates[j][i].length > 5);

                this.datas.push({
                    'time': time,
                    'altitude': elevation,
                    'distance': this.distance_offset+distance,
                    'vertical_speed': verticalSpeed_m_per_h,
                    'horizontal_speed': horizontalSpeed_km_per_h,
                    'moving_time': this.movingTime,
                    ...(hasHeartrate ? {'heartrate' : jsonData.geometry.coordinates[j][i][5]} : {})
                });

                this.hasHeartrate = this.hasHeartrate || hasHeartrate;
            }
            this.distance_offset = this.distance_offset + previousDistance;
        }
    }
}

export class GraphCube {
    constructor(jsonData) {
        this.jsonData = jsonData;
        this.graphXAxis = GraphAxis.Distance;
        this.graphYAxis = GraphAxis.Elevation;

        this.convertedData = new ConvertedDatas(jsonData);

        sharedObjects.horizontal_average = ((this.convertedData.distance_offset)/(this.convertedData.movingTime/3600));
        sharedObjects.vertical_down_average = (this.convertedData.verticalDistSumDown)/(this.convertedData.verticalTimeSumDown/3600000);
        sharedObjects.vertical_up_average = (this.convertedData.verticalDistSumUp)/(this.convertedData.verticalTimeSumUp/3600000);
        sharedObjects.moving_time = this.convertedData.movingTime;

        document.getElementById("graphYDistance").addEventListener('click', () =>  {this.changeGraphY(GraphAxis.Distance)});
        document.getElementById("graphYElevation").addEventListener('click', () =>  {this.changeGraphY(GraphAxis.Elevation)});
        document.getElementById("graphYHorizontalspeed").addEventListener('click', () =>  {this.changeGraphY(GraphAxis.HorizontalSpeed)});
        document.getElementById("graphYVerticalspeed").addEventListener('click', () =>  {this.changeGraphY(GraphAxis.VerticalSpeed)});
        if (document.getElementById("graphYHeartrate")) {
            document.getElementById("graphYHeartrate").addEventListener('click', () => {this.changeGraphY(GraphAxis.Heartrate)});
        }
        document.getElementById("graphXElapsedtime").addEventListener('click', () =>  {this.changeGraphX(GraphAxis.ElapsedTime)});
        document.getElementById("graphXMovingtime").addEventListener('click', () =>  {this.changeGraphX(GraphAxis.MovingTime)});
        document.getElementById("graphXDistance").addEventListener('click', () =>  {this.changeGraphX(GraphAxis.Distance)});

        this.drawGraph();
        if (this.convertedData.hasHeartrate)
            this.heartrateGraph = new GraphHeartrate(this.convertedData, document.getElementById("heartrateDonut"), document.getElementById("heartrate_average"), document.getElementById("heartrate_max"));
    }

    changeGraphX(type) {
        this.graphXAxis = type;
        this.drawGraph();
    }

    changeGraphY(type) {
        this.graphYAxis = type;
        this.drawGraph();
    }

    drawGraph() {
        this.margingraph = {top: 10, right: 5, bottom: 25, left: 40};

        this.width = document.getElementById('graph').clientWidth-this.margingraph.left-this.margingraph.right;
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

        switch (this.graphYAxis) {
            case GraphAxis.Elevation:
                document.getElementById("dropdowngraphyaxis").innerText = "Elevation";
                this.yScale = d3.scaleLinear().domain(d3.extent(this.convertedData.datas, function(d) { return (d.altitude*(sharedObjects.metric?1:feet_per_m)); }));
                this.functionpath.y((d) => { return this.yScale(d.altitude*(sharedObjects.metric?1:feet_per_m)) });
                break;
            case GraphAxis.Distance:
                document.getElementById("dropdowngraphyaxis").innerText = "Distance";
                this.yScale = d3.scaleLinear().domain(d3.extent(this.convertedData.datas, function(d) { return (d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)); }));
                this.functionpath.y((d) => {return this.yScale(d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)) });
                break;
            case GraphAxis.VerticalSpeed:
                document.getElementById("dropdowngraphyaxis").innerText = "Vertical Speed";
                this.yScale = d3.scaleLinear().domain(d3.extent(this.convertedData.datas, function(d) { return (d.vertical_speed*(sharedObjects.metric?1:feet_per_m)); }));
                this.functionpath.y((d) => {return this.yScale(d.vertical_speed*(sharedObjects.metric?1:feet_per_m)) });
                this.regressionGenerator.y(d => d.vertical_speed*(sharedObjects.metric?1:feet_per_m));
                break;
            case GraphAxis.HorizontalSpeed:
                document.getElementById("dropdowngraphyaxis").innerText = "Horizontal Speed";
                this.yScale = d3.scaleLinear().domain(d3.extent(this.convertedData.datas, function(d) { return (d.horizontal_speed*(sharedObjects.metric?1:miles_per_km)); }));
                this.functionpath.y((d) => { return this.yScale(d.horizontal_speed*(sharedObjects.metric?1:miles_per_km)) });
                this.regressionGenerator.y(d => d.horizontal_speed*(sharedObjects.metric?1:miles_per_km));
                break;
            case GraphAxis.Heartrate:
                document.getElementById("dropdowngraphyaxis").innerText = "Heart Rate";
                this.yScale = d3.scaleLinear().domain(d3.extent(this.convertedData.datas.filter(d => d.heartrate !== -1), function(d) { return (d.heartrate); }));
                this.functionpath.y((d) => { return this.yScale(d.heartrate) });
                break;
            default:
                break;
        }

        switch (this.graphXAxis) {
            case GraphAxis.ElapsedTime:
                document.getElementById("dropdowngraphxaxis").innerText = "Elapsed time";
                this.xScale = d3.scaleTime().domain(d3.extent(this.convertedData.datas, function(d) { return d.time; }));
                this.functionpath.x((d) => { return this.xScale(d.time) });
                this.regressionGenerator.x(d => d.time);
                break;
            case GraphAxis.Distance:
                document.getElementById("dropdowngraphxaxis").innerText = "Distance";
                this.xScale = d3.scaleLinear().domain(d3.extent(this.convertedData.datas, function(d) { return (d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)); }));
                this.functionpath.x((d) => { return this.xScale(d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)) });
                this.regressionGenerator.x(d => d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000));
                break;
            case GraphAxis.MovingTime:
                document.getElementById("dropdowngraphxaxis").innerText = "Moving Time";
                this.xScale = d3.scaleTime().domain(d3.extent(this.convertedData.datas, function(d) { return d.moving_time; }));
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

        if (this.graphXAxis === GraphAxis.MovingTime) {
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
            .datum(this.graphYAxis === GraphAxis.Heartrate ? this.convertedData.datas.filter(d => d.heartrate !== -1) : this.convertedData.datas)
            .attr("fill", "none")
            .attr("stroke", "#ff8001")
            .attr("stroke-width", 2)
            .attr("class", "datapath")
            .attr("d", this.functionpath)

        if (this.graphYAxis === GraphAxis.VerticalSpeed) {
            this.areaUp = d3.area()
                .y0(this.yScale(0))
                .y1((d) => { if (d.vertical_speed>0) {return this.yScale(d.vertical_speed*(sharedObjects.metric?1:feet_per_m))}else{return this.yScale(0);}});

            this.areaDown = d3.area()
                .y0(this.yScale(0))
                .y1((d) => { if (d.vertical_speed<0) {return this.yScale(d.vertical_speed*(sharedObjects.metric?1:feet_per_m))}else{return this.yScale(0);}});


            switch (this.graphXAxis) {
                case GraphAxis.MovingTime:
                    this.areaUp.x((d) => { return this.xScale(d.moving_time); })
                    this.areaDown.x((d) => { return this.xScale(d.moving_time); })
                    break;
                case GraphAxis.ElapsedTime:
                    this.areaUp.x((d) => { return this.xScale(d.time); })
                    this.areaDown.x((d) => { return this.xScale(d.time); })
                    break;
                case GraphAxis.Distance:
                    this.areaUp.x((d) => { return this.xScale(d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)); })
                    this.areaDown.x((d) => { return this.xScale(d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)); })
                    break;
            }

            this.svg.append("path")
                .datum(this.convertedData.datas)
                .attr("class","areaUp")
                .attr("fill", "#ffd3fe")
                .attr("d", this.areaUp)

            this.svg.append("path")
                .datum(this.convertedData.datas)
                .attr("class","areaUp")
                .attr("fill", "#caf6b9")
                .attr("d", this.areaDown)
        }

        if (this.graphYAxis === GraphAxis.Heartrate) {
            heartrateZones.forEach((zone, index) => {
                let lower_zone_limit = index===0 ? 0 : heartrateZones[index-1].zoneThreshold;
                let y1 = zone.zoneThreshold;

                let zoneBand = d3.area()
                    .y0(Math.min(this.yScale(lower_zone_limit), this.height))
                    .y1((d) => {
                        if (lower_zone_limit <= d.heartrate)
                            return this.yScale(d.heartrate);
                        else
                            return Math.min(this.yScale(lower_zone_limit), this.height);
                    })
                    .x((d) => {
                        switch (this.graphXAxis) {
                            case GraphAxis.MovingTime:
                                return this.xScale(d.moving_time);
                            case GraphAxis.ElapsedTime:
                                return this.xScale(d.time);
                            case GraphAxis.Distance:
                                return this.xScale(d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000));
                        }
                    })

                this.svg.append("path")
                    .datum(this.convertedData.datas)
                    .attr("class","zoneband"+index)
                    .attr("fill", getHeartrateZoneColors(index, heartrateZones))
                    .attr("d", zoneBand)
            })
        } else if (this.graphYAxis === GraphAxis.HorizontalSpeed || this.graphYAxis === GraphAxis.VerticalSpeed) {
            this.regressionpath = d3.line()
                .x(d => this.xScale(d[0]))
                .y(d => this.yScale(d[1]));

            d3.select(".datapath")
                .attr("stroke", "rgb(126,126,126)")
                .attr("stroke-width", 1);

            this.svg.append("path")
                .datum(this.regressionGenerator(this.convertedData.datas))
                .attr("class","graphregression")
                .attr("fill", "none")
                .attr("stroke", "#ff8001")
                .attr("stroke-width", 2)
                .attr("d", this.regressionpath)
        }

        // This allows to find the closest X index of the mouse:
        this.bisect;
        switch (this.graphXAxis) {
            case GraphAxis.MovingTime:
                this.bisect = d3.bisector(function(d) { return d.moving_time; }).left;
                break;
            case GraphAxis.ElapsedTime:
                this.bisect = d3.bisector(function(d) { return d.time; }).left;
                break;
            case GraphAxis.Distance:
                this.bisect = d3.bisector(function(d) { return (d.distance*(sharedObjects.metric?1/1000:miles_per_km/1000)); }).left;
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



        // Create a rect on top of the svg area: this rectangle recovers mouse position
        this.mouseSVG = this.svg
            .append('rect')
            .style("fill", "none")
            .style("pointer-events", "all")
            .attr('width', this.width)
            .attr('height', this.height)
            .on('mousemove', this.mousemove)
            .on('mouseout', this.mouseout);
    }

    mousemove = (event) => {
        const [x] = d3.pointer(event, event.target);
        const x0 = this.xScale.invert(x);
        const i = this.bisect(this.convertedData.datas, x0, 1);
        const selectedData = this.convertedData.datas[i];
        if (!selectedData) return;

        const [lon, lat] = getJaggedElement(this.jsonData.geometry.coordinates, i);
        eventBus.emit('moveMarkers', { lon, lat, datasIndex: i });
    }

    mouseout = () => {
        eventBus.emit('hideMarkers', {});
    }

    moveMarker = (datasIndex) => {
        let selectedData = this.convertedData.datas[datasIndex];
        let xtext, ytext, xdata, ydata;

        switch (this.graphXAxis) {
            case GraphAxis.MovingTime:
                xdata = selectedData.moving_time;
                xtext = d3.utcFormat("%H:%M")(xdata) + " h";
                break;
            case GraphAxis.ElapsedTime:
                xdata = selectedData.time;
                xtext = d3.timeFormat("%H:%M")(xdata);
                break;
            case GraphAxis.Distance:
                xdata = (selectedData.distance*(sharedObjects.metric?1/1000:miles_per_km/1000));
                xtext = (xdata).toFixed(2) + (sharedObjects.metric?" km":" mi");
                break;
        }

        switch (this.graphYAxis) {
            case GraphAxis.Distance:
                ydata = (selectedData.distance*(sharedObjects.metric?1/1000:miles_per_km/1000));
                ytext = (ydata).toFixed(2) + (sharedObjects.metric?" km":" mi");
                break;
            case GraphAxis.Elevation:
                ydata = (selectedData.altitude*(sharedObjects.metric?1:feet_per_m));
                ytext = ydata.toFixed(0) + (sharedObjects.metric?" m":" ft");
                break;
            case GraphAxis.VerticalSpeed:
                ydata = (selectedData.vertical_speed*(sharedObjects.metric?1:feet_per_m));
                ytext = ydata.toFixed(1) + (sharedObjects.metric?" m/h":" ft/h");
                break;
            case GraphAxis.HorizontalSpeed:
                ydata = (selectedData.horizontal_speed*(sharedObjects.metric?1:miles_per_km));
                ytext = ydata.toFixed(1) + (sharedObjects.metric?" km/h":" mph");
                break;
            case GraphAxis.Heartrate:
                ydata = (selectedData.heartrate);
                ytext = ydata.toFixed(0) + (" bpm");
                break;
        }

        this.focus
            .attr("cx", this.xScale(xdata))
            .attr("cy", this.yScale(ydata))
        this.xfocusText
            .html(xtext)
            .attr("x", this.xScale(xdata)+15)
            .attr("y", this.yScale(ydata))
        this.yfocusText
            .html(ytext)
            .attr("x", this.xScale(xdata)+15)
            .attr("y", this.yScale(ydata)+15)

        this.focus.style("opacity", 1)
        this.xfocusText.style("opacity",1)
        this.yfocusText.style("opacity",1)
    }

    hideMarker = () =>  {
        this.focus.style("opacity", 0)
        this.xfocusText.style("opacity", 0)
        this.yfocusText.style("opacity", 0);
    }
}

class GraphAxis {
    // Create new instances of the same class as static attributes
    static Elevation = new GraphAxis("elevation");
    static ElapsedTime = new GraphAxis("elapsed_time");
    static MovingTime = new GraphAxis("moving_time");
    static Distance = new GraphAxis("distance");
    static HorizontalSpeed = new GraphAxis("horizontal_speed");
    static VerticalSpeed = new GraphAxis("vertical_speed");
    static Heartrate = new GraphAxis("heart_rate");

    constructor(name) {
        this.name = name
    }
}

function getJaggedElement(arr, index) {
    let leftover = index;

    for (let i = 0; i < arr.length; i++) {
        if (leftover < arr[i].length) {
            return arr[i][leftover];
        } else {
            leftover -= arr[i].length;
        }
    }
    return undefined;
}

function getHeartrateZoneColors(index, heartrateZones) {
    return d3.interpolateOrRd((index + 0.5) / heartrateZones.length);
}

export class GraphHeartrate {
    constructor(convertedDatas, donut_element, heartrate_everage_element, heartrate_max_element) {
        this.datas = convertedDatas.datas.filter(item => item.heartrate !== -1);
        this.donut_element = d3.select(donut_element);
        this.heartrate_everage_element = heartrate_everage_element;
        this.heartrate_max_element = heartrate_max_element;

        this.heartrateBuckets = new Array(heartrateZones.length).fill(0);

        let heartrateSum = 0;

        for (let i = 1; i < this.datas.length; i++) {
            let time = (this.datas[i].moving_time - this.datas[i-1].moving_time)/1000; //in secs
            let avgHeartrate = ((this.datas[i].heartrate+this.datas[i-1].heartrate)/2);
            heartrateSum += time * avgHeartrate;
            this.heartrateBuckets[this._getZone(avgHeartrate)] += time;
        }
        this.averageHeartrate = heartrateSum*1000 / convertedDatas.movingTime;
        this.maxHeartrate = Math.max(...this.datas.map(item => item.heartrate));

        if (this.heartrate_everage_element)
            this.heartrate_everage_element.innerText = Math.floor(this.averageHeartrate) + " bpm";
        if (this.heartrate_max_element)
            this.heartrate_max_element.innerText = Math.floor(this.maxHeartrate)  + " bpm";

        if (d3.select("body").select(".tooltip").empty()) {
            this.tooltip = d3.select("body")
                .append("div")
                .attr("class", "tooltip")
                .style("position", "absolute")
                .style("opacity", 0)
                .attr("text-anchor", "left")
                .attr("alignment-baseline", "middle");
        } else {
            this.tooltip = d3.select("body").select(".tooltip");
        }

        this.drawDonut();
    }

    _getZone(heartrate) {
        return heartrateZones.findIndex((zone) => heartrate < zone.zoneThreshold);
    }

    drawDonut() {
        const width = this.donut_element.node().clientWidth;
        const height = this.donut_element.node().clientHeight;

        const radius = Math.min(width, height) / 2;

        this.svg = this.donut_element
            .append("svg")
            .attr("width", width)
            .attr("height", height)
            .append("g")
            .attr("transform",
                "translate(" + width/2 + "," + height/2 + ")");

        const arc = d3.arc()
            .innerRadius(radius * 0.4)
            .outerRadius(radius * 0.95);

        const arcOver = d3.arc()
            .innerRadius(radius * 0.35)
            .outerRadius(radius);


        const pie = d3.pie()
            .padAngle(1 / radius)
            .sort(null)
            .value(d => d);


        const self = this;

        this.svg.append("g")
            .selectAll()
            .data(pie(this.heartrateBuckets))
            .join("path")
            .attr("fill", (d,i) => getHeartrateZoneColors(i, heartrateZones))
            .attr("d", arc)
            .on('mousemove', (event, d) => this.mousemove(event, d))
            .on("mouseover", function() {
                d3.select(this) // "this" is the path
                    .transition()
                    .duration(200)
                    .attr("d", arcOver);
            })
            .on("mouseout", function() {
                d3.select(this)
                    .transition()
                    .duration(200)
                    .attr("d", arc);
                self.tooltip.style("opacity", 0);
            });



    }

    mousemove(event, d) {
        const [x, y] = d3.pointer(event, this.svg.node());

        const hours = Math.floor(d.value / 3600);
        const minutes = Math.floor((d.value % 3600) / 60);
        const seconds = d.value % 60;
        const hh = String(hours).padStart(2, "0");
        const mm = String(minutes).padStart(2, "0");
        const ss = String(seconds).padStart(2, "0");

        const stringtime = hours===0?`${mm}:${ss}`:`${hh}:${mm}:${ss}`;

        this.tooltip
            .style("left", (event.pageX + 10) + "px")
            .style("top", (event.pageY + 10) + "px")
            .html(`${heartrateZones[d.index].zoneName} - ${stringtime}`);
        this.tooltip.style("opacity",1)
    }
}