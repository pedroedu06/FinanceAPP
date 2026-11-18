// Chamadas a API do financeapp. O token fica no sessionStorage: some ao fechar a aba
// e nunca e enviado sozinho pelo navegador.

const TOKEN_KEY = "financeapp.token";
const LOGIN_PAGE = "/";

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

function setToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

/** Erro devolvido pela API: status HTTP, mensagem e, na validacao, o erro de cada campo. */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function messageFor(response, problem) {
  if (response.status === 429) {
    const seconds = Number(response.headers.get("Retry-After"));
    return seconds > 0
      ? `Muitas tentativas. Aguarde ${seconds} segundos e tente de novo.`
      : "Muitas tentativas. Aguarde um pouco e tente de novo.";
  }
  if (problem && typeof problem.detail === "string") {
    return problem.detail;
  }
  return "Algo deu errado. Tente de novo.";
}

async function request(method, path, { body, auth = true, redirectOnUnauthorized = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth) {
    headers.Authorization = `Bearer ${getToken()}`;
  }

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Não foi possível falar com o servidor. Verifique se a aplicação está no ar.");
  }

  if (response.status === 204) {
    return null;
  }
  let data = null;
  try {
    data = await response.json();
  } catch {
    // Resposta sem corpo JSON.
  }
  if (response.ok) {
    return data;
  }

  // Token ausente, vencido ou invalidado: a sessao acabou.
  if (response.status === 401 && auth) {
    clearToken();
    if (redirectOnUnauthorized) {
      location.replace(LOGIN_PAGE);
    }
  }
  throw new ApiError(response.status, messageFor(response, data), (data && data.errors) || {});
}

export const api = {
  async login(email, password) {
    const token = await request("POST", "/auth/login", { body: { email, password }, auth: false });
    setToken(token.accessToken);
  },

  register(account) {
    return request("POST", "/users", { body: account, auth: false });
  },

  me({ redirectOnUnauthorized = true } = {}) {
    return request("GET", "/users/me", { redirectOnUnauthorized });
  },

  updateMe(profile) {
    return request("PUT", "/users/me", { body: profile });
  },
};
