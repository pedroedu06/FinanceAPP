// Apoio comum aos formularios: mostrar e limpar erros, e travar o botao durante o envio.
// Todo texto vindo da API entra como textContent, nunca como HTML.

export function clearErrors(form, alertElement) {
  alertElement.hidden = true;
  alertElement.textContent = "";
  for (const error of form.querySelectorAll(".field-error")) {
    error.hidden = true;
    error.textContent = "";
  }
  for (const input of form.querySelectorAll("input")) {
    input.removeAttribute("aria-invalid");
  }
}

/** Mostra o erro da API: por campo quando houver, senao no aviso do topo do formulario. */
export function showError(form, alertElement, error) {
  let shownInField = false;
  for (const [field, message] of Object.entries(error.fieldErrors || {})) {
    const slot = form.querySelector(`[data-error-for="${CSS.escape(field)}"]`);
    const input = form.elements.namedItem(field);
    if (slot && input) {
      slot.textContent = message;
      slot.hidden = false;
      input.setAttribute("aria-invalid", "true");
      shownInField = true;
    }
  }
  if (!shownInField) {
    alertElement.textContent = error.message || "Algo deu errado. Tente de novo.";
    alertElement.hidden = false;
  }
}

/** Roda a acao com o botao de envio travado, para nao mandar o formulario duas vezes. */
export async function submitting(form, action) {
  const button = form.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    await action();
  } finally {
    button.disabled = false;
  }
}
