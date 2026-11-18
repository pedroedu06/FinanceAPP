// DADOS FICTICIOS. Alimentam so os cartoes marcados como "exemplo" no painel, para
// ajudar a decidir o que o produto vai ter. Nada aqui vem da API; quando uma
// funcionalidade virar real, o cartao passa a ler da API e a entrada daqui some.

export const sampleData = {
  balance: { income: 5200.0, expense: 3847.35 },

  transactions: [
    { date: "2026-11-14", description: "Supermercado", category: "Alimentação", amount: -412.87 },
    { date: "2026-11-12", description: "Conta de luz", category: "Moradia", amount: -189.4 },
    { date: "2026-11-10", description: "Freelance", category: "Renda extra", amount: 700.0 },
    { date: "2026-11-08", description: "Combustível", category: "Transporte", amount: -230.0 },
    { date: "2026-11-05", description: "Salário", category: "Salário", amount: 4500.0 },
  ],

  categories: [
    { name: "Moradia", amount: 1450.0 },
    { name: "Alimentação", amount: 980.5 },
    { name: "Transporte", amount: 540.0 },
    { name: "Lazer", amount: 420.85 },
    { name: "Outros", amount: 456.0 },
  ],

  goals: [
    { name: "Reserva de emergência", saved: 6500.0, target: 15000.0 },
    { name: "Viagem de férias", saved: 2100.0, target: 5000.0 },
  ],

  bills: [
    { name: "Aluguel", dueDate: "2026-11-20", amount: 1450.0 },
    { name: "Internet", dueDate: "2026-11-22", amount: 109.9 },
    { name: "Cartão de crédito", dueDate: "2026-11-28", amount: 1238.6 },
  ],
};
