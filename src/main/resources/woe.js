
const deviceDataMsInterval = 250;

let worldMap;
let canvas;
let mouseSelectionWidth;
let areaSelectionOn = false;
let areaSelectionRate = 1000;
let areaSelectionAction = "";
let areaSelectionColor = [0, 0, 0, 0];
let queryResponse = {};
queryResponse.deviceCount = 0;
queryResponse.happyCount = 0;
queryResponse.sadCount = 0;
queryResponse.regionSummaries = [];
let activityMonitor = {};

const drawFPS = 30;
const gridLatLines = [];
const gridLngLines = [];
const selectedRegionColor = [212, 0, 255, 48];
const areaSelectionColorCreate = [212, 0, 255, 100];
const areaSelectionColorDelete = [64, 64, 64, 100];
const areaSelectionColorHappy = [0, 255, 0, 100];
const areaSelectionColorSad = [255, 0, 0, 100];
const selectedMarkerColor = [33, 183, 0];
const minSelectableZoom = 8; // up to 1,048,576 devices, Math.pow(4, 18 - 8)
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
  initActivityMonitor();
}

function draw() {
  clear();
  drawLatLngGrid();
  drawDeviceSelections();
  drawDashboard();
  drawMouseLocation();
  drawActivityMonitor();
}

function drawLatLngGrid() {
  stroke(125);
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
  } else {
    drawDeviceCountWithinMouseRegion();
  }
}

function drawMouseSectors() {
  const loc = mouseGridLocation();
  if (loc.inGrid) {
    fill(color(areaSelectionColor));
    strokeWeight(0);
    rect(loc.rect.x, loc.rect.y, loc.rect.w, loc.rect.h);
    const deviceDensity = Math.pow(4, 18 - worldMap.zoom());
    const durationSec = Math.round(deviceDensity / areaSelectionRate);

    if (durationSec > 0) {
      Label().setX(grid.toGridX(loc.rect.x))
              .setBorder(0.15)
              .setY(grid.toGridY(loc.rect.y))
              .setW(grid.toGridLength(loc.rect.w))
              .setH(1)
              .setKey(durationFormat(durationSec))
              .setKeyColor(color(75, 75, 125))
              .setBgColor(color(225, 225, 225, 150))
              .draw();
    }
  }

  function durationFormat(seconds) {
    const d = new Date(0);
    d.setSeconds(seconds);
    const h = d.getUTCHours();
    const m = d.getUTCMinutes();
    const s = d.getSeconds();
    return "PT" + (h > 0 ? h + "H" : "") + (m > 0 ? m + "M" : "") + (s > 0 ? s + "S" : "");
  }
}

