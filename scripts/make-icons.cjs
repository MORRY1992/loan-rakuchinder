const fs = require('node:fs');
const path = require('node:path');
const sharp = require(require.resolve('sharp', {paths: [process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES]}));
const root = path.resolve(__dirname, '..');
const paths = '<path d="M50 4 91 20 85 71Q74 96 50 108 26 96 15 71L9 20Z" fill="#ecc36f"/><path d="M50 12 82 25 77 68Q69 88 50 99 31 88 23 68L18 25Z" fill="#233b57"/><path d="m56 23-29 37h20l-7 28 33-44H53Z" fill="#ecc36f"/><path d="m72 22 10 4-3 23-10-5Z" fill="#d64237"/><path d="m20 26 10-4 4 16-12 5Z" fill="#fff" opacity=".75"/>';
function svg(w,h,background=true,scale=.66,round=false) {
 const size=Math.min(w,h)*scale;
 const shape=round?`<circle cx="${w/2}" cy="${h/2}" r="${Math.min(w,h)/2}" fill="#101f35"/>`:`<path d="M0 0H${w}V${h}H0Z" fill="#101f35"/>`;
 return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${background?shape:''}<g transform="translate(${w/2-size*100/112/2} ${h/2-size/2}) scale(${size/112})">${paths}</g></svg>`;
}
(async()=>{
 fs.writeFileSync(path.join(root,'assets/launcher.svg'),svg(512,512));
 await sharp(Buffer.from(svg(512,512))).png().toFile(path.join(root,'assets/launcher-512.png'));
 const res=path.join(root,'android/app/src/main/res');
 for(const dir of fs.readdirSync(res)){
   const folder=path.join(res,dir); if(!fs.statSync(folder).isDirectory())continue;
   for(const filename of fs.readdirSync(folder)){
     if(!filename.endsWith('.png'))continue;
     const file=path.join(folder,filename);const {width,height}=await sharp(file).metadata();
     const adaptive=filename==='ic_launcher_foreground.png';
     const splash=filename==='splash.png';
     const source=svg(width,height,!adaptive,splash?.22:adaptive?.52:.7,filename==='ic_launcher_round.png');
     await sharp(Buffer.from(source)).png().toBuffer().then(data=>fs.writeFileSync(file,data));
   }
 }
 const colorFile=path.join(res,'values/ic_launcher_background.xml');
 fs.writeFileSync(colorFile,'<?xml version="1.0" encoding="utf-8"?><resources><color name="ic_launcher_background">#101f35</color></resources>');
 console.log('Generated original shield icons and splash images.');
})();
