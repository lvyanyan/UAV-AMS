/**
 * 鐢?@gltf-transform/core 鐢熸垚 3D Tiles锛坆3dm + tileset.json锛? * 杩愯: node scripts/generate-tileset.cjs
 */
const path = require('path');
const fs = require('fs');
const { Document, WebIO } = require('@gltf-transform/core');

const OUT = path.resolve(__dirname, '../public/tilesets');
const CX = 116.397, CY = 39.908;
const COLOR_HEX = [0xff4444, 0xff9900, 0x33ff66, 0x3388ff, 0x9933ff, 0xffdd33];

const REGIONS = [
  { west: CX-0.015, south: CY-0.005, east: CX+0.005, north: CY+0.015, minH: 0, maxH: 400, color: 0 },
  { west: CX-0.022, south: CY-0.022, east: CX+0.002, north: CY-0.002, minH: 0, maxH: 300, color: 1 },
  { west: CX+0.005, south: CY-0.014, east: CX+0.022, north: CY+0.003, minH: 0, maxH: 500, color: 2 },
  { west: CX+0.002, south: CY+0.002, east: CX+0.022, north: CY+0.022, minH: 0, maxH: 200, color: 3 },
  { west: CX-0.006, south: CY-0.007, east: CX+0.008, north: CY+0.005, minH: 0, maxH: 350, color: 4 },
  { west: CX-0.013, south: CY-0.001, east: CX+0.001, north: CY+0.013, minH: 0, maxH: 250, color: 5 }
];

async function main() {
  const doc = new Document();

  for (let i = 0; i < REGIONS.length; i++) {
    const r = REGIONS[i];
    const sx = (r.east - r.west) * 111320;
    const sy = (r.north - r.south) * 111320;
    const sz = r.maxH - r.minH;
    const cx = (r.west + r.east) / 2;
    const cy = (r.south + r.north) / 2;
    const ox = (cx - CX) * 111320;
    const oy = (cy - CY) * 111320;
    const oz = (r.minH + r.maxH) / 2;

    const hex = COLOR_HEX[r.color];
    const rCol = ((hex >> 16) & 0xff) / 255;
    const gCol = ((hex >> 8) & 0xff) / 255;
    const bCol = (hex & 0xff) / 255;

    const positions = new Float32Array([
      -sx/2, -sy/2, -sz/2,  sx/2, -sy/2, -sz/2,  sx/2,  sy/2, -sz/2, -sx/2, sy/2, -sz/2,
      -sx/2, -sy/2,  sz/2,  sx/2, -sy/2,  sz/2,  sx/2,  sy/2,  sz/2, -sx/2, sy/2,  sz/2
    ]);
    const indices = new Uint16Array([
      0,1,2, 0,2,3,
      4,6,5, 4,7,6,
      0,4,5, 0,5,1,
      3,2,6, 3,6,7,
      0,3,7, 0,7,4,
      1,5,6, 1,6,2
    ]);

    const posAcc = doc.createAccessor().setType('VEC3').setArray(positions);
    const idxAcc = doc.createAccessor().setType('SCALAR').setArray(indices);

    const mat = doc.createMaterial('mat-' + i)
      .setBaseColorFactor([rCol, gCol, bCol, 0.5])
      .setAlphaMode('BLEND')
      .setDoubleSided(true);

    const prim = doc.createPrimitive();
    prim.setAttribute('POSITION', posAcc);
    prim.setIndices(idxAcc);
    prim.setMaterial(mat);

    const mesh = doc.createMesh('box-' + i);
    mesh.addPrimitive(prim);

    const node = doc.createNode('node-' + i);
    node.setMesh(mesh);
    node.setTranslation([ox, oy, oz]);
  }

  // Scene
  const scene = doc.createScene('main');
  const allNodes = doc.getRoot().listNodes();
  allNodes.forEach(n => scene.addChild(n));
  doc.getRoot().setDefaultScene(scene);

  // 瀵煎嚭 GLB
  fs.mkdirSync(OUT, { recursive: true });
  doc.createBuffer();`nconst io = new WebIO();
  const glbBuffer = await io.writeBinary(doc);
  const glbBuf = Buffer.from(glbBuffer);

  // 灏佽 b3dm
  const fjson = Buffer.from(JSON.stringify({}));
  const bjson = Buffer.from(JSON.stringify({}));
  const zero = Buffer.alloc(0);
  const header = Buffer.alloc(28);
  header.write('b3dm', 0, 4, 'ascii');
  header.writeUInt32LE(1, 4);
  const total = 28 + fjson.length + zero.length + bjson.length + zero.length + glbBuf.length;
  header.writeUInt32LE(total, 8);
  header.writeUInt32LE(fjson.length, 12);
  header.writeUInt32LE(0, 16);
  header.writeUInt32LE(bjson.length, 20);
  header.writeUInt32LE(0, 24);
  fs.writeFileSync(path.join(OUT, 'tile.b3dm'), Buffer.concat([header, fjson, zero, bjson, zero, glbBuf]));

  // 鍖呭洿鐩?  let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity, minH = Infinity, maxH = -Infinity;
  REGIONS.forEach(r => {
    if (r.west < minLng) minLng = r.west;
    if (r.east > maxLng) maxLng = r.east;
    if (r.south < minLat) minLat = r.south;
    if (r.north > maxLat) maxLat = r.north;
    if (r.minH < minH) minH = r.minH;
    if (r.maxH > maxH) maxH = r.maxH;
  });
  const cx2 = (minLng + maxLng) / 2, cy2 = (minLat + maxLat) / 2, ch = (minH + maxH) / 2;

  // tileset.json
  const tileset = {
    asset: { version: '1.0', gltfUpAxis: 'Z' },
    geometricError: 500,
    root: {
      boundingVolume: {
        box: [cx2, cy2, ch, (maxLng - minLng) / 2, 0, 0, 0, (maxLat - minLat) / 2, 0, 0, 0, (maxH - minH) / 2]
      },
      geometricError: 100,
      refine: 'REPLACE',
      content: { uri: 'tile.b3dm' }
    }
  };
  fs.writeFileSync(path.join(OUT, 'tileset.json'), JSON.stringify(tileset, null, 2));

  console.log('鉁?鐢熸垚瀹屾垚: public/tilesets/tileset.json + tile.b3dm (' + (glbBuf.length / 1024).toFixed(1) + ' KB)');
  console.log('   鍖呭惈 ' + REGIONS.length + ' 涓┖鍩?);
}

main().catch(e => { console.error('鉂?澶辫触:', e.message); process.exit(1); });
