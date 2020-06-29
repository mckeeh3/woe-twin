
const deviceDataMsInterval = 250;

let worldMap;
let canvas;
let mouseSelectionWidth;
let areaSelectionOn = false;
let areaSelectionAction = "";
let areaSelectionColor = [0, 0, 0, 0];
let deviceSelections = [];

const drawFPS = 30;
const gridLatLines = [];
const gridLngLines = [];
const selectedRegionColor = [212, 0, 255, 48];
const areaSelectionColorCreate = [212, 0, 255, 50];
const areaSelectionColorDelete = [64, 64, 64, 50];
const areaSelectionColorHappy = [0, 255, 0, 50];
const areaSelectionColorSad = [255, 0, 0, 50];
const selectedMarkerColor = [33, 183, 0];
const mappa = new Mappa('Leaflet');
const mapOptions = {
  lat: 0,
  lng: 0,
  zoom: 3,
  style: "https://{s}.tile.osm.org/{z}/{x}/{y}.png"
}

function setup() {
  canvas = createCanvas(windowWidth, windowHeight);
  frameRate(drawFPS);
  mouseSelectionWidth = Math.min(windowWidth, windowHeight) / 10;

  worldMap = mappa.tileMap(mapOptions);
  worldMap.overlay(canvas, mapReady);
  worldMap.onChange(mapChanged);

  scheduleNextDeviceQuery();
}

function draw() {
  clear();
  drawLatLngGrid();
  drawDashboard();
  drawMouseLocation();
  drawDeviceSelections();
}

function drawLatLngGrid() {
  stroke(175);
  strokeWeight(0.5);

  gridLatLines.forEach(latLine => line(0, latLine.y, windowWidth - 1, latLine.y));
  gridLngLines.forEach(lngLine => line(lngLine.x, 0, lngLine.x, windowHeight - 1));
}

function drawMouseLocation() {
  if (areaSelectionOn) {
    drawMouseSectors();
  }
}

function drawMouseSectors() {
  const loc = mouseGridLocation();
  if (loc.inGrid) {
    fill(color(areaSelectionColor));
    strokeWeight(0);
    rect(loc.rect.x, loc.rect.y, loc.rect.w, loc.rect.h);
  }
}

function drawDashboard() {
  const height = 100;
  const top = windowHeight - height;
  fill(color(32, 128));
  strokeWeight(0);
  rect(0, top, windowWidth, height);
  const latLng = worldMap.pixelToLatLng(mouseX, mouseY);
  const lat = latLng.lat.toFixed(8);
  const lng = latLng.lng.toFixed(8);
  const zoom = worldMap.zoom();
  const msg = `Lat ${lat}, Lng ${lng} Zoom ${zoom}`;

  textSize(20);
  fill(color(255, 200, 0));
  text(msg, 25, top + 30);
}

function mouseGridLocation() {
  const indexes = mouseGridIndexes();

  if (indexes.latIndex >= 0 && indexes.lngIndex >= 0) {
    return {
      inGrid: true,
      rect: {
        x: gridLngLines[indexes.lngIndex].x,
        w: gridLngLines[indexes.lngIndex + 1].x - gridLngLines[indexes.lngIndex].x,
        y: gridLatLines[indexes.latIndex].y,
        h: gridLatLines[indexes.latIndex + 1].y - gridLatLines[indexes.latIndex].y
      },
      map: {
        topLeft: {
          lat: gridLatLines[indexes.latIndex].lat,
          lng: gridLngLines[indexes.lngIndex].lng
        },
        botRight: {
          lat: gridLatLines[indexes.latIndex + 1].lat,
          lng: gridLngLines[indexes.lngIndex + 1].lng
        }
      }
    };
  } else {
    return {
      inGrid: false
    };
  }
}

