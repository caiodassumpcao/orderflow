/** Papéis do realm do Keycloak usados pela aplicação. */
export const Role = {
  User: 'USER',
  Admin: 'ADMIN',
} as const;

export type Role = (typeof Role)[keyof typeof Role];

/**
 * Lê `realm_access.roles` do access token, só para decidir o que a interface mostra. A API valida
 * a assinatura e aplica a autorização de verdade; aqui nada é confiado para segurança.
 */
export function rolesFromAccessToken(token: string | null | undefined): Role[] {
  const payload = token?.split('.')[1];
  if (!payload) {
    return [];
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = new TextDecoder().decode(
      Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) =>
        c.charCodeAt(0),
      ),
    );
    const roles: unknown = JSON.parse(json)?.realm_access?.roles;
    if (!Array.isArray(roles)) {
      return [];
    }
    const known: readonly string[] = Object.values(Role);
    return roles.filter((role): role is Role => typeof role === 'string' && known.includes(role));
  } catch {
    return [];
  }
}
