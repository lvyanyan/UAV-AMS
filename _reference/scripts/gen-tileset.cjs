/**
 * 用 @gltf-transform/core v4 生成本地 3D Tiles（b3dm + tileset.json）
 * 位置/尺寸与 useAirspaces.js 一致。每个空域独立材质：半透明填充+线框边框
 * 运行: node scripts/gen-tileset.cjs
 */
const { Document, NodeIO, Accessor, Primitive } = require('@gltf-transform/core');
const fs = require('fs');
const path = require('path');

const OUT = path.resolve(__dirname, '../public/tilesets');
fs.mkdirSync(OUT, { recursive: true });

const REGIONS = [
  { cx: -500,  cy: 500,  minH: 0, maxH: 400, hx: 1000, hy: 1000, r: 1.0,   g: 0.15,  b: 0.15,  name: '禁飞区' },
  { cx: -1000, cy: -1200, minH: 0, maxH: 300, hx: 1500, hy: 1500, r: 1.0,   g: 0.6,   b: 0.1,   name: '限飞区' },
  { cx: 1350,  cy: -550,  minH: 0, maxH: 500, hx: 850,  hy: 850,  r: 0.1,   g: 1.0,   b: 0.4,   name: '适飞区' },
  { cx: 1200,  cy: 1200,  minH: 0, maxH: 200, hx: 1200, hy: 1200, r: 0.2,   g: 0.5,   b: 1.0,   name: '训练空域' },
  { cx: 100,   cy: -100,  minH: 0, maxH: 350, hx: 600,  hy: 600,  r: 0.6,   g: 0.2,   b: 1.0,   name: '多边形空域' },
  { cx: -600,  cy: 600,   minH: 0, maxH: 250, hx: 700,  hy: 700,  r: 1.0,   g: 0.9,   b: 0.1,   name: '电子围栏' }
];

const V = [-1,-1,-1,1,-1,-1,1,1,-1,-1,1,-1,-1,-1,1,1,-1,1,1,1,1,-1,1,1];
const I = [0,1,2,0,2,3,4,5,6,4,6,7,0,1,5,0,5,4,2,3,7,2,7,6,0,3,7,0,7,4,1,2,6,1,6,5];
const WI = [0,1,1,2,2,3,3,0,4,5,5,6,6,7,7,4,0,4,1,5,2,6,3,7];

