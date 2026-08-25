import { access, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { inflateRawSync } from 'node:zlib';

const OFFICIAL_FONT_URLS = [
  'https://developer.huawei.com/images/download/next/HarmonyOS-Sans.zip',
  'https://developer.huawei.com/images/download/general/HarmonyOS-Sans.zip',
];

const FONT_STYLES = [
  { style: 'Light', weight: 300, required: false },
  { style: 'Regular', weight: 400, required: true },
  { style: 'Medium', weight: 500, required: true },
  { style: 'Semibold', weight: 600, required: false },
  { style: 'Bold', weight: 700, required: true },
];

const MAX_ZIP_ENTRY_SIZE = 64 * 1024 * 1024;
const ZIP_LOCAL_FILE_SIGNATURE = 0x04034b50;
const ZIP_CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
const ZIP_END_SIGNATURE = 0x06054b50;

const rootDir = process.cwd();
const fontDir = path.join(rootDir, 'public', 'fonts', 'harmonyos-sans-sc');
const fontCssPath = path.join(fontDir, 'font.css');
const licenseDir = path.join(rootDir, 'public', 'licenses');
const licensePath = path.join(licenseDir, 'HarmonyOS-Sans-LICENSE.txt');

const normalize = (value) => value.toLowerCase().replace(/[^a-z0-9]/g, '');
const basename = (entryName) => path.posix.basename(entryName.replaceAll('\\', '/'));

const exists = async (filePath) => {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
};

const loadFontArchive = async () => {
  const localArchive = process.env.HARMONYOS_SANS_ZIP;
  if (localArchive) {
    const archivePath = path.resolve(rootDir, localArchive);
    return {
      buffer: await readFile(archivePath),
      source: archivePath,
    };
  }

  const configuredUrl = process.env.HARMONYOS_SANS_ZIP_URL;
  const urls = configuredUrl ? [configuredUrl] : OFFICIAL_FONT_URLS;

  let lastError;
  for (const url of urls) {
    try {
      console.log(`[font] Downloading HarmonyOS Sans from ${url}`);
      const response = await fetch(url, { redirect: 'follow' });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const buffer = Buffer.from(await response.arrayBuffer());
      if (buffer.length < 1024 * 1024) {
        throw new Error('downloaded archive is unexpectedly small');
      }

      return { buffer, source: url };
    } catch (error) {
      lastError = error;
      console.warn(`[font] Failed to download ${url}: ${error.message}`);
    }
  }

  throw new Error(
    `Unable to obtain HarmonyOS Sans. Set HARMONYOS_SANS_ZIP to an official local ZIP when building offline. Last error: ${lastError?.message ?? 'unknown'}`,
  );
};

const findEndOfCentralDirectory = (buffer) => {
  const minimumOffset = Math.max(0, buffer.length - 0xffff - 22);
  for (let offset = buffer.length - 22; offset >= minimumOffset; offset -= 1) {
    if (buffer.readUInt32LE(offset) === ZIP_END_SIGNATURE) {
      return offset;
    }
  }
  throw new Error('Invalid ZIP: end of central directory not found.');
};

const readZipEntries = (buffer) => {
  const endOffset = findEndOfCentralDirectory(buffer);
  const totalEntries = buffer.readUInt16LE(endOffset + 10);
  let offset = buffer.readUInt32LE(endOffset + 16);
  const entries = [];

  if (offset === 0xffffffff) {
    throw new Error('ZIP64 archives are not supported.');
  }

  for (let index = 0; index < totalEntries; index += 1) {
    if (buffer.readUInt32LE(offset) !== ZIP_CENTRAL_DIRECTORY_SIGNATURE) {
      throw new Error('Invalid ZIP: central directory entry is malformed.');
    }

    const flags = buffer.readUInt16LE(offset + 8);
    const compressionMethod = buffer.readUInt16LE(offset + 10);
    const compressedSize = buffer.readUInt32LE(offset + 20);
    const uncompressedSize = buffer.readUInt32LE(offset + 24);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const localHeaderOffset = buffer.readUInt32LE(offset + 42);
    const nameStart = offset + 46;
    const nameEnd = nameStart + nameLength;
    const entryName = buffer.toString('utf8', nameStart, nameEnd);

    entries.push({
      entryName,
      flags,
      compressionMethod,
      compressedSize,
      uncompressedSize,
      localHeaderOffset,
      isDirectory: entryName.endsWith('/'),
    });

    offset = nameEnd + extraLength + commentLength;
  }

  return entries;
};

const extractZipEntry = (buffer, entry) => {
  if (entry.flags & 0x1) {
    throw new Error(`Encrypted ZIP entry is not supported: ${entry.entryName}`);
  }
  if (entry.uncompressedSize > MAX_ZIP_ENTRY_SIZE) {
    throw new Error(`ZIP entry is too large: ${entry.entryName}`);
  }

  const offset = entry.localHeaderOffset;
  if (buffer.readUInt32LE(offset) !== ZIP_LOCAL_FILE_SIGNATURE) {
    throw new Error(`Invalid ZIP local header: ${entry.entryName}`);
  }

  const nameLength = buffer.readUInt16LE(offset + 26);
  const extraLength = buffer.readUInt16LE(offset + 28);
  const dataStart = offset + 30 + nameLength + extraLength;
  const dataEnd = dataStart + entry.compressedSize;
  if (dataEnd > buffer.length) {
    throw new Error(`Invalid ZIP data range: ${entry.entryName}`);
  }

  const compressed = buffer.subarray(dataStart, dataEnd);
  let output;
  if (entry.compressionMethod === 0) {
    output = Buffer.from(compressed);
  } else if (entry.compressionMethod === 8) {
    output = inflateRawSync(compressed, { maxOutputLength: MAX_ZIP_ENTRY_SIZE });
  } else {
    throw new Error(
      `Unsupported ZIP compression method ${entry.compressionMethod}: ${entry.entryName}`,
    );
  }

  if (output.length !== entry.uncompressedSize) {
    throw new Error(`ZIP entry size mismatch: ${entry.entryName}`);
  }
  return output;
};

const findFontEntry = (entries, style) => {
  const expectedName = normalize(`HarmonyOS_SansSC_${style}.ttf`);
  return entries.find(
    (entry) => !entry.isDirectory && normalize(basename(entry.entryName)) === expectedName,
  );
};

const findLicenseEntry = (entries) => {
  const licenses = entries.filter((entry) => {
    if (entry.isDirectory) return false;
    const name = normalize(basename(entry.entryName));
    return name === 'license' || name === 'licensetxt' || name === 'licensefonts';
  });

  return (
    licenses.find((entry) => normalize(entry.entryName).includes('harmonyossanssc')) ??
    licenses.find((entry) => normalize(basename(entry.entryName)) === 'licensefonts') ??
    licenses[0]
  );
};

const buildFontCss = (fonts) =>
  `${fonts
    .map(
      ({ filename, style, weight }) => `@font-face {
  font-family: 'HarmonyOS Sans SC';
  font-style: normal;
  font-weight: ${weight};
  font-display: swap;
  src: local('HarmonyOS Sans SC ${style}'),
    url('/fonts/harmonyos-sans-sc/${filename}') format('truetype');
}`,
    )
    .join('\n\n')}\n`;

const main = async () => {
  if (
    process.env.HARMONYOS_SANS_FORCE !== '1' &&
    (await exists(fontCssPath)) &&
    (await exists(licensePath))
  ) {
    console.log('[font] HarmonyOS Sans SC assets already exist; skipping setup.');
    return;
  }

  const { buffer, source } = await loadFontArchive();
  const entries = readZipEntries(buffer);

  const selectedFonts = FONT_STYLES.flatMap(({ style, weight, required }) => {
    const entry = findFontEntry(entries, style);
    if (!entry) {
      if (required) {
        throw new Error(`Official HarmonyOS Sans archive is missing the ${style} SC font.`);
      }
      console.warn(`[font] Optional ${style} font not found; continuing.`);
      return [];
    }

    return [{ entry, filename: basename(entry.entryName), style, weight }];
  });

  const licenseEntry = findLicenseEntry(entries);
  if (!licenseEntry) {
    throw new Error('Official HarmonyOS Sans archive does not contain a font license file.');
  }

  await rm(fontDir, { recursive: true, force: true });
  await mkdir(fontDir, { recursive: true });
  await mkdir(licenseDir, { recursive: true });

  for (const { entry, filename } of selectedFonts) {
    await writeFile(path.join(fontDir, filename), extractZipEntry(buffer, entry));
  }
  await writeFile(fontCssPath, buildFontCss(selectedFonts), 'utf8');
  await writeFile(licensePath, extractZipEntry(buffer, licenseEntry));

  console.log(
    `[font] HarmonyOS Sans SC ready (${selectedFonts.length} weights, source: ${source}).`,
  );
};

main().catch((error) => {
  console.error(`[font] ${error.message}`);
  process.exitCode = 1;
});
