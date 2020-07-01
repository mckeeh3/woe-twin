
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
const minSelectableZoom = 9; // up to 262,144 devices, Math.pow(4, 9)
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

  grid.resize();

  scheduleNextDeviceQuery();
}

function draw() {
  clear();
  drawLatLngGrid();
  drawDeviceSelections();
  drawDashboard();
  drawMouseLocation();
}

function drawLatLngGrid() {
  stroke(175);
  strokeWeight(0.5);

  gridLatLines.forEach(latLine => line(0, latLine.y, windowWidth - 1, latLine.y));
  gridLngLines.forEach(lngLine => line(lngLine.x, 0, lngLine.x, windowHeight - 1));
}

function drawMouseLocation() {
  if (areaSelectionOn) {
    if (isAreaSelectionAllowed()) {
      drawMouseSectors();
    } else {
      areaSelectionOn = false;
    }
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
  drawZoomAndMouseLocation();
  drawSelectionInstructions();
  drawSelectionCounts();
}

function drawZoomAndMouseLocation() {
  const latLng = worldMap.pixelToLatLng(mouseX, mouseY);
  const lat = latLng.lat.toFixed(8);
  const lng = latLng.lng.toFixed(8);
  const height = 1.7;
  const keyColor = color(255, 255, 0);
  const valueColor = color(255);
  const bgColor = color(50, 100);

  Label().setX(2).setY(0.25).setW(5).setH(height)
          .setBorder(0.3)
          .setKey("Zoom")
          .setValue(worldMap.zoom())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(7.5).setY(0.25).setW(10).setH(height)
          .setBorder(0.3)
          .setKey("Lat")
          .setValue(lat)
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(18).setY(0.25).setW(10).setH(height)
          .setBorder(0.3)
          .setKey("Lng")
          .setValue(lng)
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
}

function drawSelectionInstructions() {
  if (!isAreaSelectionAllowed()) return;

  const zoom = worldMap.zoom();
  const height = 1.7;
  const keyColor = color(255, 255, 0);
  const valueColor = color(255);
  const bgColor = color(50, 100);

  Label().setX(grid.ticksHorizontal - 6).setY(0.25).setW(6).setH(height)
          .setBorder(0.3)
          .setKey("'c'")
          .setValue("create")
          .setBgColor(bgColor)
          .setKeyColor(color(212, 0, 255))
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(2).setW(6).setH(height)
          .setBorder(0.3)
          .setKey("'d'")
          .setValue("delete")
          .setBgColor(bgColor)
          .setKeyColor(color(50, 50, 50))
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(3.75).setW(6).setH(height)
          .setBorder(0.3)
          .setKey("'h'")
          .setValue("happy")
          .setBgColor(bgColor)
          .setKeyColor(color(0, 255, 0))
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(5.5).setW(6).setH(height)
          .setBorder(0.3)
          .setKey("'s'")
          .setValue("sad")
          .setBgColor(bgColor)
          .setKeyColor(color(255, 0, 0))
          .setValueColor(valueColor)
          .draw();

  if (areaSelectionOn && areaSelectionAction == "create") {
    const devicesAtZoom = Math.pow(4, 18 - zoom);
    const msg = `${devicesAtZoom.toLocaleString()} devices`

    Label().setX(grid.ticksHorizontal - 17).setY(0.25).setW(10).setH(height)
            .setBorder(0.3)
            .setValue(msg)
            .setBgColor(bgColor)
            .setValueColor(color(255))
            .draw();
  }
}

function drawSelectionCounts() {
  const height = 1.6;
  const bgColor = color(50, 150);
  const keyColor = color(255, 255, 0);
  const valueColor = color(255);
  const happyCounter = (a, c) => a + c.happyCount;
  const deviceCounter = (a, c) => a + c.deviceCount;
  const sadCounter = (a, c) => a + c.sadCount;
  const deviceTotal = deviceSelections.reduce(deviceCounter, 0);
  const happyTotal = deviceSelections.reduce(happyCounter, 0);
  const sadTotal = deviceSelections.reduce(sadCounter, 0);

  Label().setX(1).setY(grid.ticksVertical - 5.5).setW(20).setH(height)
          .setBorder(0.3)
          .setKey("Devices in view")
          .setValue(deviceTotal.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(1).setY(grid.ticksVertical - 3.75).setW(20).setH(height)
          .setBorder(0.3)
          .setKey("Happy status")
          .setValue(happyTotal.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(1).setY(grid.ticksVertical - 2).setW(20).setH(height)
          .setBorder(0.3)
          .setKey("Sad status")
          .setValue(sadTotal.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
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
  deviceSelections.forEach(d => drawDeviceSelection(d));
}

function drawDeviceSelection(deviceSelection) {
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

  grid.resize();
}

function getStyleByClassName(className) {
  const element = document.getElementsByClassName(className);
  return (element && element[0]) ? element[0].style : undefined;
}

function keyPressed() {
  if (!isAreaSelectionAllowed()) return;

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

function isAreaSelectionAllowed() {
  const zoom = worldMap.zoom();
  return zoom >= minSelectableZoom;
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
    function (response) {
      console.log((new Date()).toISOString() + " UI query " + (performance.now() - start) + "ms");
      deviceSelections = response;
      scheduleNextDeviceQuery();
    },
    function (error) {
      console.log(error);
      scheduleNextDeviceQuery();
    }
  );
}

const grid = {
  borderWidth: 20,
  ticksHorizontal: 100,
  ticksVertical: 0,
  tickWidth: 0,
  resize: function () {
    gridWidth = windowWidth - 2 * this.borderWidth;
    this.tickWidth = gridWidth / this.ticksHorizontal;
    this.ticksVertical = windowHeight / windowWidth * this.ticksHorizontal;
  },
  toX: function (gridX) { // convert from grid scale to canvas scale
    return this.borderWidth + gridX * this.tickWidth;
  },
  toY: function (gridY) {
    return this.borderWidth + gridY * this.tickWidth;
  },
  toLength: function (gridLength) {
    return gridLength * this.tickWidth
  },
  line: function (x1, y1, x2, y2) {
    line(grid.toX(x1), grid.toY(y1), grid.toX(x2), grid.toY(y2));
  },
  rect: function (x, y, w, h) {
    rect(grid.toX(x), grid.toY(y), grid.toLength(w), grid.toLength(h));
  }
};

let Label = function () {
  return {
    setX: function(x) { this.x = x; return this; },
    setY: function(y) { this.y = y; return this; },
    setW: function(w) { this.w = w; return this; },
    setH: function(h) { this.h = h; return this; },
    setBorder: function(b) { this.border = b; return this; },
    setKey: function(k) { this.key = k; return this; },
    setValue: function(v) { this.value = v; return this; },
    setBgColor: function(c) { this.bgColor = c; return this; },
    setKeyColor: function(c) { this.keyColor = c; return this; },
    setValueColor: function(c) { this.valueColor = c; return this; },
    draw: function() {
      const cx = grid.toX(this.x);
      const cy = grid.toY(this.y);
      const cw = grid.toLength(this.w);
      const ch = grid.toLength(this.h);
      const cb = grid.toLength(this.border);

      strokeWeight(0);
      fill(this.bgColor || color(0, 0));
      rect(cx, cy, cw, ch);

      textSize(ch - cb * 2);

      if (this.key) {
        textAlign(LEFT, CENTER);
        fill(this.keyColor || color(0, 0));
        text(this.key, cx + cb, cy + ch / 2);
      }

      if (this.value) {
        textAlign(RIGHT, CENTER);
        fill(this.valueColor || color(0, 0));
        text(this.value, cx + cw - cb, cy + ch / 2);
      }
    },
    Label: function() {
      if (!(this instanceof Label)) {
        return new Label();
      }
    }
  };
};