function mouseGridIndexes() {
  const latLngMouse = worldMap.pixelToLatLng(mouseX, mouseY);
  const latIndex = gridLatLines.findIndex((line, index) => indexOk(index, gridLatLines.length) && isMouseBetweenLatLines(index));
  const lngIndex = gridLngLines.findIndex((line, index) => indexOk(index, gridLngLines.length) && isMouseBetweenLngLines(index));

  return { latIndex: latIndex, lngIndex: lngIndex };

  function indexOk(index, length) {
    return index >= 0 && index < length - 1;
  }
  function isMouseBetweenLatLines(index) {
    return gridLatLines[index].lat > latLngMouse.lat && gridLatLines[index + 1].lat < latLngMouse.lat;
  }
  function isMouseBetweenLngLines(index) {
    return gridLngLines[index].lng < latLngMouse.lng && gridLngLines[index + 1].lng > latLngMouse.lng;
  }
}

function mouseClicked(event) {
  if (areaSelectionOn) {
    const loc = mouseGridLocation();
    if (loc.inGrid) {
      const selection = {
        action: areaSelectionAction,
        zoom: worldMap.zoom(),
        topLeftLat: loc.map.topLeft.lat,
        topLeftLng: loc.map.topLeft.lng,
        botRightLat: loc.map.botRight.lat,
        botRightLng: loc.map.botRight.lng
      };
      const xhr = new XMLHttpRequest();
      xhr.open("POST", location + "selection");
      xhr.setRequestHeader("Content-Type", "application/json");
      xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {
          const json = JSON.parse(xhr.responseText);
          console.log(json);
        }
      };
      const entity = JSON.stringify(selection);
      xhr.send(entity);
    }
    areaSelectionOn = false;
  }
}

function drawDeviceSelections() {
  deviceSelections.forEach(d => DrawDeviceSelection(d));
}

function DrawDeviceSelection(deviceSelection) {
  const posTopLeft = worldMap.latLngToPixel(deviceSelection.region.topLeft);
  const posBotRight = worldMap.latLngToPixel(deviceSelection.region.botRight);

  if (isPartial(deviceSelection)) {
    drawAsMarker(posTopLeft, posBotRight, markerColorFor(deviceSelection));
  } else {
    drawAsRegion(posTopLeft, posBotRight, regionColorFor(deviceSelection));
  }

  function drawAsMarker(posTopLeft, posBotRight, markerColor) {
    const w = posBotRight.x - posTopLeft.x;
    const h = posBotRight.y - posTopLeft.y;
    const x1 = posTopLeft.x + w / 2;
    const y1 = posTopLeft.y + h / 2;
    const x2 = x1 - 10;
    const y2 = y1 - 20;
    const x3 = x1;
    const y3 = y1 - 30;
    const x4 = x1 + 10;
    const y4 = y2;
    fill(color(markerColor));
    strokeWeight(0);
    quad(x1, y1, x2, y2, x3, y3, x4, y4);
  }

  function drawAsRegion(posTopLeft, posBotRight, regionColor) {
    const x = posTopLeft.x;
    const y = posTopLeft.y;
    const w = posBotRight.x - x;
    const h = posBotRight.y - y;
    fill(color(regionColor));
    strokeWeight(0);
    rect(x, y, w, h);
  }

  function markerColorFor(deviceSelection) {
    if (isHappy(deviceSelection)) {
      return [0, 171, 23];
    } else if (isSad(deviceSelection)) {
      return [204, 10, 0];
    } else {
      return [209, 182, 0];
    }
  }

  function regionColorFor(deviceSelection) {
    if (isHappy(deviceSelection)) {
      return [0, 235, 6, 100];
    } else if (isSad(deviceSelection)) {
      return [240, 9, 0, 100];
    } else {
      return [250, 182, 13, 100];
    }
  }

  function isHappy(deviceSelection) {
    return deviceSelection.happyCount > 0 && deviceSelection.sadCount == 0;
  }

  function isSad(deviceSelection) {
    return deviceSelection.happyCount == 0 && deviceSelection.sadCount > 0;
  }

  function isPartial(deviceSelection) {
    return 0.5 > deviceSelection.deviceCount / maxDevicesIn(deviceSelection);
  }

  function maxDevicesIn(deviceSelection) {
    return Math.pow(4, 18 - deviceSelection.region.zoom);
  }
}

