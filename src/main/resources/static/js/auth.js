// Tela de entrada: abas "Entrar" e "Criar conta".

import { api, getToken } from "./api.js";
import { clearErrors, showError, submitting } from "./forms.js";

const DASHBOARD_PAGE = "/dashboard.html";

const alertElement = document.getElementById("form-alert");
const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const tabs = [
  { button: document.getElementById("tab-login"), form: loginForm },
  { button: document.getElementById("tab-register"), form: registerForm },
];

function selectTab(selected) {
  for (const tab of tabs) {
    const active = tab === selected;
    tab.button.setAttribute("aria-selected", String(active));
    tab.form.hidden = !active;
    clearErrors(tab.form, alertElement);
  }
  selected.form.querySelector("input").focus();
}

for (const tab of tabs) {
  tab.button.addEventListener("click", () => selectTab(tab));
}

loginForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearErrors(loginForm, alertElement);
  submitting(loginForm, async () => {
    try {
      await api.login(loginForm.elements.email.value, loginForm.elements.password.value);
      location.assign(DASHBOARD_PAGE);
    } catch (error) {
      showError(loginForm, alertElement, error);
    }
  });
});

registerForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearErrors(registerForm, alertElement);
  const fields = registerForm.elements;
  const payDay = fields.payDay.value === "" ? null : Number(fields.payDay.value);
  submitting(registerForm, async () => {
    try {
      await api.register({
        name: fields.name.value,
        email: fields.email.value,
        password: fields.password.value,
        payDay,
      });
      await api.login(fields.email.value, fields.password.value);
      location.assign(DASHBOARD_PAGE);
    } catch (error) {
      showError(registerForm, alertElement, error);
    }
  });
});

// Quem ja tem uma sessao valida vai direto para o painel.
if (getToken()) {
  api.me({ redirectOnUnauthorized: false })
    .then(() => location.replace(DASHBOARD_PAGE))
    .catch(() => {});
}
