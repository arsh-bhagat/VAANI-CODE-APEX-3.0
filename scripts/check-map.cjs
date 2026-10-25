// Verify the native-to-Leaflet state contract without network tiles or a device.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/leaflet_map.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const layers = new Set();
const groups = [];
const shapes = [];
const element = () => ({ style: {}, clientWidth: 360, clientHeight: 360,
  classList: { add() {}, remove() {} }, addEventListener() {}, setAttribute() {},
  append() {}, cloneNode() { return element(); } });
const map = { setView() { return this; }, invalidateSize() {},
  getContainer: element, getSize: () => ({ x: 360, y: 360 }),
  addLayer(layer) { layers.add(layer); return this; },
  removeLayer(layer) { layers.delete(layer); return this; }, whenReady() {} };
const shape = (kind, points, options) => {
  const item = { kind, points, options, bindPopup(value) { this.popup = value; return this; }, bindTooltip(value) { this.tooltip = value; return this; },
    addTo(target) { if (target === map) layers.add(this); else target.items.push(this); return this; } };
  shapes.push(item);
  return item;
};
const L = { map: () => map, tileLayer: () => ({ on() {}, addTo() {}, redraw() {} }),
  layerGroup() { const group = { items: [], addTo() { layers.add(this); return this; }, clearLayers() { this.items = []; } }; groups.push(group); return group; },
  marker: (p, o) => shape('marker', p, o), circle: (p, o) => shape('circle', p, o),
  polyline: (p, o) => shape('polyline', p, o), divIcon: o => o,
  control: () => ({ addTo() {}, remove() {} }), Control: { extend: () => function () { this.addTo = () => {}; } },
  DomUtil: { create: element }, DomEvent: { disableClickPropagation() {}, on() {} } };
const window = { innerHeight: 360, addEventListener() {} };
const context = vm.createContext({ L, window, navigator: { onLine: true },
  document: { getElementById: element, querySelector: () => null, createElement: element, documentElement: element(), body: element(), addEventListener() {} },
  requestAnimationFrame() {}, console });
vm.runInContext(fs.readFileSync(path.join(__dirname, '../app/src/main/assets/ui_copy.js'), 'utf8'), context);
vm.runInContext(script, context);
const [risk, corridor, patrol, haven] = groups;
for (const [filter, expected] of [
  ['All Signals', [risk, corridor, patrol, haven]],
  ['Well-Lit Streets', [corridor]], ['Patrol Points', [patrol]],
  ['Safe Haven', [haven]], ['Risk Corridor', [risk]]
]) {
  window.setSignalFilter(filter);
  for (const group of groups) assert.equal(layers.has(group), expected.includes(group), filter);
}
assert.equal(patrol.items.length, 2);
assert.equal(haven.items.length, 1);
assert.equal(corridor.items[0].options.color, '#c9a227');
assert.equal(corridor.items[0].options.weight, 4);
window.setDangerZones([{ lat: 28.6, lng: 77.2, radius: 200, level: 'high', title: '<unsafe>', detail: 'Sample' }]);
assert.equal(risk.items.length, 3);
assert.equal(risk.items.find(x => x.kind === 'polyline').options.dashArray, '8 6');
window.setDangerZones([{ lat: 91, lng: 0, radius: 100 }, { lat: 0, lng: 0, radius: -1 }]);
assert.equal(risk.items.length, 0, 'Reject invalid coordinates and radii');
window.applyNativeState('All Signals', 28.6, 77.2, 20, Date.now(), []);
assert(shapes.some(x => x.kind === 'marker' && x.options.icon.className.includes('marker-user') && layers.has(x)));
window.applyNativeState('All Signals', null, null, null, null, []);
assert(!shapes.some(x => x.kind === 'marker' && x.options.icon.className.includes('marker-user') && layers.has(x)), 'Remove stale user marker when GPS clears');
for (const [language, expected] of [['hi', 'आप यहाँ हैं'], ['mr', 'तुम्ही येथे आहात'], ['en', 'You are here']]) {
  window.setUiLanguage(language);
  window.applyNativeState('All Signals', 28.6, 77.2, 20, Date.now(), []);
  const user = shapes.findLast(x => x.kind === 'marker' && x.options.icon.className.includes('marker-user') && layers.has(x));
  assert.equal(user.tooltip, expected);
  assert.equal(layers.has(risk), true, 'Translated copy must preserve canonical filter IDs');
  assert(user.popup.includes('20'), 'Localized accuracy keeps its measurement');
}
console.log('Map checks passed: filters, markers, routes, validation, GPS cleanup and English/Hindi/Marathi labels.');
