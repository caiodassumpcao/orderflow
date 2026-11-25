import Ajv2020, { ValidateFunction } from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parse } from 'yaml';

/**
 * Contrato da API publicado pelo backend (docs/openapi.yaml, conferido no build do backend). Os
 * testes do frontend validam contra ele o que a SPA envia e o que ela espera receber, e a API falsa
 * dos e2e valida as próprias respostas: nenhum dos dois consegue divergir da API real em silêncio.
 */
const CONTRACT_PATH = resolve(__dirname, '../../../docs/openapi.yaml');

type Json = Record<string, unknown>;
interface Operation {
  requestBody?: { content: Record<string, { schema: Json }> };
  responses?: Record<string, { content?: Record<string, { schema: Json }> }>;
}
interface Spec {
  paths: Record<string, Record<string, Operation>>;
  components: { schemas: Record<string, Json> };
}

const spec = parse(readFileSync(CONTRACT_PATH, 'utf8')) as Spec;

/**
 * O contrato não fecha os objetos (additionalProperties); aqui eles são fechados, para que um
 * campo com nome errado ou inexistente na API seja apontado em vez de ignorado.
 */
function closeObjects(schema: unknown): void {
  if (Array.isArray(schema)) {
    schema.forEach(closeObjects);
    return;
  }
  if (typeof schema !== 'object' || schema === null) {
    return;
  }
  const node = schema as Json;
  if (node['properties'] && node['additionalProperties'] === undefined) {
    node['additionalProperties'] = false;
  }
  Object.values(node).forEach(closeObjects);
}
closeObjects(spec.components.schemas);

const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
for (const format of ['int32', 'int64', 'float', 'double']) {
  ajv.addFormat(format, true);
}
ajv.addSchema({ $id: 'openapi', components: spec.components });

const templates = Object.keys(spec.paths).map((template) => ({
  template,
  pattern: new RegExp(`^${template.replace(/\{[^/]+\}/g, '[^/]+')}$`),
}));

/** Caminho do contrato que atende a URL real (ex.: /api/v1/orders/abc → /api/v1/orders/{orderId}). */
export function contractPath(path: string): string {
  const match = templates.find(({ pattern }) => pattern.test(path));
  if (!match) {
    throw new Error(`${path} não existe no contrato da API`);
  }
  return match.template;
}

function operation(method: string, path: string): Operation {
  const found = spec.paths[contractPath(path)]?.[method.toLowerCase()];
  if (!found) {
    throw new Error(`${method} ${path} não existe no contrato da API`);
  }
  return found;
}

function compile(schema: Json | undefined, what: string): ValidateFunction {
  if (!schema) {
    throw new Error(`${what} não tem corpo JSON no contrato`);
  }
  // Os $ref do contrato apontam para "#/components/..."; aqui resolvem no esquema "openapi".
  const rebased = JSON.parse(
    JSON.stringify(schema).replace(/"#\/components\//g, '"openapi#/components/'),
  );
  return ajv.compile(rebased);
}

function check(validate: ValidateFunction, body: unknown, what: string): void {
  if (!validate(body)) {
    throw new Error(
      `${what} fora do contrato: ${ajv.errorsText(validate.errors, { separator: '; ' })}`,
    );
  }
}

/** Falha se o corpo da resposta não for o que o contrato promete para esse status. */
export function expectResponseToMatchContract(
  method: string,
  path: string,
  status: number,
  body: unknown,
): void {
  const what = `Resposta ${status} de ${method} ${path}`;
  const response = operation(method, path).responses?.[String(status)];
  if (!response) {
    throw new Error(`${what}: status não documentado no contrato`);
  }
  check(compile(response.content?.['application/json']?.schema, what), body, what);
}

/** Falha se o corpo enviado não for aceito pelo contrato. */
export function expectRequestToMatchContract(method: string, path: string, body: unknown): void {
  const what = `Corpo de ${method} ${path}`;
  check(
    compile(operation(method, path).requestBody?.content['application/json']?.schema, what),
    body,
    what,
  );
}
