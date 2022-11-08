var kdTreeScript = require("./kd-tree.js");

var points = [
        [-93.644346, 32.831718, "bla"],
        [-132.701578, -79.916542, "bla"],
        [74.115335, -20.866482, "bla"],
        [-79.272251, -27.737648, "bla"],
        [73.539323, 12.806696, "bla"],
        [54.706111, -51.355516, "bla"],
        [-165.771653, -74.537204, "bla"],
        [150.356346, 85.163403, "bla"],
        [81.45228, -82.394628, "bla"],
        [-151.595094, 62.69771, "bla"]
];

var distance = function(a, b){
    return Math.pow(a[0] - b[0], 2) +  Math.pow(a[1] - b[1], 2);
}

var tree = new kdTreeScript.kdTree(points, distance);

var nearest = tree.nearest([-151.595094, 65.69771], 1, 0.01);


console.log(points.indexOf(nearest));
console.log(nearest);