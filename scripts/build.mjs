import {readFile, writeFile, mkdir} from 'node:fs/promises';
const template = await readFile(new URL('../src/index.html', import.meta.url), 'utf8');
const core = (await readFile(new URL('../src/core.mjs', import.meta.url), 'utf8')).replaceAll('export function ', 'function ');
await mkdir(new URL('../dist/', import.meta.url), {recursive: true});
await writeFile(new URL('../dist/index.html', import.meta.url), template.replace('/* SHARED_CALCULATION_CORE */', core));
console.log('Built dist/index.html (standalone HTML, no external libraries or network requests).');
