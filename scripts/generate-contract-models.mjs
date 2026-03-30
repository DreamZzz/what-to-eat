import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const CHECK_MODE = process.argv.includes('--check');

const CONFIG_PATH = path.join(ROOT_DIR, 'contracts', 'model-sync.config.json');
const BACKEND_SRC_DIR = path.join(ROOT_DIR, 'backend', 'src', 'main', 'java');
const GENERATED_REGISTRY_PATH = path.join(ROOT_DIR, 'contracts', 'generated', 'model-registry.json');
const GENERATED_FRONTEND_PATH = path.join(ROOT_DIR, 'frontend', 'src', 'shared', 'models', 'generatedContracts.js');
const GENERATED_FRONTEND_INDEX_PATH = path.join(ROOT_DIR, 'frontend', 'src', 'shared', 'models', 'index.js');
const GENERATED_SWIFT_PATH = path.join(ROOT_DIR, 'frontend', 'ios', 'frontend', 'Generated', 'APIContractModels.swift');

function walkFiles(directory) {
  const entries = fs.readdirSync(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const resolvedPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkFiles(resolvedPath));
    } else {
      files.push(resolvedPath);
    }
  }

  return files;
}

function ensureDirectory(directory) {
  fs.mkdirSync(directory, { recursive: true });
}

function computeHash(content) {
  return crypto.createHash('sha256').update(content).digest('hex');
}

function countOccurrences(input, token) {
  return (input.match(new RegExp(`\\${token}`, 'g')) || []).length;
}

function parseJavaClass(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const classMatch = content.match(/public\s+class\s+([A-Za-z0-9_]+)/);
  if (!classMatch) {
    throw new Error(`Could not determine class name for ${filePath}`);
  }

  const lines = content.split(/\r?\n/);
  let depth = 0;
  let inClass = false;
  const fields = [];
  let annotations = [];

  for (const line of lines) {
    const trimmed = line.trim();

    if (!inClass && /\bclass\b/.test(trimmed) && trimmed.includes(classMatch[1])) {
      inClass = true;
    } else if (inClass && depth === 1 && trimmed.startsWith('@')) {
      annotations.push(trimmed);
    } else if (inClass && depth === 1) {
      const fieldMatch = trimmed.match(/^(private|protected|public)\s+(?:static\s+)?(?:final\s+)?([A-Za-z0-9_<>, ?\[\]]+)\s+([A-Za-z0-9_]+)\s*(?:=\s*.+)?;$/);
      if (fieldMatch) {
        fields.push({
          name: fieldMatch[3],
          javaType: fieldMatch[2].replace(/\s+/g, ' ').trim(),
          annotations,
        });
        annotations = [];
      } else if (trimmed !== '') {
        annotations = [];
      }
    }

    depth += countOccurrences(line, '{');
    depth -= countOccurrences(line, '}');
  }

  return {
    className: classMatch[1],
    fields,
    source: content,
  };
}

function toPosixPath(targetPath) {
  return path.relative(ROOT_DIR, targetPath).split(path.sep).join('/');
}

function toConstantName(className) {
  return className
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1_$2')
    .toUpperCase();
}

function javaTypeToTypeScriptLike(javaType) {
  const trimmed = javaType.trim();

  const listMatch = trimmed.match(/^(List|Set)<(.+)>$/);
  if (listMatch) {
    return `Array<${javaTypeToTypeScriptLike(listMatch[2].trim())}>`;
  }

  const mapMatch = trimmed.match(/^Map<(.+),\s*(.+)>$/);
  if (mapMatch) {
    return 'Record<string, unknown>';
  }

  switch (trimmed) {
    case 'String':
    case 'LocalDate':
    case 'LocalDateTime':
    case 'UUID':
      return 'string';
    case 'Long':
    case 'long':
    case 'Integer':
    case 'int':
    case 'Double':
    case 'double':
    case 'Float':
    case 'float':
    case 'BigDecimal':
      return 'number';
    case 'Boolean':
    case 'boolean':
      return 'boolean';
    default:
      return trimmed;
  }
}

function javaTypeToJsDocType(javaType) {
  const mapped = javaTypeToTypeScriptLike(javaType);
  if (mapped.startsWith('Array<') || mapped.startsWith('Record<')) {
    return mapped;
  }
  return `${mapped}|null`;
}

function javaTypeToDefaultValue(javaType) {
  const mapped = javaTypeToTypeScriptLike(javaType);
  if (mapped.startsWith('Array<')) {
    return '[]';
  }
  return 'null';
}

