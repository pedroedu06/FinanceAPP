// Painel: dados reais da conta (API) e cartoes de exemplo (sample-data.js).

import { api, clearToken, getToken } from "./api.js";
import { clearErrors, showError, submitting } from "./forms.js";
import { sampleData } from "./sample-data.js";

const LOGIN_PAGE = "/";

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const longDate = new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" });
const shortDate = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" });
const weekdayDate = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "numeric", month: "long" });

const byId = (id) => document.getElementById(id);

/** Cria um elemento com texto. O texto sempre entra como textContent. */
function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) {
    node.textContent = text;
  }
  if (className) {
    node.className = className;
  }
  return node;
}

/** Data local a partir de "AAAA-MM-DD", sem deslocamento de fuso. */
function localDate(isoDay) {
  const [year, month, day] = isoDay.split("-").map(Number);
  return new Date(year, month - 1, day);
}

/** Proxima data do dia de pagamento. Em mes mais curto, vale o ultimo dia do mes. */
function nextPayday(payDay, today) {
  const inMonth = (year, month) => {
    const lastDay = new Date(year, month + 1, 0).getDate();
    return new Date(year, month, Math.min(payDay, lastDay));
  };
  const thisMonth = inMonth(today.getFullYear(), today.getMonth());
  return thisMonth >= today ? thisMonth : inMonth(today.getFullYear(), today.getMonth() + 1);
}

// ---------- dados reais ----------

let account = null;

function renderAccount() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const firstName = account.name.trim().split(/\s+/)[0];

  byId("greeting").textContent = `Olá, ${firstName}`;
  byId("today").textContent = weekdayDate.format(today);
  byId("topbar-user").textContent = account.email;
  byId("account-name").textContent = account.name;
  byId("account-email").textContent = account.email;
  byId("account-created").textContent = longDate.format(new Date(account.createdAt));

  const payday = nextPayday(account.payDay, today);
  const days = Math.round((payday - today) / 86_400_000);
  byId("payday-countdown").textContent = days === 0 ? "É hoje" : days === 1 ? "Falta 1 dia" : `Faltam ${days} dias`;
  byId("payday-date").textContent = `Dia ${account.payDay} de cada mês · próximo em ${longDate.format(payday)}`;
}

// ---------- cartoes de exemplo ----------

function barItem(label, value, ratio) {
  const item = element("li");
  const header = element("div", undefined, "bar-label");
  header.append(element("span", label), element("span", value));
  const track = element("div", undefined, "bar-track");
  const fill = element("div", undefined, "bar-fill");
  fill.style.width = `${Math.round(Math.min(1, Math.max(0, ratio)) * 100)}%`;
  track.append(fill);
  item.append(header, track);
  return item;
}

function renderSamples() {
  const { balance, transactions, categories, goals, bills } = sampleData;

  byId("balance-total").textContent = money.format(balance.income - balance.expense);
  byId("balance-income").textContent = money.format(balance.income);
  byId("balance-expense").textContent = money.format(balance.expense);

  byId("transactions-body").replaceChildren(...transactions.map((transaction) => {
    const row = element("tr");
    const amount = element("td", money.format(transaction.amount), "numeric");
    amount.classList.add(transaction.amount < 0 ? "negative" : "positive");
    row.append(
      element("td", shortDate.format(localDate(transaction.date))),
      element("td", transaction.description),
      element("td", transaction.category),
      amount,
    );
    return row;
  }));

  const largest = Math.max(...categories.map((category) => category.amount));
  byId("categories-list").replaceChildren(...categories.map((category) =>
    barItem(category.name, money.format(category.amount), category.amount / largest)));

  byId("goals-list").replaceChildren(...goals.map((goal) =>
    barItem(goal.name, `${money.format(goal.saved)} de ${money.format(goal.target)}`, goal.saved / goal.target)));

  byId("bills-list").replaceChildren(...bills.map((bill) => {
    const item = element("li");
    const description = element("span", bill.name);
    description.append(element("small", `vence em ${shortDate.format(localDate(bill.dueDate))}`));
    item.append(description, element("span", money.format(bill.amount)));
    return item;
  }));
}

// ---------- editar perfil ----------

const dialog = byId("edit-dialog");
const editForm = byId("edit-form");
const editAlert = byId("edit-alert");

byId("edit-button").addEventListener("click", () => {
  clearErrors(editForm, editAlert);
  editForm.elements.name.value = account.name;
  editForm.elements.payDay.value = account.payDay;
  dialog.showModal();
});

byId("edit-cancel").addEventListener("click", () => dialog.close());

editForm.addEventListener("submit", (event) => {
  event.preventDefault();
  clearErrors(editForm, editAlert);
  const fields = editForm.elements;
  const payDay = fields.payDay.value === "" ? null : Number(fields.payDay.value);
  submitting(editForm, async () => {
    try {
      account = await api.updateMe({ name: fields.name.value, payDay });
      renderAccount();
      dialog.close();
    } catch (error) {
      showError(editForm, editAlert, error);
    }
  });
});

// ---------- sair ----------

byId("logout-button").addEventListener("click", () => {
  clearToken();
  location.replace(LOGIN_PAGE);
});

// ---------- inicio ----------

async function start() {
  if (!getToken()) {
    location.replace(LOGIN_PAGE);
    return;
  }
  try {
    account = await api.me();
  } catch (error) {
    // Em 401 a api.js ja voltou para a entrada. Qualquer outro erro: sem dados nao ha painel.
    if (error.status !== 401) {
      location.replace(LOGIN_PAGE);
    }
    return;
  }
  renderAccount();
  renderSamples();
  byId("dashboard").hidden = false;
}

start();
