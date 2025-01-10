const miles_per_km = 0.621371;
const feet_per_m = 3.28084;



export function settings() {
    if (localStorage.getItem("metric") === null) {
        localStorage.setItem("metric", true);
    } else {
        sharedObjects.metric = (localStorage.getItem("metric")==="true");
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById("metricChecked").addEventListener('click', clickSettingsMetric);
        if (sharedObjects.metric) {
            document.getElementById("metricChecked").checked = true;
            setMetric();
        } else {
            document.getElementById("metricChecked").checked = false;
            setMetric();
        }


        if (document.getElementById("favoritestar"))
            document.getElementById("favoritestar").addEventListener('click', clickfavorite);
        if (document.getElementById("sharebutton"))
            document.getElementById("sharebutton").addEventListener('click', clickshare);
        if (document.getElementById("favoriteChecked"))
            document.getElementById("favoriteChecked").addEventListener('click', clickfavorite);
        if (document.getElementById("publicChecked"))
            document.getElementById("publicChecked").addEventListener('click', () => {setTrackShare(undefined)});
        if (document.getElementById("hiddenChecked"))
            document.getElementById("hiddenChecked").addEventListener('click', () => {clickhide(false)});
        if (document.getElementById("deleteTrack"))
            document.getElementById("deleteTrack").addEventListener('click', () => {clickdelete(false)});
        if (document.getElementById("saveEditButton"))
            document.getElementById("saveEditButton").addEventListener('click', saveEdit);
        if (document.getElementById("saveEditButton2"))
            document.getElementById("saveEditButton2").addEventListener('click', saveEdit);
        if (document.getElementById("hideTrackButton"))
            document.getElementById("hideTrackButton").addEventListener('click', () => {clickhide(true)});
        if (document.getElementById("deleteTrackButton"))
            document.getElementById("deleteTrackButton").addEventListener('click', () => {clickdelete(true)});
        if (document.getElementById("trackShareButton"))
            document.getElementById("trackShareButton").addEventListener('click', () => {setTrackShare('PUBLIC')});
        if (document.getElementById("recalculateHeightButton"))
            document.getElementById("recalculateHeightButton").addEventListener('click', recalculateHeight);


        if (document.getElementById("copylinktoclipboard"))
            document.getElementById("copylinktoclipboard").addEventListener('click', copylinktoclipboard);
        if (document.getElementById("inputTitle")) {
            document.getElementById("inputTitle").addEventListener("keypress", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    event.stopPropagation()
                    event.stopImmediatePropagation();
                    saveEdit();
                }
            });
        }
    });

}


export function clickSettingsMetric() {
    sharedObjects.metric = document.getElementById("metricChecked").checked;
    localStorage.setItem("metric",sharedObjects.metric);
    setMetric();
}

function setMetric() {
    document.getElementById("metricCheckedLabel").innerText=(sharedObjects.metric?"Metric Units":"Imperial Units");
    document.getElementById("value_distance").innerText = (sharedObjects.distance/1000*(sharedObjects.metric?1:miles_per_km)).toFixed(1) + (sharedObjects.metric?" km":" mi");
    document.getElementById("value_elevation_up").innerText = (sharedObjects.elevationUp*(sharedObjects.metric?1:feet_per_m)).toFixed(0) + (sharedObjects.metric?" m":" ft");
    document.getElementById("value_elevation_down").innerText = (sharedObjects.elevationDown*(sharedObjects.metric?1:feet_per_m)).toFixed(0) + (sharedObjects.metric?" m":" ft");
    document.getElementById("value_highest_point").innerText = (sharedObjects.highestElevationPoint*(sharedObjects.metric?1:feet_per_m)).toFixed(0) + (sharedObjects.metric?" m":" ft");
    document.getElementById("value_lowest_point").innerText = (sharedObjects.lowestElevationPoint*(sharedObjects.metric?1:feet_per_m)).toFixed(0) + (sharedObjects.metric?" m":" ft");
    document.getElementById("value_horizontal_average").innerText=(sharedObjects.horizontal_average*(sharedObjects.metric?1:miles_per_km)).toFixed(1)+(sharedObjects.metric?" km/h":" mph");
    document.getElementById("value_vertical_down_average").innerText=(sharedObjects.ertical_down_average*(sharedObjects.metric?1:feet_per_m)).toFixed(1)+(sharedObjects.metric?" m/h":" ft/h");
    document.getElementById("value_vertical_up_average").innerText=(sharedObjects.vertical_up_average*(sharedObjects.metric?1:feet_per_m)).toFixed(1)+(sharedObjects.metric?" m/h":" ft/h")

    //graph = new drawGraph(graphYAxis, graphXAxis);
}

function saveEdit() {
    let editTitle = document.getElementById("inputTitle").value;
    let editType = document.getElementById("inputType").value;
    let editComment = document.getElementById("inputComment").value;

    let data = {index: sharedObjects.trackid, title: editTitle, activitytype: editType, note: editComment};

    fetch(sharedObjects.root+"modify", {
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

function recalculateHeight() {
    fetch(sharedObjects.root+"modify/recalculateHeight/id="+sharedObjects.trackid, {
        method: 'GET'
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {

                var myHeaders = new Headers();
                myHeaders.append('pragma', 'no-cache');
                myHeaders.append('cache-control', 'no-cache');

                //force cache override
                fetch(sharedObjects.root + "geojson/"+sharedObjects.trackid+".geojson", {
                    method: 'GET',
                    headers: myHeaders,
                    cache: 'reload'
                }).then(response => {
                    location.reload()
                }
            )
            } else {
                //not good
            }
        });
    }).catch(error => {
        console.log("--error");
        console.log(error);
    });
}

function clickfavorite() {
    fetch(sharedObjects.root+"modify/id="+sharedObjects.trackid+"&favorite="+(!sharedObjects.favorite), {
        method: "GET"
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                sharedObjects.favorite = !sharedObjects.favorite;
                if (sharedObjects.favorite)
                    document.getElementById("favoritestar").src="../assets/icon_favorite_select.svg";
                else
                    document.getElementById("favoritestar").src="../assets/icon_favorite_unselect.svg";

                document.getElementById("favoriteChecked").checked = sharedObjects.favorite;
                if (sharedObjects.hidden)
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
    if (!sharedObjects.hidden && !confirmed) {
        bootstrap.Modal.getOrCreateInstance(document.getElementById('confirmhidden')).show();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('settingsModal')).hide();
        document.getElementById("hiddenChecked").checked = false;
        return;
    }
    fetch(sharedObjects.root+"modify/id="+sharedObjects.trackid+"&hidden="+(!sharedObjects.hidden), {
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
    fetch(sharedObjects.root + "modify/id=" + sharedObjects.trackid, {
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
    document.getElementById("sharelink").value = window.location.origin + window.location.pathname;
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
        if (sharedObjects.sharing === "PUBLIC")
            setTrackShare("PRIVATE");
        else
            setTrackShare("PUBLIC");
        return;
    }
    fetch(sharedObjects.root+"modify/id="+sharedObjects.trackid+"&sharing="+state, {
        method: "GET"
    }).then(res => {
        res.json().then(response => {
            if (res.ok) {
                sharedObjects.sharing = state;
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