function javaTypeToSwift(javaType) {
  const trimmed = javaType.trim();

  const listMatch = trimmed.match(/^(List|Set)<(.+)>$/);
  if (listMatch) {
    return `[${javaTypeToSwiftNonOptional(listMatch[2].trim())}]?`;
  }

  const mapMatch = trimmed.match(/^Map<(.+),\s*(.+)>$/);
  if (mapMatch) {
    return '[String: String]?';
  }

  switch (trimmed) {
    case 'String':
    case 'LocalDate':
    case 'LocalDateTime':
    case 'UUID':
      return 'String?';
    case 'Long':
    case 'long':
      return 'Int64?';
    case 'Integer':
    case 'int':
      return 'Int?';
    case 'Double':
    case 'double':
    case 'Float':
    case 'float':
    case 'BigDecimal':
      return 'Double?';
    case 'Boolean':
    case 'boolean':
      return 'Bool?';
    default:
      return `${trimmed}?`;
  }
}

function javaTypeToSwiftNonOptional(javaType) {
  const swiftType = javaTypeToSwift(javaType);
  return swiftType.endsWith('?') ? swiftType.slice(0, -1) : swiftType;
}

function compatibleJavaTypes(entityType, dtoType) {
  const normalize = (value) => {
    switch (value) {
      case 'long':
        return 'Long';
      case 'int':
        return 'Integer';
      case 'double':
        return 'Double';
      case 'float':
        return 'Float';
      case 'boolean':
        return 'Boolean';
      default:
        return value;
    }
  };

  return normalize(entityType) === normalize(dtoType);
}

function buildFrontendContent(models) {
  const lines = [];
  lines.push('// AUTO-GENERATED BY scripts/generate-contract-models.mjs. DO NOT EDIT.');
  lines.push('');

  for (const model of models) {
    lines.push('/**');
    lines.push(` * @typedef {Object} ${model.className}`);
    for (const field of model.fields) {
      lines.push(` * @property {${javaTypeToJsDocType(field.javaType)}} ${field.name}`);
    }
    lines.push(' */');
    lines.push('');

    const constantName = toConstantName(model.className);
    lines.push(`export const ${constantName}_FIELDS = Object.freeze([`);
    for (const field of model.fields) {
      lines.push(`  '${field.name}',`);
    }
    lines.push(']);');
    lines.push('');

    lines.push('/**');
    lines.push(` * @param {Partial<${model.className}>} [overrides={}]`);
    lines.push(` * @returns {${model.className}}`);
    lines.push(' */');
    lines.push(`export function create${model.className}(overrides = {}) {`);
    lines.push('  return {');
    for (const field of model.fields) {
      lines.push(`    ${field.name}: ${javaTypeToDefaultValue(field.javaType)},`);
    }
    lines.push('    ...overrides,');
    lines.push('  };');
    lines.push('}');
    lines.push('');
  }

  lines.push('export const CONTRACT_MODEL_NAMES = Object.freeze([');
  for (const model of models) {
    lines.push(`  '${model.className}',`);
  }
  lines.push(']);');
  lines.push('');
  lines.push('export const CONTRACT_MODEL_FACTORIES = Object.freeze({');
  for (const model of models) {
    lines.push(`  ${model.className}: create${model.className},`);
  }
  lines.push('});');
  lines.push('');
  return `${lines.join('\n')}\n`;
}

function buildFrontendIndexContent() {
  return "export * from './generatedContracts';\n";
}