async function main() {
  const doc = new Document();
  const buf = doc.createBuffer('airspace');
  const scene = doc.createScene('airspaces');
  const rootNode = doc.createNode('root');

  REGIONS.forEach(r => {
    const sx = r.hx, sy = r.hy, sz = (r.maxH - r.minH) / 2;
    const cz = (r.minH + r.maxH) / 2;
    const pos = new Float32Array(V.length);
    for (let i = 0; i < 8; i++) {
      pos[i*3] = V[i*3]*sx + r.cx;
      pos[i*3+1] = V[i*3+1]*sy + r.cy;
      pos[i*3+2] = V[i*3+2]*sz + cz;
    }

    // 填充面
    const fp = doc.createAccessor('fp_'+r.name).setArray(new Float32Array(pos)).setType(Accessor.Type.VEC3).setBuffer(buf);
    const fi = doc.createAccessor('fi_'+r.name).setArray(new Uint16Array(I)).setType(Accessor.Type.SCALAR).setBuffer(buf);
    const fPrim = doc.createPrimitive().setAttribute('POSITION',fp).setIndices(fi).setMode(Primitive.Mode.TRIANGLES);
    const fMat = doc.createMaterial('m_'+r.name).setDoubleSided(true).setAlphaMode('BLEND').setBaseColorFactor([r.r,r.g,r.b,0.3]);
    fPrim.setMaterial(fMat);

    // 线框
    const wp = doc.createAccessor('wp_'+r.name).setArray(new Float32Array(pos)).setType(Accessor.Type.VEC3).setBuffer(buf);
    const wi = doc.createAccessor('wi_'+r.name).setArray(new Uint16Array(WI)).setType(Accessor.Type.SCALAR).setBuffer(buf);
    const wPrim = doc.createPrimitive().setAttribute('POSITION',wp).setIndices(wi).setMode(Primitive.Mode.LINES);
    const wMat = doc.createMaterial('wm_'+r.name).setDoubleSided(true).setAlphaMode('BLEND').setBaseColorFactor([r.r,r.g,r.b,0.8]);
    wPrim.setMaterial(wMat);

    // 一个 mesh 可以包含多个 primitive
    const mesh = doc.createMesh('mesh_'+r.name);
    mesh.addPrimitive(fPrim);
    mesh.addPrimitive(wPrim);

    // 每个 region 一个 node，挂到 root
    const node = doc.createNode('node_'+r.name).setMesh(mesh);
    rootNode.addChild(node);
    console.log(`  ${r.name}: 面(alpha=0.3) + 线框(alpha=0.8)`);
  });

  scene.addChild(rootNode);

  const io = new NodeIO();
  console.log('\n写入 GLB ...');
  const glbBuffer = await io.writeBinary(doc);
  console.log(`GLB: ${(glbBuffer.byteLength/1024).toFixed(1)} KB`);

  // b3dm 封装
  const fjson = Buffer.from(JSON.stringify({ BATCH_LENGTH: REGIONS.length }));
  const bjson = Buffer.from(JSON.stringify({}));
  const zero = Buffer.alloc(0);
  const glbBuf = Buffer.from(glbBuffer);
  const header = Buffer.alloc(28);
  header.write('b3dm',0,4,'ascii');
  header.writeUInt32LE(1,4);
  const total = 28 + fjson.length + zero.length + bjson.length + zero.length + glbBuf.length;
  header.writeUInt32LE(total,8);
  header.writeUInt32LE(fjson.length,12);
  header.writeUInt32LE(0,16);
  header.writeUInt32LE(bjson.length,20);
  header.writeUInt32LE(0,24);
  fs.writeFileSync(path.join(OUT,'tile.b3dm'), Buffer.concat([header,fjson,zero,bjson,zero,glbBuf]));
  console.log(`b3dm: ${(glbBuf.length/1024).toFixed(1)} KB`);

  // 包围盒
  let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity,minZ=Infinity,maxZ=-Infinity;
  REGIONS.forEach(r=>{const cz=(r.minH+r.maxH)/2,hz=(r.maxH-r.minH)/2;if(r.cx-r.hx<minX)minX=r.cx-r.hx;if(r.cx+r.hx>maxX)maxX=r.cx+r.hx;if(r.cy-r.hy<minY)minY=r.cy-r.hy;if(r.cy+r.hy>maxY)maxY=r.cy+r.hy;if(cz-hz<minZ)minZ=cz-hz;if(cz+hz>maxZ)maxZ=cz+hz;});
  const bcx=(minX+maxX)/2,bcy=(minY+maxY)/2,bcz=(minZ+maxZ)/2;
  const bhx=(maxX-minX)/2,bhy=(maxY-minY)/2,bhz=(maxZ-minZ)/2;
  const tileset={asset:{version:'1.0',gltfUpAxis:'Z'},geometricError:500,root:{boundingVolume:{box:[bcx,bcy,bcz,bhx,0,0,0,bhy,0,0,0,bhz]},geometricError:100,refine:'REPLACE',content:{uri:'tile.b3dm'}}};
  fs.writeFileSync(path.join(OUT,'tileset.json'),JSON.stringify(tileset,null,2));

  console.log(`\n✅ 完成！${REGIONS.length} 个半透明(0.3)空域+线框(0.8):`);
  REGIONS.forEach(r=>console.log(`  - ${r.name}`));
}

main().catch(err=>{console.error('\n❌ 失败:',err.message);process.exit(1);});