function windowResized() {
  let style = getStyleByClassName("leaflet-container");
  if (style) {
    style.width = windowWidth + "px";
    style.height = windowHeight + "px";
  }
  worldMap.map.invalidateSize();
  resizeCanvas(windowWidth, windowHeight);
  mouseSelectionWidth = Math.min(windowWidth, windowHeight) / 10;
}

function getStyleByClassName(className) {
  const element = document.getElementsByClassName(className);
  return (element && element[0]) ? element[0].style : undefined;
}

function keyPressed() {
  switch (key) {
    case "C":
      areaSelectionOn = !areaSelectionOn;
      areaSelectionAction = "create";
      areaSelectionColor = areaSelectionColorCreate;
      break;
    case "D":
      areaSelectionOn = !areaSelectionOn;
      areaSelectionAction = "delete";
      areaSelectionColor = areaSelectionColorDelete;
      break;
    case "H":
      areaSelectionOn = !areaSelectionOn;
      areaSelectionAction = "happy";
      areaSelectionColor = areaSelectionColorHappy;
      break;
    case "S":
      areaSelectionOn = !areaSelectionOn;
      areaSelectionAction = "sad";
      areaSelectionColor = areaSelectionColorSad;
      break;
  }
}

function mapReady() {
  worldMap.map.setMinZoom(3);
}

function mapChanged() {
  recalculateLatLngGrid();
}

function recalculateLatLngGrid() {
  const zoom = worldMap.getZoom();
  const totalLatLines = 9 * Math.pow(2, zoom - 3);
  const totalLngLines = 18 * Math.pow(2, zoom - 3);
  const tickLenLat = 180 / totalLatLines;
  const tickLenLng = 360 / totalLngLines;
  const latLngTopLeft = worldMap.pixelToLatLng(0, 0);
  const latLngBotRight = worldMap.pixelToLatLng(windowWidth - 1, windowHeight - 1);
  const topLatGridLine = tickLenLat * (Math.trunc(latLngTopLeft.lat / tickLenLat));
  const leftLngGridLine = tickLenLng * (Math.trunc(latLngTopLeft.lng / tickLenLng));
  console.log(`zoom ${zoom}, tick len lat ${tickLenLat}, lng ${tickLenLng}` );

  gridLatLines.length = 0;
  gridLngLines.length = 0;

  let latGridLine = topLatGridLine;
  while (latGridLine > latLngBotRight.lat) {
    const latY = worldMap.latLngToPixel({ lat: latGridLine, lng: 0 }).y;
    gridLatLines.push({ lat: latGridLine, y: latY });
    latGridLine -= tickLenLat;
  }
  let lngGridLine = leftLngGridLine;
  while (lngGridLine < latLngBotRight.lng) {
    const lngX = worldMap.latLngToPixel({ lat: 0, lng: lngGridLine }).x;
    gridLngLines.push({ lng: lngGridLine, x: lngX });
    lngGridLine += tickLenLng;
  }
}

function scheduleNextDeviceQuery() {
  setTimeout(deviceQueryInterval, deviceDataMsInterval);
}

function deviceQueryInterval() {
  var start = performance.now();
  const topLeft = worldMap.pixelToLatLng(0, 0);
  const botRight = worldMap.pixelToLatLng(windowWidth - 1, windowHeight - 1);

  httpPost(
    location + "query-devices",
    "json",
    {
      zoom: Math.min(18, worldMap.getZoom() + 2),
      topLeft: {
        lat: topLeft.lat,
        lng: topLeft.lng
      },
      botRight: {
        lat: botRight.lat,
        lng: botRight.lng
      }
    },
    function (result) {
      console.log((new Date()).toISOString() + " UI query " + (performance.now() - start) + "ns");
      deviceSelections = result;
      scheduleNextDeviceQuery();
    },
    function (error) {
      console.log(error);
      scheduleNextDeviceQuery();
    }
  );
}