function buildSwiftContent(models) {
  const lines = [];
  lines.push('// AUTO-GENERATED BY scripts/generate-contract-models.mjs. DO NOT EDIT.');
  lines.push('import Foundation');
  lines.push('');

  for (const model of models) {
    lines.push(`public struct ${model.className}: Codable, Equatable, Sendable {`);
    for (const field of model.fields) {
      lines.push(`    public var ${field.name}: ${javaTypeToSwift(field.javaType)}`);
    }
    lines.push('');

    lines.push('    public init(');
    model.fields.forEach((field, index) => {
      const suffix = index === model.fields.length - 1 ? '' : ',';
      lines.push(`        ${field.name}: ${javaTypeToSwift(field.javaType)} = nil${suffix}`);
    });
    lines.push('    ) {');
    for (const field of model.fields) {
      lines.push(`        self.${field.name} = ${field.name}`);
    }
    lines.push('    }');
    lines.push('}');
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

function writeFileIfChanged(filePath, nextContent, changedFiles) {
  const exists = fs.existsSync(filePath);
  const previousContent = exists ? fs.readFileSync(filePath, 'utf8') : null;

  if (previousContent === nextContent) {
    return;
  }

  changedFiles.push(toPosixPath(filePath));
  if (!CHECK_MODE) {
    ensureDirectory(path.dirname(filePath));
    fs.writeFileSync(filePath, nextContent, 'utf8');
  }
}

function main() {
  const config = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  const dtoFiles = walkFiles(BACKEND_SRC_DIR)
    .filter((filePath) => filePath.endsWith('.java') && filePath.includes(`${path.sep}dto${path.sep}`))
    .sort();

  const dtoModels = dtoFiles.map((filePath) => {
    const parsed = parseJavaClass(filePath);
    return {
      ...parsed,
      sourcePath: toPosixPath(filePath),
      sourceHash: computeHash(parsed.source),
    };
  });

  const entityReport = config.entityContracts.map((mapping) => {
    const entityPath = path.join(ROOT_DIR, mapping.entity);
    const dtoPath = path.join(ROOT_DIR, mapping.dto);
    const entity = parseJavaClass(entityPath);
    const dto = parseJavaClass(dtoPath);

    const ignoreFields = new Set(mapping.ignoreEntityFields || []);
    const dtoOnlyFields = new Set(mapping.dtoOnlyFields || []);
    const expectedSharedFields = entity.fields.filter((field) => {
      if (ignoreFields.has(field.name)) {
        return false;
      }
      return !field.annotations.some((annotation) => annotation.startsWith('@JsonIgnore'));
    });

    const dtoFieldByName = new Map(dto.fields.map((field) => [field.name, field]));
    const problems = [];

    for (const field of expectedSharedFields) {
      const dtoField = dtoFieldByName.get(field.name);
      if (!dtoField) {
        problems.push(`Missing DTO field "${field.name}" in ${mapping.dto}`);
        continue;
      }
      if (!compatibleJavaTypes(field.javaType, dtoField.javaType)) {
        problems.push(
          `Type mismatch for "${field.name}": entity=${field.javaType}, dto=${dtoField.javaType}`,
        );
      }
    }

    for (const field of dto.fields) {
      if (dtoOnlyFields.has(field.name)) {
        continue;
      }
      const entityField = expectedSharedFields.find((candidate) => candidate.name === field.name);
      if (!entityField) {
        problems.push(`DTO-only field "${field.name}" is not declared in dtoOnlyFields for ${mapping.name}`);
      }
    }

    return {
      name: mapping.name,
      entity: mapping.entity,
      dto: mapping.dto,
      entityHash: computeHash(entity.source),
      dtoHash: computeHash(dto.source),
      expectedSharedFields: expectedSharedFields.map((field) => ({
        name: field.name,
        javaType: field.javaType,
      })),
      dtoOnlyFields: Array.from(dtoOnlyFields),
      problems,
    };
  });

  const contractProblems = entityReport.flatMap((item) => item.problems.map((problem) => `${item.name}: ${problem}`));
  if (contractProblems.length > 0) {
    throw new Error(`Model contract drift detected:\n- ${contractProblems.join('\n- ')}`);
  }

  const registryContent = JSON.stringify(
    {
      sourceOfTruth: {
        entities: 'backend/src/main/java/**/model/*.java',
        dtoClasses: 'backend/src/main/java/**/dto/*.java',
      },
      outputs: {
        frontendModels: toPosixPath(GENERATED_FRONTEND_PATH),
        swiftCodable: toPosixPath(GENERATED_SWIFT_PATH),
      },
      entityContracts: entityReport,
      dtoModels: dtoModels.map((model) => ({
        className: model.className,
        sourcePath: model.sourcePath,
        sourceHash: model.sourceHash,
        fields: model.fields.map((field) => ({
          name: field.name,
          javaType: field.javaType,
        })),
      })),
    },
    null,
    2,
  );

  const frontendContent = buildFrontendContent(dtoModels);
  const frontendIndexContent = buildFrontendIndexContent();
  const swiftContent = buildSwiftContent(dtoModels);

  const changedFiles = [];
  writeFileIfChanged(GENERATED_REGISTRY_PATH, `${registryContent}\n`, changedFiles);
  writeFileIfChanged(GENERATED_FRONTEND_PATH, frontendContent, changedFiles);
  writeFileIfChanged(GENERATED_FRONTEND_INDEX_PATH, frontendIndexContent, changedFiles);
  writeFileIfChanged(GENERATED_SWIFT_PATH, swiftContent, changedFiles);

  if (CHECK_MODE && changedFiles.length > 0) {
    throw new Error(`Generated model contracts are stale:\n- ${changedFiles.join('\n- ')}`);
  }

  if (changedFiles.length === 0) {
    console.log('[model-sync] contracts already up to date');
    return;
  }

  console.log(`[model-sync] updated ${changedFiles.length} generated artifacts`);
  for (const changedFile of changedFiles) {
    console.log(`[model-sync] wrote ${changedFile}`);
  }
}

try {
  main();
} catch (error) {
  console.error(`[model-sync] ${error.message}`);
  process.exit(1);
}