function drawDeviceCountWithinMouseRegion() {
  const loc = mouseGridLocation();
  if (loc.inGrid) {
    const counts = deviceCounts(findSelectionsUnderMouse(loc));
    if (counts.deviceCount > 0) {
      const x = grid.toGridX(loc.rect.x);
      const y = grid.toGridY(loc.rect.y);
      const w = grid.toGridLength(loc.rect.w);
      const h = 1;
      let yOffset = y;
      outlineRegion();
      drawCount(counts.deviceCount.toLocaleString(), x, y, w, h, color(75, 75, 125));
      if (counts.happyCount > 0 && counts.happyCount != counts.deviceCount) {
        yOffset += h + 0.05;
        drawCount(counts.happyCount.toLocaleString() + "-H", x, yOffset, w, h, color(28, 98, 0));
      }
      if (counts.sadCount > 0 && counts.sadCount != counts.deviceCount) {
        yOffset += h + 0.05;
        drawCount(counts.sadCount.toLocaleString() + "-S", x, yOffset, w, h, color(210, 42, 42));
      }
    }
  }

  function outlineRegion() {
    stroke(255, 251, 51);
    strokeWeight(2);
    noFill();
    rect(loc.rect.x, loc.rect.y, loc.rect.w, loc.rect.h);
  }

  function drawCount(count, x, y, w, h, countColor) {
    Label().setX(x).setY(y).setW(w).setH(h)
            .setBorder(0.1)
            .setKey(count)
            .setKeyColor(countColor)
            .setBgColor(color(255, 251, 51, 100))
            .draw();
  }

  function deviceCounts(selectionsUnderMouse) {
    const counts = { deviceCount: 0, happyCount: 0, sadCount: 0 };
    selectionsUnderMouse.forEach(s => {
      counts.deviceCount += s.deviceCount;
      counts.happyCount += s.happyCount;
      counts.sadCount += s.sadCount;
    });
    return counts;
  }

  function findSelectionsUnderMouse(loc) {
    const selections = [];
    queryResponse.regionSummaries.forEach(r => {
      if (overlaps(loc.map, r.region)) {
         selections.push(r);
      }
    });
    return selections;
  }

  function overlaps(mouse, region) {
    return !isMouseAbove(mouse, region) && !isMouseBelow(mouse, region) && !isMouseLeft(mouse, region) && !isMouseRight(mouse, region);
  }

  function isMouseAbove(mouse, region) {
    return mouse.botRight.lat >= region.topLeft.lat;
  }

  function isMouseBelow(mouse, region) {
    return mouse.topLeft.lat <= region.botRight.lat;
  }

  function isMouseLeft(mouse, region) {
    return mouse.botRight.lng <= region.topLeft.lng;
  }

  function isMouseRight(mouse, region) {
    return mouse.topLeft.lng >= region.botRight.lng;
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
  const height = 1.2;
  const border = 0.2;
  const keyColor = color(255, 255, 0);
  const valueColor = color(255);
  const bgColor = color(0, 0, 75, 125);

  Label().setX(2).setY(0.1).setW(5).setH(height)
          .setBorder(border)
          .setKey("Zoom")
          .setValue(19 - worldMap.zoom())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(7.05).setY(0.1).setW(8).setH(height)
          .setBorder(border)
          .setKey("Lat")
          .setValue(lat)
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(15.1).setY(0.1).setW(8).setH(height)
          .setBorder(border)
          .setKey("Lng")
          .setValue(lng)
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(2).setY(1.4).setW(9.5).setH(height - 0.15)
          .setBorder(border)
          .setKey("Device density")
          .setValue((Math.pow(4, 18 - worldMap.zoom()).toLocaleString()))
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
}

function drawSelectionInstructions() {
  if (!isAreaSelectionAllowed()) return;

  const zoom = worldMap.zoom();
  const height = 1.2;
  const border = 0.2;
  const keyColor = color(255, 255, 0);
  const valueColor = color(255);
  const bgColor = color(0, 0, 75, 125);

  Label().setX(grid.ticksHorizontal - 10.2).setY(offsetY(0)).setW(5.2).setH(height)
          .setBorder(border)
          .setKey("Rate +'R' -'r'")
          .setBgColor(color(0, 0, 204, 100))
          .setKeyColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 5).setY(offsetY(0)).setW(5).setH(height)
          .setBorder(border)
          .setValue(areaSelectionRate.toLocaleString() + "/s")
          .setBgColor(bgColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(offsetY(1)).setW(1.7).setH(height)
          .setBorder(border)
          .setKey("'c'")
          .setBgColor(areaSelectionColorCreate)
          .setKeyColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 4.3).setY(offsetY(1)).setW(4.3).setH(height)
          .setBorder(border)
          .setValue("create")
          .setBgColor(bgColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(offsetY(2)).setW(1.7).setH(height)
          .setBorder(border)
          .setKey("'d'")
          .setBgColor(areaSelectionColorDelete)
          .setKeyColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 4.3).setY(offsetY(2)).setW(4.3).setH(height)
          .setBorder(border)
          .setValue("delete")
          .setBgColor(bgColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(offsetY(3)).setW(1.7).setH(height)
          .setBorder(border)
          .setKey("'h'")
          .setBgColor(areaSelectionColorHappy)
          .setKeyColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 4.3).setY(offsetY(3)).setW(4.3).setH(height)
          .setBorder(border)
          .setValue("happy")
          .setBgColor(bgColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 6).setY(offsetY(4)).setW(1.7).setH(height)
          .setBorder(border)
          .setKey("'s'")
          .setBgColor(areaSelectionColorSad)
          .setKeyColor(color(255))
          .draw();
  Label().setX(grid.ticksHorizontal - 4.3).setY(offsetY(4)).setW(4.3).setH(height)
          .setBorder(border)
          .setValue("sad")
          .setBgColor(bgColor)
          .setValueColor(valueColor)
          .draw();

  if (areaSelectionOn) {
    const devicesAtZoom = Math.pow(4, 18 - zoom);
    const msg = `${devicesAtZoom.toLocaleString()} devices`

    let offset = 1;
    switch (areaSelectionAction) {
      case "create":
        offset = 1;
        break;
      case "delete":
        offset = 2;
        break;
      case "happy":
        offset = 3;
        break;
      case "sad":
        offset = 4;
        break;
    }
    Label().setX(grid.ticksHorizontal - 16.1).setY(offsetY(offset)).setW(10).setH(height)
            .setBorder(border)
            .setValue(msg)
            .setBgColor(bgColor)
            .setValueColor(color(255))
            .draw();
  }

  function offsetY(line) {
    return 0.1 + line * 1.3;
  }
}

