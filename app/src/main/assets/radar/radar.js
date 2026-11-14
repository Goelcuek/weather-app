/* Hava radar / wind / temperature map.
 * Radar: RainViewer past 2 h (personal use, zoom ≤ 7, Universal Blue).
 * Wind & temperature: Open-Meteo hourly forecast on a coarse grid over the visible area,
 * interpolated and drawn as a colour field with Windy-style flowing particles. */
(function () {
  "use strict";

  // ---------- params ----------
  const q = new URLSearchParams(location.search);
  const LAT = parseFloat(q.get("lat")) || 39.77;
  const LON = parseFloat(q.get("lon")) || 30.52;
  const UNIT_F = q.get("unit") === "f";
  const LANG = (q.get("lang") || navigator.language || "en").slice(0, 2);
  const PLACE = q.get("place") || "";

  const T = {
    tr: { radar: "Radar", wind: "Rüzgâr", temp: "Sıcaklık", windy: "Windy", now: "Şimdi",
          loading: "Yükleniyor…", offline: "Veri alınamadı. Bağlantıyı kontrol edin.",
          radarZoom: "Radar en fazla bu yakınlıkta gösterilir.", mmh: "mm/sa", kmh: "km/sa",
          light: "Hafif", heavy: "Şiddetli", minAgo: "dk önce", from: "yönünden" },
    en: { radar: "Radar", wind: "Wind", temp: "Temp", windy: "Windy", now: "Now",
          loading: "Loading…", offline: "Couldn't load data. Check your connection.",
          radarZoom: "Radar is shown up to this zoom level.", mmh: "mm/h", kmh: "km/h",
          light: "Light", heavy: "Heavy", minAgo: "min ago", from: "from" },
  }[LANG === "tr" ? "tr" : "en"];

  document.querySelectorAll("#layers button").forEach((b) => (b.textContent = T[b.dataset.layer]));

  // ---------- map ----------
  const map = L.map("map", {
    zoomControl: false, minZoom: 3, maxZoom: 10, worldCopyJump: true,
    zoomSnap: 0.5, attributionControl: true,
  }).setView([LAT, LON], 6);
  map.attributionControl.setPrefix(false);

  map.createPane("radarPane").style.zIndex = 360;
  const fieldPane = map.createPane("fieldPane");
  fieldPane.style.zIndex = 370;
  fieldPane.classList.add("field-pane");
  map.createPane("labelsPane").style.zIndex = 450;
  map.getPane("labelsPane").style.pointerEvents = "none";

  const CARTO = "https://{s}.basemaps.cartocdn.com/{style}/{z}/{x}/{y}{r}.png";
  L.tileLayer(CARTO, { style: "dark_nolabels", subdomains: "abcd", maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a>' }).addTo(map);
  L.tileLayer(CARTO, { style: "dark_only_labels", subdomains: "abcd", maxZoom: 19, pane: "labelsPane" }).addTo(map);

  L.marker([LAT, LON], { icon: L.divIcon({ className: "", html: '<div class="me"></div>', iconSize: [16, 16] }), interactive: false })
    .addTo(map);

  // ---------- ui ----------
  const $ = (s) => document.querySelector(s);
  const slider = $("#time"), tlabel = $("#tlabel"), status = $("#status"), panel = $("#panel");
  const setStatus = (s) => (status.textContent = s || "");
  function sliderPct() {
    const max = +slider.max || 1;
    slider.style.setProperty("--pct", ((+slider.value / max) * 100).toFixed(1) + "%");
  }
  function updatePanelHeight() {
    document.documentElement.style.setProperty("--panel-h", panel.offsetHeight + 24 + "px");
  }

  window.setInsets = function (top, bottom) {
    const r = document.documentElement.style;
    r.setProperty("--inset-top", top + "px");
    r.setProperty("--inset-bottom", bottom + "px");
    setTimeout(updatePanelHeight, 50);
  };

  $("#close").onclick = () => (window.HavaApp ? HavaApp.close() : history.back());
  $("#locate").onclick = () => map.flyTo([LAT, LON], Math.max(map.getZoom(), 7), { duration: 0.8 });

  // ---------- colour ramps ----------
  function hex(h) { return [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)]; }
  function ramp(stops) {
    const s = stops.map(([v, c]) => [v, hex(c)]);
    return function (v) {
      if (v <= s[0][0]) return s[0][1];
      for (let i = 1; i < s.length; i++) {
        if (v <= s[i][0]) {
          const t = (v - s[i - 1][0]) / (s[i][0] - s[i - 1][0]);
          const a = s[i - 1][1], b = s[i][1];
          return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
        }
      }
      return s[s.length - 1][1];
    };
  }
  const WIND_STOPS = [[0, "#6271B7"], [5, "#3961A9"], [10, "#4A94A9"], [20, "#4D8D7B"], [30, "#53A553"], [40, "#359F35"],
    [50, "#A79D51"], [60, "#9F7F3A"], [75, "#A16C5C"], [90, "#813A4E"], [110, "#AF5088"], [140, "#754A93"]];
  const TEMP_STOPS = [[-30, "#7E4B9C"], [-20, "#5B5FC7"], [-10, "#3F8FD0"], [0, "#5DC5D9"], [5, "#7AD3A1"], [10, "#A6D96A"],
    [15, "#E6E05A"], [20, "#F6B748"], [25, "#F08A3C"], [30, "#DC5A3A"], [40, "#9E2A4A"]];
  const windColor = ramp(WIND_STOPS), tempColor = ramp(TEMP_STOPS);
  const toC = (t) => (UNIT_F ? (t - 32) * 5 / 9 : t);
  const tUnit = UNIT_F ? "°F" : "°C";

  function setLegend(stops, ticks, fmt) {
    const lo = stops[0][0], hi = stops[stops.length - 1][0];
    $("#lbar").style.background = "linear-gradient(to right," +
      stops.map(([v, c]) => `${c} ${(((v - lo) / (hi - lo)) * 100).toFixed(1)}%`).join(",") + ")";
    $("#lticks").innerHTML = ticks.map((t) => `<span>${fmt(t)}</span>`).join("");
  }

  // ---------- time helpers ----------
  const DAYS = { tr: ["Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"], en: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"] }[LANG === "tr" ? "tr" : "en"];
  const pad = (n) => String(n).padStart(2, "0");

  // =====================================================================
  // RADAR (RainViewer)
  // =====================================================================
  const Radar = {
    frames: [], layers: [], host: "", idx: 0, loaded: false,
    async load() {
      setStatus(T.loading);
      try {
        const r = await fetch("https://api.rainviewer.com/public/weather-maps.json", { cache: "no-store" });
        const j = await r.json();
        this.host = j.host;
        this.frames = (j.radar && j.radar.past) || [];
        this.loaded = true;
        setStatus("");
      } catch (e) {
        setStatus(T.offline);
      }
    },
    layer(i) {
      if (!this.layers[i]) {
        const f = this.frames[i];
        this.layers[i] = L.tileLayer(`${this.host}${f.path}/256/{z}/{x}/{y}/2/1_1.png`, {
          pane: "radarPane", opacity: 0, maxNativeZoom: 7, maxZoom: 10, tileSize: 256,
          attribution: '<a href="https://www.rainviewer.com/">Weather data by RainViewer</a>',
        }).addTo(map);
      }
      return this.layers[i];
    },
    async show() {
      if (!this.loaded) await this.load();
      if (!this.frames.length) return;
      slider.max = this.frames.length - 1;
      this.set(this.frames.length - 1);
      // preload neighbours so playback is smooth
      this.frames.forEach((_, i) => this.layer(i));
      setLegend([[0, "#9BE9FF"], [1, "#4FB3F7"], [2, "#2A7FE0"], [3, "#1E5BC6"], [4, "#F4D35E"], [5, "#F28C38"], [6, "#E5484D"], [7, "#B03EB5"]],
        [T.light, "", "", T.heavy], (x) => x);
    },
    set(i) {
      this.idx = i;
      slider.value = i;
      sliderPct();
      this.layers.forEach((l, k) => l && l.setOpacity(k === i ? 0.82 : 0));
      this.layer(i).setOpacity(0.82);
      const f = this.frames[i];
      const d = new Date(f.time * 1000);
      const ago = Math.round((Date.now() / 1000 - f.time) / 60);
      tlabel.textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}` + (ago > 5 ? `  ·  ${ago} ${T.minAgo}` : `  ·  ${T.now}`);
    },
    hide() { this.layers.forEach((l) => l && l.setOpacity(0)); },
    count() { return this.frames.length; },
  };

  // =====================================================================
  // GRID (Open-Meteo) for wind + temperature
  // =====================================================================
  const NX = 13, NY = 11;
  const Grid = {
    data: null, // {lat0,lat1,lon0,lon1, times[], u[t][j][i], v, s, t}
    zoom: null, busy: false, idx: 0,
    covers() {
      if (!this.data) return false;
      const b = map.getBounds(), d = this.data;
      return b.getSouth() >= d.lat0 && b.getNorth() <= d.lat1 && b.getWest() >= d.lon0 && b.getEast() <= d.lon1 &&
        Math.abs(map.getZoom() - this.zoom) < 2;
    },
    async ensure() {
      if (this.covers() || this.busy) return false;
      this.busy = true;
      setStatus(T.loading);
      const b = map.getBounds().pad(0.25);
      const lat0 = Math.max(-85, b.getSouth()), lat1 = Math.min(85, b.getNorth());
      let lon0 = b.getWest(), lon1 = b.getEast();
      if (lon1 - lon0 > 360) { lon0 = -180; lon1 = 180; }
      const lats = [], lons = [];
      for (let j = 0; j < NY; j++) for (let i = 0; i < NX; i++) {
        lats.push((lat0 + (lat1 - lat0) * j / (NY - 1)).toFixed(3));
        let lo = lon0 + (lon1 - lon0) * i / (NX - 1);
        lo = ((lo + 540) % 360) - 180;
        lons.push(lo.toFixed(3));
      }
      const url = "https://api.open-meteo.com/v1/forecast?latitude=" + lats.join(",") + "&longitude=" + lons.join(",") +
        "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m&forecast_hours=25&timezone=auto&wind_speed_unit=kmh" +
        (UNIT_F ? "&temperature_unit=fahrenheit" : "");
      try {
        const r = await fetch(url);
        if (!r.ok) throw new Error("HTTP " + r.status);
        let arr = await r.json();
        if (!Array.isArray(arr)) arr = [arr];
        const times = arr[0].hourly.time;
        const nT = times.length;
        const mk = () => Array.from({ length: nT }, () => Array.from({ length: NY }, () => new Float32Array(NX)));
        const u = mk(), v = mk(), s = mk(), t = mk();
        arr.forEach((p, k) => {
          const j = Math.floor(k / NX), i = k % NX, h = p.hourly;
          for (let n = 0; n < nT; n++) {
            const sp = h.wind_speed_10m[n] ?? 0, dir = (h.wind_direction_10m[n] ?? 0) * Math.PI / 180;
            s[n][j][i] = sp;
            u[n][j][i] = -sp * Math.sin(dir); // eastward
            v[n][j][i] = -sp * Math.cos(dir); // northward
            t[n][j][i] = h.temperature_2m[n] ?? 0;
          }
        });
        this.data = { lat0, lat1, lon0, lon1, times, u, v, s, t };
        this.zoom = map.getZoom();
        // start at the current hour
        const nowKey = (() => { const d = new Date(Date.now() + (arr[0].utc_offset_seconds || 0) * 1000);
          return d.toISOString().slice(0, 13); })();
        const k0 = times.findIndex((x) => x.slice(0, 13) >= nowKey);
        if (this.idx === 0 && k0 > 0) this.idx = k0;
        this.idx = Math.min(this.idx, nT - 1);
        setStatus("");
        return true;
      } catch (e) {
        setStatus(T.offline);
        return false;
      } finally {
        this.busy = false;
      }
    },
    sample(field, lat, lon) {
      const d = this.data;
      let lo = lon;
      while (lo < d.lon0) lo += 360;
      while (lo > d.lon1) lo -= 360;
      const fx = Math.min(NX - 1.001, Math.max(0, (lo - d.lon0) / (d.lon1 - d.lon0) * (NX - 1)));
      const fy = Math.min(NY - 1.001, Math.max(0, (lat - d.lat0) / (d.lat1 - d.lat0) * (NY - 1)));
      const i = Math.floor(fx), j = Math.floor(fy), a = fx - i, b = fy - j;
      const g = d[field][this.idx];
      return (g[j][i] * (1 - a) + g[j][i + 1] * a) * (1 - b) + (g[j + 1][i] * (1 - a) + g[j + 1][i + 1] * a) * b;
    },
    label(i) {
      const s = this.data.times[i];
      const d = new Date(s + ":00");
      const now = new Date();
      const today = d.getDate() === now.getDate() && d.getMonth() === now.getMonth();
      const isNow = today && d.getHours() === now.getHours();
      return (isNow ? T.now + "  ·  " : today ? "" : DAYS[d.getDay()] + " ") + `${pad(d.getHours())}:00`;
    },
  };

  // ---------- field canvases ----------
  const heat = L.DomUtil.create("canvas", "", fieldPane);
  const parts = L.DomUtil.create("canvas", "", fieldPane);
  const hctx = heat.getContext("2d"), pctx = parts.getContext("2d");
  const small = document.createElement("canvas"), sctx = small.getContext("2d");
  const DPR = Math.min(window.devicePixelRatio || 1, 2);
  let W = 0, H = 0;

  function placeCanvases() {
    const size = map.getSize();
    W = size.x; H = size.y;
    [heat, parts].forEach((c) => {
      c.width = W * DPR; c.height = H * DPR;
      c.style.width = W + "px"; c.style.height = H + "px";
      L.DomUtil.setPosition(c, map.containerPointToLayerPoint([0, 0]));
    });
  }

  const CELL = 6;
  function drawHeat(kind) {
    if (!Grid.data) return;
    const cw = Math.ceil(W / CELL) + 1, ch = Math.ceil(H / CELL) + 1;
    small.width = cw; small.height = ch;
    const img = sctx.createImageData(cw, ch);
    for (let y = 0; y < ch; y++) {
      for (let x = 0; x < cw; x++) {
        const ll = map.containerPointToLatLng([x * CELL, y * CELL]);
        const c = kind === "wind" ? windColor(Grid.sample("s", ll.lat, ll.lng)) : tempColor(toC(Grid.sample("t", ll.lat, ll.lng)));
        const o = (y * cw + x) * 4;
        img.data[o] = c[0]; img.data[o + 1] = c[1]; img.data[o + 2] = c[2]; img.data[o + 3] = 255;
      }
    }
    sctx.putImageData(img, 0, 0);
    hctx.clearRect(0, 0, heat.width, heat.height);
    hctx.imageSmoothingEnabled = true;
    hctx.imageSmoothingQuality = "high";
    hctx.globalAlpha = kind === "wind" ? 0.62 : 0.68;
    hctx.drawImage(small, 0, 0, cw * CELL * DPR, ch * CELL * DPR);
    hctx.globalAlpha = 1;
  }

  // ---------- particles ----------
  const VSTEP = 8;
  let vfield = null, vw = 0, vh = 0, particles = [], raf = 0, lastFrame = 0;
  function buildVectorField() {
    vw = Math.ceil(W / VSTEP) + 1; vh = Math.ceil(H / VSTEP) + 1;
    vfield = new Float32Array(vw * vh * 2);
    // pixels per frame per km/h, gently scaled with zoom so motion stays readable
    const k = 0.028 * Math.pow(1.25, map.getZoom() - 6);
    for (let y = 0; y < vh; y++) for (let x = 0; x < vw; x++) {
      const ll = map.containerPointToLatLng([x * VSTEP, y * VSTEP]);
      const o = (y * vw + x) * 2;
      vfield[o] = Grid.sample("u", ll.lat, ll.lng) * k;
      vfield[o + 1] = -Grid.sample("v", ll.lat, ll.lng) * k;
    }
  }
  function vecAt(x, y) {
    const fx = x / VSTEP, fy = y / VSTEP;
    const i = Math.floor(fx), j = Math.floor(fy);
    if (i < 0 || j < 0 || i >= vw - 1 || j >= vh - 1) return null;
    const a = fx - i, b = fy - j;
    const o00 = (j * vw + i) * 2, o10 = o00 + 2, o01 = o00 + vw * 2, o11 = o01 + 2;
    return [
      (vfield[o00] * (1 - a) + vfield[o10] * a) * (1 - b) + (vfield[o01] * (1 - a) + vfield[o11] * a) * b,
      (vfield[o00 + 1] * (1 - a) + vfield[o10 + 1] * a) * (1 - b) + (vfield[o01 + 1] * (1 - a) + vfield[o11 + 1] * a) * b,
    ];
  }
  function spawn(p) {
    p.x = Math.random() * W; p.y = Math.random() * H;
    p.age = 0; p.max = 40 + Math.random() * 60;
    return p;
  }
  function startParticles() {
    stopParticles();
    buildVectorField();
    const n = Math.min(3200, Math.round((W * H) / 220));
    particles = Array.from({ length: n }, () => spawn({}));
    particles.forEach((p) => (p.age = Math.random() * p.max));
    pctx.clearRect(0, 0, parts.width, parts.height);
    const tick = (ts) => {
      raf = requestAnimationFrame(tick);
      if (ts - lastFrame < 30) return; // ~33 fps
      lastFrame = ts;
      pctx.globalCompositeOperation = "destination-in";
      pctx.fillStyle = "rgba(0,0,0,0.9)";
      pctx.fillRect(0, 0, parts.width, parts.height);
      pctx.globalCompositeOperation = "source-over";
      pctx.strokeStyle = "rgba(255,255,255,0.85)";
      pctx.lineWidth = 1.1 * DPR;
      pctx.beginPath();
      for (const p of particles) {
        const v = vecAt(p.x, p.y);
        if (!v || p.age++ > p.max) { spawn(p); continue; }
        const nx = p.x + v[0], ny = p.y + v[1];
        pctx.moveTo(p.x * DPR, p.y * DPR);
        pctx.lineTo(nx * DPR, ny * DPR);
        p.x = nx; p.y = ny;
      }
      pctx.stroke();
    };
    raf = requestAnimationFrame(tick);
  }
  function stopParticles() {
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    pctx.clearRect(0, 0, parts.width, parts.height);
  }

  // =====================================================================
  // layer switching + timeline
  // =====================================================================
  let layer = "radar", playing = false, playTimer = 0;

  async function renderField() {
    if (layer !== "wind" && layer !== "temp") return;
    placeCanvases();
    if (!Grid.data) { hctx.clearRect(0, 0, heat.width, heat.height); stopParticles(); return; }
    slider.max = Grid.data.times.length - 1;
    slider.value = Grid.idx;
    sliderPct();
    tlabel.textContent = Grid.label(Grid.idx);
    drawHeat(layer);
    if (layer === "wind") startParticles(); else stopParticles();
  }

  async function refreshField() {
    if (layer !== "wind" && layer !== "temp") return;
    await Grid.ensure();
    renderField();
  }

  function clearField() {
    stopParticles();
    hctx.clearRect(0, 0, heat.width, heat.height);
  }

  const OM_ATTR = '<a href="https://open-meteo.com/">Open-Meteo</a>';
  async function setLayer(name) {
    if (name === layer && name !== "radar") return;
    map.attributionControl.removeAttribution(OM_ATTR);
    if (name === "wind" || name === "temp") map.attributionControl.addAttribution(OM_ATTR);
    pause();
    layer = name;
    document.querySelectorAll("#layers button").forEach((b) => b.classList.toggle("on", b.dataset.layer === name));
    document.body.classList.toggle("windy-on", name === "windy");
    try { localStorage.setItem("hava.layer", name); } catch (e) {}

    if (name === "windy") {
      Radar.hide(); clearField();
      const f = $("#windy");
      const c = map.getCenter();
      const src = "https://embed.windy.com/embed.html?type=map&location=coordinates" +
        "&metricRain=mm&metricTemp=" + encodeURIComponent(tUnit) + "&metricWind=" + encodeURIComponent("km/h") +
        "&zoom=" + Math.round(map.getZoom()) + "&overlay=wind&product=ecmwf&level=surface" +
        "&lat=" + c.lat.toFixed(3) + "&lon=" + c.lng.toFixed(3) + "&marker=true&message=true";
      if (f.dataset.src !== src) { f.src = src; f.dataset.src = src; }
      return;
    }
    if (name === "radar") {
      clearField();
      await Radar.show();
    } else {
      Radar.hide();
      if (name === "wind") setLegend(WIND_STOPS, [0, 20, 40, 60, 90, 140], (v) => v + (v === 140 ? " " + T.kmh : ""));
      else setLegend(TEMP_STOPS, [-30, -10, 0, 10, 20, 30, 40], (v) => (UNIT_F ? Math.round(v * 9 / 5 + 32) : v) + "°");
      await refreshField();
    }
    updatePanelHeight();
  }

  function stepTo(i) {
    if (layer === "radar") Radar.set(i);
    else if (Grid.data) { Grid.idx = i; renderField(); }
  }
  function count() { return layer === "radar" ? Radar.count() : Grid.data ? Grid.data.times.length : 0; }

  function play() {
    if (!count()) return;
    playing = true;
    panel.classList.add("playing");
    const delay = layer === "radar" ? 550 : 900;
    playTimer = setInterval(() => {
      const n = count();
      let i = +slider.value + 1;
      if (i >= n) i = 0;
      stepTo(i);
    }, delay);
  }
  function pause() {
    playing = false;
    panel.classList.remove("playing");
    clearInterval(playTimer);
  }
  $("#play").onclick = () => (playing ? pause() : play());
  slider.oninput = () => { pause(); stepTo(+slider.value); };

  document.querySelectorAll("#layers button").forEach((b) => (b.onclick = () => setLayer(b.dataset.layer)));

  // map movement: hide the field while moving, rebuild after
  map.on("movestart zoomstart", () => { if (layer === "wind" || layer === "temp") clearField(); });
  let moveTimer = 0;
  map.on("moveend", () => {
    clearTimeout(moveTimer);
    moveTimer = setTimeout(refreshField, 120);
    if (layer === "radar") {
      setStatus(map.getZoom() > 7.5 ? T.radarZoom : "");
    }
  });
  map.on("resize", () => renderField());

  // tap for a point readout
  map.on("click", (e) => {
    if ((layer !== "wind" && layer !== "temp") || !Grid.data) return;
    const { lat, lng } = e.latlng;
    let html;
    if (layer === "wind") {
      const u = Grid.sample("u", lat, lng), v = Grid.sample("v", lat, lng);
      const sp = Math.hypot(u, v);
      const from = (Math.atan2(-u, -v) * 180 / Math.PI + 360) % 360;
      const dirs = LANG === "tr" ? ["K", "KD", "D", "GD", "G", "GB", "B", "KB"] : ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
      html = `<b>${Math.round(sp)} ${T.kmh}</b><br>${dirs[Math.round(from / 45) % 8]} ${T.from}`;
    } else {
      html = `<b>${Math.round(Grid.sample("t", lat, lng))}${tUnit}</b>`;
    }
    L.popup({ closeButton: false, offset: [0, -4] }).setLatLng(e.latlng).setContent(html).openOn(map);
  });

  // ---------- go ----------
  let start = "radar";
  try { start = localStorage.getItem("hava.layer") || "radar"; } catch (e) {}
  setLayer(start);
  setTimeout(updatePanelHeight, 100);
  window.addEventListener("resize", updatePanelHeight);
})();
