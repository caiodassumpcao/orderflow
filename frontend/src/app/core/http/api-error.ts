import { HttpErrorResponse } from '@angular/common/http';

/** Base do `type` dos Problem Details da API (docs/api/problems.md). */
const PROBLEM_TYPE_BASE =
  'https://github.com/caiodassumpcao/orderflow/blob/main/docs/api/problems.md#';

export interface FieldError {
  readonly field: string;
  readonly message: string;
}

/** Erro da API pronto para exibir: mensagem em português, campos inválidos e dados de suporte. */
export interface ApiError {
  readonly status: number;
  /** Âncora do catálogo de erros (ex.: `order-state-conflict`), quando houver. */
  readonly problemType: string | null;
  readonly message: string;
  readonly fieldErrors: readonly FieldError[];
  readonly retryAfterSeconds: number | null;
  readonly correlationId: string | null;
}

interface ProblemBody {
  type?: unknown;
  errors?: unknown;
  correlationId?: unknown;
}

const MESSAGES_BY_TYPE: Readonly<Record<string, string>> = {
  'order-not-found': 'Pedido não encontrado.',
  'order-state-conflict': 'O pedido mudou de status e a operação não é mais permitida.',
  'concurrent-modification': 'O pedido foi alterado por outra operação. Tente novamente.',
  'invalid-order': 'O pedido não pôde ser aceito. Revise os itens e tente novamente.',
  'idempotency-key-reused': 'Esta solicitação já foi usada com outros dados. Envie de novo.',
  'stock-item-not-found': 'Produto não encontrado.',
  'stock-item-already-exists': 'Já existe um produto com este SKU.',
  'stock-item-modified':
    'O produto mudou desde a última leitura. Confira as quantidades e confirme de novo.',
  'precondition-required': 'A operação precisa da versão atual do produto. Recarregue a página.',
  'stock-limit-exceeded': 'A reposição ultrapassa o limite de unidades do produto.',
  'validation-error': 'Alguns campos estão inválidos. Corrija e tente novamente.',
};

function messageByStatus(status: number, retryAfter: number | null): string {
  switch (status) {
    case 0:
      return 'Não foi possível falar com o servidor. Verifique sua conexão e tente novamente.';
    case 400:
      return MESSAGES_BY_TYPE['validation-error'];
    case 401:
      return 'Sua sessão expirou. Entre novamente para continuar.';
    case 403:
      return 'Você não tem permissão para esta operação.';
    case 404:
      return 'O recurso procurado não existe.';
    case 429:
      return retryAfter
        ? `Muitas solicitações em pouco tempo. Tente novamente em ${retryAfter} s.`
        : 'Muitas solicitações em pouco tempo. Aguarde um instante.';
    default:
      return status >= 500
        ? 'O servidor encontrou um erro. Tente novamente em instantes.'
        : 'Não foi possível concluir a operação.';
  }
}

function fieldErrors(errors: unknown): FieldError[] {
  if (!Array.isArray(errors)) {
    return [];
  }
  return errors.flatMap((error) =>
    typeof error?.field === 'string' && typeof error?.message === 'string'
      ? [{ field: error.field, message: error.message }]
      : [],
  );
}

/** Converte qualquer falha de chamada HTTP em {@link ApiError}. */
export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      status: 0,
      problemType: null,
      message: 'Ocorreu um erro inesperado. Tente novamente.',
      fieldErrors: [],
      retryAfterSeconds: null,
      correlationId: null,
    };
  }
  const body: ProblemBody = typeof error.error === 'object' && error.error ? error.error : {};
  const problemType =
    typeof body.type === 'string' && body.type.startsWith(PROBLEM_TYPE_BASE)
      ? body.type.slice(PROBLEM_TYPE_BASE.length)
      : null;
  const retryAfterHeader = Number(error.headers?.get('Retry-After'));
  const retryAfterSeconds =
    Number.isInteger(retryAfterHeader) && retryAfterHeader > 0 ? retryAfterHeader : null;
  return {
    status: error.status,
    problemType,
    message:
      (problemType && MESSAGES_BY_TYPE[problemType]) ||
      messageByStatus(error.status, retryAfterSeconds),
    fieldErrors: fieldErrors(body.errors),
    retryAfterSeconds,
    correlationId:
      typeof body.correlationId === 'string'
        ? body.correlationId
        : (error.headers?.get('X-Correlation-Id') ?? null),
  };
}