function drawSelectionCounts() {
  const height = 1.2;
  const border = 0.2;
  const bgColor = color(0, 0, 75, 125);
  const keyColor = color(255, 255, 0);
  const valueColor = color(255);
  const deviceCounter = (a, c) => a + c.deviceCount;
  const happyCounter = (a, c) => a + c.happyCount;
  const sadCounter = (a, c) => a + c.sadCount;
  const deviceTotal = queryResponse.regionSummaries.reduce(deviceCounter, 0);
  const happyTotal = queryResponse.regionSummaries.reduce(happyCounter, 0);
  const sadTotal = queryResponse.regionSummaries.reduce(sadCounter, 0);

  Label().setX(1).setY(grid.ticksVertical - 4.6).setW(20).setH(height)
          .setBorder(border)
          .setKey("Devices in view")
          .setValue(deviceTotal.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(1).setY(grid.ticksVertical - 3.3).setW(20).setH(height)
          .setBorder(border)
          .setKey("Happy status")
          .setValue(happyTotal.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(1).setY(grid.ticksVertical - 2).setW(20).setH(height)
          .setBorder(border)
          .setKey("Sad status")
          .setValue(sadTotal.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();

  Label().setX(grid.ticksHorizontal - 21).setY(grid.ticksVertical - 4.6).setW(20).setH(height)
          .setBorder(border)
          .setKey("Devices worldwide")
          .setValue(queryResponse.deviceCount.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 21).setY(grid.ticksVertical - 3.3).setW(20).setH(height)
          .setBorder(border)
          .setKey("Happy status")
          .setValue(queryResponse.happyCount.toLocaleString())
          .setBgColor(bgColor)
          .setKeyColor(keyColor)
          .setValueColor(valueColor)
          .draw();
  Label().setX(grid.ticksHorizontal - 21).setY(grid.ticksVertical - 2).setW(20).setH(height)
          .setBorder(border)
          .setKey("Sad status")
          .setValue(queryResponse.sadCount.toLocaleString())
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
      httpPost(
        location + "selection",
        "json",
        {
          action: areaSelectionAction,
          rate: areaSelectionRate,
          zoom: worldMap.zoom(),
          topLeftLat: loc.map.topLeft.lat,
          topLeftLng: loc.map.topLeft.lng,
          botRightLat: loc.map.botRight.lat,
          botRightLng: loc.map.botRight.lng
        },
        function (response) {
          console.log(response);
        },
        function (error) {
          console.log(error);
        }
      );
    }
    areaSelectionOn = false;
  }
}

function drawDeviceSelections() {
  queryResponse.regionSummaries.forEach(d => drawDeviceSelection(d));
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

function keyTyped() {
  if (keyCode == 'r'.charCodeAt(0)) {
    if (areaSelectionRate > 10000000) {
      areaSelectionRate -= 10000000;
    } else if (areaSelectionRate > 1000000) {
      areaSelectionRate -= 1000000;
    } else if (areaSelectionRate > 100000) {
      areaSelectionRate -= 100000;
    } else if (areaSelectionRate > 10000) {
      areaSelectionRate -= 10000;
    } else if (areaSelectionRate > 1000) {
      areaSelectionRate -= 1000;
    } else if (areaSelectionRate > 100) {
      areaSelectionRate -= 100;
    }
    areaSelectionRate = Math.max(100, areaSelectionRate);
    console.log("Decrease " + areaSelectionRate.toLocaleString());
  } else if (keyCode == 'R'.charCodeAt(0)) {
    if (areaSelectionRate >= 10000000) {
      areaSelectionRate += 10000000;
    } else if (areaSelectionRate >= 1000000) {
      areaSelectionRate += 1000000;
    } else if (areaSelectionRate >= 100000) {
      areaSelectionRate += 100000;
    } else if (areaSelectionRate >= 10000) {
      areaSelectionRate += 10000;
    } else if (areaSelectionRate >= 1000) {
      areaSelectionRate += 1000;
    } else if (areaSelectionRate >= 100) {
      areaSelectionRate += 100;
    }
    areaSelectionRate = Math.min(Math.pow(10, 7), areaSelectionRate);
    console.log("Increase " + areaSelectionRate.toLocaleString());
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
      console.log((new Date()).toISOString() + " UI query " + (performance.now() - start) + "ms, regions " + response.regionSummaries.length.toLocaleString());
      queryResponse = response;
      scheduleNextDeviceQuery();
    },
    function (error) {
      console.log(error);
      scheduleNextDeviceQuery();
    }
  );
}

function initActivityMonitor() {
  activityMonitor.size = 180;
  activityMonitor.firstQueryUpdate = true;
  activityMonitor.lastUpdate = Date.now();
  activityMonitor.counts = [];
  for (let i = 0; i < activityMonitor.size; i++) {
    activityMonitor.counts.push({deviceCount: 0, deviceDelta: 0, happyCount: 0, happyDelta: 0, sadCount: 0, sadDelta: 0});
  }
}

function drawActivityMonitor() {
  updateData();

  const verticalOffset = 5.0;
  const offsetLeftRight = 22;
  const width = grid.ticksHorizontal - 2 * offsetLeftRight;
  const height = verticalOffset - 0.5;

  const minDelta = minDeviceDelta();
  const maxDelta = maxDeviceDelta();
  const scale = {pos: scalePos(maxDelta), neg: scaleNeg(minDelta)};

  const x = offsetLeftRight;
  const y = grid.ticksVertical - verticalOffset;

  drawBackground(x, y, width, height);
  drawAxis(x, y, width, height, scale);
  drawTickLines(x, y, width, height, scale);
  drawActivity(x, y, width, height, scale);
  drawLabels(x, y, grid.ticksVertical - 1.3, scale);

  function drawBackground(x, y, width, height) {
    const bgColor = color(0, 0, 75, 125);
    fill(bgColor);
    grid.rect(x, y, width, height);
  }

  function drawLabels(x, yTop, yBot, scale) {
    const valueBgColor = color(200, 200, 200, 200);
    const valueFontColor = color(50, 50, 200);
    Label().setX(x).setY(yTop).setW(3).setH(0.8)
            .setBorder(0.1)
            .setKey(scale.pos.toLocaleString())
            .setBgColor(valueBgColor)
            .setKeyColor(valueFontColor)
            .draw();

    Label().setX(x).setY(yBot).setW(3).setH(0.8)
            .setBorder(0.1)
            .setKey(scale.neg.toLocaleString())
            .setBgColor(valueBgColor)
            .setKeyColor(valueFontColor)
            .draw();
  }

  function drawAxis(x, y, width, height, scale) {
    const range = scale.pos - scale.neg;
    const yAxis = y + height * scale.pos / range;
    stroke(color(200, 200, 0));
    strokeWeight(1);
    grid.line(x, yAxis, x + width, yAxis);
  }

  function drawTickLines(x, y, width, height, scale) {
    const range = scale.pos - scale.neg;
    const yAxis = y + height * scale.pos / range;
    const tick = (scale.pos > -1 * scale.neg ? yAxis - y : y + height - yAxis) * 0.25;
    let yTick = yAxis - tick;
    stroke(color(200, 200, 200));
    strokeWeight(0.75);
    while (yTick > y) {
      grid.line(x, yTick, x + width, yTick);
      yTick -= tick;
    }
    yTick = yAxis + tick;
    while (yTick < y + height) {
      grid.line(x, yTick, x + width, yTick);
      yTick += tick;
    }
  }

  function drawActivity(x, y, width, height, scale) {
    for (let i = 0; i < activityMonitor.counts.length; i++) {
      const deltas = activityMonitor.counts[i];
      const barWidth = width / activityMonitor.counts.length / 2;
      const barX = x + i * barWidth * 2;
      drawActivityBar(barX, y, width, height, scale, deltas.happyDelta, color(0, 200, 0));
      drawActivityBar(barX + barWidth, y, width, height, scale, deltas.sadDelta, color(200, 0, 0));
    }
  }

  function drawActivityBar(x, y, width, height, scale, delta, barColor) {
    if (delta != 0) {
      const range = scale.pos - scale.neg;
      const barWidth = width / activityMonitor.counts.length / 2;
      const rangeHeight = height / range;
      const barTop = Math.max(0, delta);
      const barBot = Math.min(0, delta);
      const barX = x;
      const barY = y + (scale.pos - barTop) * rangeHeight;
      const barHeight = height * ((scale.pos - barBot) - (scale.pos - barTop)) / range;
      fill(barColor);
      noStroke();
      grid.rect(barX, barY, barWidth, barHeight);
    }
  }

  function updateData() {
    const timeNow = Date.now();
    const timeDelta = timeNow - activityMonitor.lastUpdate;
    if (timeDelta > 1000) {
      const last = activityMonitor.counts[activityMonitor.counts.length - 1];
      if (activityMonitor.firstQueryUpdate && queryResponse.deviceCount > 0) {
        activityMonitor.firstQueryUpdate = false;
        last.deviceCount = queryResponse.deviceCount;
        last.happyCount = queryResponse.happyCount;
        last.sadCount = queryResponse.sadCount;
      }
      activityMonitor.counts.shift();
      activityMonitor.counts.push({
        deviceCount: queryResponse.deviceCount,
        deviceDelta: queryResponse.deviceCount - last.deviceCount,
        happyCount: queryResponse.happyCount,
        happyDelta: queryResponse.happyCount - last.happyCount,
        sadCount: queryResponse.sadCount,
        sadDelta: queryResponse.sadCount - last.sadCount,
        time: timeNow});
      activityMonitor.lastUpdate = timeDelta > 5000 ? timeNow : activityMonitor.lastUpdate + 1000;
    }
  }

  function minDeviceDelta() {
    let deltaMin = Number.MAX_SAFE_INTEGER;
    activityMonitor.counts.forEach(deltas => {
      deltaMin = Math.min(deltaMin, deltas.deviceDelta, deltas.happyDelta, deltas.sadDelta);
    });
    return deltaMin;
  }

  function maxDeviceDelta() {
    let deltaMax = Number.MIN_SAFE_INTEGER;
    activityMonitor.counts.forEach(deltas => {
      deltaMax = Math.max(deltaMax, deltas.deviceDelta, deltas.happyDelta, deltas.sadDelta);
    });
    return deltaMax;
  }

  function scalePos(maxDelta) {
    let scale = 10;
    while (maxDelta / scale > 10) {
      scale *= 10;
    }
    return Math.floor(maxDelta / scale) * scale + scale;
  }

  function scaleNeg(minDelta) {
    let scale = -10;
    while (minDelta / scale > 10) {
      scale *= 10;
    }
    return Math.floor(minDelta / scale) * scale + scale;
  }
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
  toGridX: function(x) {
    return (x - this.borderWidth) / this.tickWidth;
  },
  toGridY: function(y) {
    return (y - this.borderWidth) / this.tickWidth;
  },
  toGridLength: function(length) {
    return length / this.tickWidth;
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
