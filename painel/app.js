// Painel das páginas (DataDisasters, Eduleiteiro, PrimoPrimata), adaptado do painel do Futelupa.
// Lê só o banco do Supabase (nunca as APIs das redes); quem grava os números é o coletor.
// Abre filtrado pela página de ?pagina=slug (ou a última escolhida); "todas" junta as três.
(function () {
  const SUPABASE_URL = "https://psirslicbeuziojabglc.supabase.co";
  const SUPABASE_CHAVE_PUBLICA = "sb_publishable_6VxfQMs474kAO2eFzRlbEw_HWhmiitJ";
  const db = window.supabase.createClient(SUPABASE_URL, SUPABASE_CHAVE_PUBLICA);

  const $ = (s) => document.querySelector(s);
  const H = 3_600_000;
  // Janelas do zoom do gráfico (horas desde a publicação) e o espaço entre as marcas do eixo em cada uma.
  const JANELAS = [[2, 0.5], [4, 1], [6, 1], [12, 2], [18, 3], [24, 4], [36, 6], [72, 12]];
  const TZ = "America/Sao_Paulo";
  const id4 = (n) => String(n).padStart(4, "0");
  const n = (v) => (v == null ? "—" : Number(v).toLocaleString("pt-BR"));
  const k = (v) => (v == null ? "—" : v >= 1000 ? (v / 1000).toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + " mil" : String(v));
  const dt = (s, hora = true) => (s ? new Date(s).toLocaleString("pt-BR", { timeZone: TZ, day: "2-digit", month: "2-digit", ...(hora ? { hour: "2-digit", minute: "2-digit" } : {}) }) : "—");
  const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const soma = (...v) => (v.every((x) => x == null) ? null : v.reduce((a, x) => a + (x ?? 0), 0));
  const store = { get(x) { try { return localStorage.getItem(x); } catch { return null; } }, set(x, v) { try { localStorage.setItem(x, v); } catch {} } };

  // ---------- tema ----------
  const SOL = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>';
  const LUA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>';
  const tema = () => document.documentElement.dataset.theme || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  function botaoTema() { const d = tema() === "dark"; $("#tema").innerHTML = d ? SOL + "Claro" : LUA + "Escuro"; $("#tema").setAttribute("aria-label", d ? "Mudar para o tema claro" : "Mudar para o tema escuro"); }
  $("#tema").onclick = () => { const v = tema() === "dark" ? "light" : "dark"; document.documentElement.dataset.theme = v; store.set("paginas-tema", v); botaoTema(); };
  botaoTema();

  // ---------- ícones ----------
  const ICO = {
    drive: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M8.5 3h7l6 10.5-3.5 6h-12L2.5 13.5z"/><path d="M8.5 3l6.5 11.5M15.5 3L9 14.5M2.5 13.5h13M6 19.5l3-5"/></svg>',
    ig: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3.5" y="3.5" width="17" height="17" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.2" cy="6.8" r="1" fill="currentColor" stroke="none"/></svg>',
    tt: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M13.5 3v11.5a3.5 3.5 0 1 1-3.5-3.5"/><path d="M13.5 3c.5 2.6 2.4 4.4 5 4.6"/></svg>',
    yt: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><rect x="2.5" y="5.5" width="19" height="13" rx="4"/><path d="M10 9.5v5l4.5-2.5z" fill="currentColor"/></svg>',
  };
  // Redes que o coletor lê. "k" é o campo da linha (r.ig, r.tt, r.yt) e o prefixo das cores (--ig, .ig-t, .ig-b).
  const REDES = [
    { k: "ig", rede: "instagram", nome: "Instagram" },
    { k: "tt", rede: "tiktok", nome: "TikTok" },
    { k: "yt", rede: "youtube", nome: "YouTube" },
  ];
  const daRede = (rede) => REDES.find((x) => x.rede === rede);
  const pubsDe = (r) => REDES.map((x) => r[x.k]).filter(Boolean);
  // Só aparecem nos totais, barras e colunas as redes que já têm algum post ou seguidor na seleção.
  let ativas = REDES.slice(0, 2);
  const pcts = (vals, t) => vals.map((v) => (t ? Math.round(((v ?? 0) / t) * 100) : 0));
  const dica = (p) => ativas.map((x, i) => `${x.nome} ${p[i]}%`).join(" · ");
  function links(r) {
    const um = (tipo, url, nome) => url
      ? `<a class="ico" href="${esc(url)}" target="_blank" rel="noopener noreferrer" title="Abrir no ${nome}" aria-label="Abrir no ${nome}">${ICO[tipo]}</a>`
      : `<span class="ico off" title="${nome}: ${tipo === "drive" ? "sem pasta" : "ainda não encontrado"}" aria-label="${nome} indisponível">${ICO[tipo]}</span>`;
    return `<div class="links">${um("drive", r.drive, "Google Drive")}${ativas.map((x) => um(x.k, r[x.k]?.link, x.nome)).join("")}</div>`;
  }

  // ---------- estado ----------
  let dados = { paginas: [], conteudos: [], pubs: [], seguidores: [], mapa: [], execucao: null };
  let pagina = new URLSearchParams(location.search).get("pagina") || store.get("paginas-pagina") || "todas";
  const daPagina = (x) => pagina === "todas" || x.pagina === pagina;
  const infoPagina = (slug) => dados.paginas.find((p) => p.slug === slug) ?? { slug, nome: slug, prefixo: "" };
  let linhas = [];
  let periodo = "30", topPeriodo = "7", filtro = "todos", busca = "", ordem = { c: "chave", d: -1 };
  const abertos = new Set();
  const series = new Map(); // publicacao_id -> leituras
  const zoom = new Map(); // chave -> janela escolhida no gráfico

  // ---------- login ----------
  async function iniciar() {
    const { data } = await db.auth.getSession();
    if (data.session) mostrarPainel(); else mostrarLogin();
  }
  function mostrarLogin() {
    $("#entrar").hidden = false; $("#painel").hidden = true; $("#sair").hidden = true; $("#pagina").hidden = true; $("#coletor").textContent = "";
    $("#usuario").focus();
  }
  $("#form-login").onsubmit = async (e) => {
    e.preventDefault();
    const email = $("#usuario").value.trim();
    const botao = e.target.querySelector("button");
    botao.disabled = true; $("#erro-login").textContent = "";
    const { error } = await db.auth.signInWithPassword({ email, password: $("#senha").value });
    botao.disabled = false;
    if (error) { $("#erro-login").textContent = "Login ou senha incorretos."; return; }
    mostrarPainel();
  };
  $("#sair").onclick = async () => { await db.auth.signOut(); mostrarLogin(); };

  async function mostrarPainel() {
    $("#entrar").hidden = true; $("#painel").hidden = false; $("#sair").hidden = false;
    await carregar();
    $("#pagina").hidden = false;
  }

  // ---------- página ----------
  function seletor() {
    const opcoes = [{ slug: "todas", nome: "Todas as páginas" }, ...dados.paginas];
    if (!opcoes.some((o) => o.slug === pagina)) pagina = "todas";
    $("#pagina").innerHTML = opcoes.map((o) => `<option value="${o.slug}"${o.slug === pagina ? " selected" : ""}>${esc(o.nome)}</option>`).join("");
    document.title = pagina === "todas" ? "Painel das páginas" : `Painel · ${infoPagina(pagina).nome}`;
  }
  $("#pagina").onchange = (e) => {
    pagina = e.target.value; store.set("paginas-pagina", pagina);
    const url = new URL(location.href); url.searchParams.set("pagina", pagina); history.replaceState(null, "", url);
    abertos.clear(); carregar();
  };

  // ---------- dados ----------
  async function carregar() {
    const [pg, c, p, sg, mp, x] = await Promise.all([
      db.from("paginas").select("slug,nome,prefixo").order("nome"),
      db.from("conteudos").select("pagina,id,titulo,tipo,etapa,drive_pasta_id,legenda,criado_em,concluido_em").order("id", { ascending: false }),
      db.from("painel_publicacoes").select("*"),
      db.from("seguidores").select("pagina,rede,data,seguidores").order("data", { ascending: false }).limit(2000),
      db.rpc("painel_mapa_horas", { p_pagina: pagina === "todas" ? null : pagina }),
      db.from("execucoes").select("iniciado_em,processamento_ms,erro").order("iniciado_em", { ascending: false }).limit(1),
    ]);
    const erro = [pg, c, p, sg, mp, x].find((r) => r.error);
    if (erro) {
      if (/JWT|token/i.test(erro.error.message)) { await db.auth.signOut(); return mostrarLogin(); }
      $("#view").innerHTML = `<p class="wait">Não foi possível ler o banco: ${esc(erro.error.message)}</p>`;
      return;
    }
    dados = { paginas: pg.data, conteudos: c.data.filter(daPagina), pubs: p.data.filter(daPagina), seguidores: sg.data.filter(daPagina), mapa: mp.data, execucao: x.data[0] ?? null };
    ativas = REDES.filter((x) => dados.pubs.some((p) => p.rede === x.rede) || dados.seguidores.some((s) => s.rede === x.rede));
    if (!ativas.length) ativas = REDES.slice(0, 2);
    seletor();
    montarLinhas();
    render();
  }

  // Uma linha por post nosso (com o post de cada rede ligado a ele) e uma por post sem cadastro.
  function montarLinhas() {
    // Por rede, vale o post ainda ativo (um repost substitui o apagado); entre os ativos, o mais antigo.
    const melhor = (a, p) => !a || (p.coleta_ativa !== a.coleta_ativa ? p.coleta_ativa : p.publicado_em < a.publicado_em);
    const porConteudo = new Map();
    for (const p of dados.pubs) if (p.conteudo_id) {
      const g = porConteudo.get(`${p.pagina}:${p.conteudo_id}`) ?? {};
      const x = daRede(p.rede);
      if (x && melhor(g[x.k], p)) g[x.k] = p;
      porConteudo.set(`${p.pagina}:${p.conteudo_id}`, g);
    }
    linhas = dados.conteudos.map((c) => {
      const g = porConteudo.get(`${c.pagina}:${c.id}`) ?? {};
      const ps = pubsDe(g);
      const publicado = ps.map((p) => p.publicado_em).filter(Boolean).sort()[0] ?? null;
      return {
        chave: `${c.pagina}:${c.id}`, pagina: c.pagina, id: c.id, titulo: c.titulo, resp: infoPagina(c.pagina).nome, ig: g.ig, tt: g.tt, yt: g.yt,
        sit: publicado ? "pub" : c.etapa === "Descartado" ? "desc" : c.etapa === "Finalizado" ? "fin" : "prod",
        tipo: ps.map((p) => p.tipo).find(Boolean) ?? c.tipo ?? null, publicado, criado: c.criado_em, finalizado: c.concluido_em,
        drive: c.drive_pasta_id ? "https://drive.google.com/drive/folders/" + c.drive_pasta_id : null,
        legenda: c.legenda || ps.map((p) => p.legenda).find(Boolean) || "",
      };
    });
    // Sem cadastro: o mesmo post em várias redes vira uma linha só.
    for (const g of agruparSoltos(dados.pubs.filter((p) => !p.conteudo_id))) {
      const por = Object.fromEntries(REDES.map((x) => [x.k, g.find((p) => p.rede === x.rede) ?? null]));
      const ps = pubsDe(por), base = ps[0];
      const primeira = (base.legenda ?? "").split("\n").find((l) => l.trim()) ?? "(sem legenda)";
      linhas.push({
        chave: "p" + Math.min(...g.map((p) => p.id)), pagina: base.pagina, id: null, pubIds: g.map((p) => p.id), titulo: primeira.slice(0, 90), resp: infoPagina(base.pagina).nome, ...por,
        sit: "sem", tipo: ps.map((p) => p.tipo).find(Boolean), publicado: g.map((p) => p.publicado_em).sort()[0],
        criado: g.map((p) => p.encontrado_em).sort()[0], finalizado: null, drive: null, legenda: base.legenda ?? "",
      });
    }
  }
  // Par = redes diferentes, publicados com até 30 min de diferença e legenda começando igual
  // (ignorando emojis, hashtags, acentos e pontuação).
  const inicioLegenda = (s) => (s ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/#\S+/g, "").replace(/[^a-z0-9]/g, "").slice(0, 20);
  function agruparSoltos(pubs) {
    const usados = new Set(), grupos = [];
    const dist = (a, b) => Math.abs(Date.parse(a.publicado_em) - Date.parse(b.publicado_em));
    for (const p of pubs) {
      if (usados.has(p.id)) continue;
      usados.add(p.id);
      const x = inicioLegenda(p.legenda);
      const grupo = [p];
      // No máximo um par de cada outra rede.
      for (const outra of REDES) {
        if (outra.rede === p.rede) continue;
        const par = pubs.filter((q) => {
          const y = inicioLegenda(q.legenda);
          return !usados.has(q.id) && q.rede === outra.rede && q.pagina === p.pagina && dist(p, q) <= 30 * 60_000 && x.length >= 8 && y.length >= 8 && (x.startsWith(y) || y.startsWith(x));
        }).sort((a, b) => dist(a, p) - dist(b, p))[0];
        if (par) { usados.add(par.id); grupo.push(par); }
      }
      grupos.push(grupo);
    }
    return grupos;
  }
  const curt = (r) => soma(...pubsDe(r).map((p) => p.curtidas));
  const views = (r) => soma(...pubsDe(r).map((p) => p.visualizacoes));
  const recente = (r) => Date.parse(r.publicado ?? r.criado);

  // ---------- resumo ----------
  // Cards do topo: os posts que mais tiveram views e as horas em que as views chegam.
  function kpis() {
    const ex = dados.execucao;
    const min = ex ? Math.round((Date.now() - Date.parse(ex.iniciado_em)) / 60000) : null;
    $("#coletor").textContent = ex ? `Coletor: última consulta há ${min} min · ${ex.processamento_ms ?? "?"} ms${ex.erro ? " · com erro" : ""}` : "Coletor: sem consultas ainda";
    $("#coletor").classList.toggle("alerta", !!ex?.erro || min > 15);
    const bot = (v, t) => `<button class="f" data-top="${v}" aria-pressed="${topPeriodo === v}">${t}</button>`;
    $("#kpis").innerHTML = `
      <div class="kpi tot">${totais()}</div>
      <div class="kpi"><span>Top 5 posts <i>visualizações</i></span><div class="seg">${bot("7", "7 dias")}${bot("tudo", "Desde sempre")}</div>${topPosts()}</div>
      <div class="kpi"><span>Quando as visualizações chegam <i>por dia da semana e hora</i></span>${mapaHoras()}</div>`;
  }
  // Totais desde sempre, com a divisão por rede; seguidores vêm da leitura diária das contas.
  function totais() {
    const pubs = dados.pubs, somaRede = (rede, c) => pubs.filter((p) => p.rede === rede).reduce((a, p) => a + (p[c] ?? 0), 0);
    const seg = (rede) => dados.seguidores.filter((s) => s.rede === rede);
    // Seguidores: soma da leitura mais recente de cada conta da rede (uma ou várias páginas).
    const atual = (rede) => {
      const vistos = new Set(), u = seg(rede).filter((s) => !vistos.has(s.pagina) && vistos.add(s.pagina));
      return u.length ? u.reduce((a, s) => a + (s.seguidores ?? 0), 0) : null;
    };
    const bloco = (rot, vals, extra = "") => {
      const t = soma(...vals), p = pcts(vals, t);
      return `<div class="tb"><span>${rot}</span><b>${k(t)}</b>${extra}
        <span class="bar" title="${dica(p)}">${ativas.map((x, i) => `<i class="${x.k}-b" style="width:${p[i]}%"></i>`).join("")}</span>
        <small>${ativas.map((x, i) => `<span class="${x.k}-t">${k(vals[i])}</span>`).join("")}</small></div>`;
    };
    const desde = ativas.flatMap((x) => seg(x.rede)).map((s) => s.data).sort()[0];
    const ganho = (l) => [...new Set(l.map((s) => s.pagina))].reduce((a, x) => {
      const q = l.filter((s) => s.pagina === x);
      return a + (q[0].seguidores ?? 0) - (q.at(-1).seguidores ?? 0);
    }, 0);
    const g = ativas.reduce((a, x) => a + ganho(seg(x.rede)), 0);
    return bloco("Visualizações <i>total</i>", ativas.map((x) => somaRede(x.rede, "visualizacoes")))
      + bloco("Curtidas <i>total</i>", ativas.map((x) => somaRede(x.rede, "curtidas")))
      + bloco("Seguidores", ativas.map((x) => atual(x.rede)), desde ? `<em class="gain">${g >= 0 ? "+" : ""}${n(g)} desde ${dt(desde + "T12:00:00Z", false)}</em>` : "");
  }
  function topPosts() {
    const L = linhas.filter((r) => r.publicado && views(r) && (topPeriodo === "tudo" || Date.now() - Date.parse(r.publicado) <= 7 * 24 * H))
      .sort((a, b) => views(b) - views(a)).slice(0, 5);
    if (!L.length) return '<p class="wait">Nenhum post publicado no período.</p>';
    return `<ol class="top5">${L.map((r) => {
      const vals = ativas.map((x) => r[x.k]?.visualizacoes ?? 0), t = vals.reduce((a, v) => a + v, 0), p = pcts(vals, t);
      const i = vals.indexOf(Math.max(...vals)), ganhou = `<span class="${ativas[i].k}-t">${ativas[i].nome}</span>`;
      return `<li><div class="t5"><b title="${esc(r.titulo)}">${esc(r.titulo)}</b><small>${dt(r.publicado, false)} · mais views no ${ganhou}</small></div>
        <div class="t5b"><span class="bar" title="${dica(p)}">${ativas.map((x, j) => `<i class="${x.k}-b" style="width:${p[j]}%"></i>`).join("")}</span><small>${ativas.map((x, j) => `<span class="${x.k}-t">${k(vals[j])}</span>`).join("")}</small></div>
        <b class="t5n">${k(t)}</b></li>`;
    }).join("")}</ol>`;
  }
  function mapaHoras() {
    const M = dados.mapa;
    if (!M.length) return '<p class="wait">Ainda sem leituras suficientes.</p>';
    const val = new Map(M.map((m) => [`${m.dia}-${m.hora}`, +m.visualizacoes]));
    const max = Math.max(1, ...val.values());
    const DIAS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];
    let g = `<span></span>${Array.from({ length: 24 }, (_, h) => `<small>${h % 3 ? "" : h + "h"}</small>`).join("")}`;
    DIAS.forEach((d, i) => {
      g += `<span>${d}</span>`;
      for (let h = 0; h < 24; h++) {
        const v = val.get(`${i + 1}-${h}`);
        g += v == null ? `<i class="vazio" title="${d} ${h}h: sem dados"></i>` : `<i style="--h:${(0.08 + 0.92 * (v / max) ** 0.6).toFixed(2)}" title="${d} ${h}h: ${n(v)} views"></i>`;
      }
    });
    return `<div class="mapa">${g}</div><small class="wait">Views ganhas em cada hora, somando todas as semanas (cor mais forte = mais views). No começo reflete muito o horário das postagens.</small>`;
  }

  // ---------- lista ----------
  function filtradas() {
    const q = busca.trim().toLowerCase();
    const val = (r, c) => c === "curt" ? (curt(r) ?? -1) : c === "views" ? (views(r) ?? -1) : c === "publicado" ? (r.publicado ?? "") : c === "chave" ? recente(r) : (r[c] ?? "");
    return linhas
      .filter((r) => periodo === "tudo" || Date.now() - recente(r) <= 30 * 24 * H)
      .filter((r) => (filtro === "todos" ? r.sit !== "desc" : r.sit === filtro))
      .filter((r) => !q || r.titulo.toLowerCase().includes(q) || (r.id && rotuloId(r).toLowerCase().includes(q)))
      .sort((a, b) => { const x = val(a, ordem.c), y = val(b, ordem.c); return (x > y ? 1 : x < y ? -1 : 0) * ordem.d; });
  }
  function th(c, label, cls = "") {
    const on = ordem.c === c, seta = on ? (ordem.d > 0 ? " ↑" : " ↓") : "";
    return `<th class="${cls}" ${on ? `aria-sort="${ordem.d > 0 ? "ascending" : "descending"}"` : ""}><button data-sort="${c}">${label}${seta}</button></th>`;
  }
  const SIT = { pub: "Publicado", fin: "Sem publicar", prod: "Em produção", desc: "Descartado", sem: "Sem cadastro" };
  const pill = (r) => `<span class="pill ${r.sit}">${SIT[r.sit]}</span>`;
  const heat = (v, max) => (v == null ? 0 : (0.06 + (v / max) * 0.32).toFixed(2));
  const rotuloId = (r) => (r.id ? infoPagina(r.pagina).prefixo + id4(r.id) : "—");
  // Total em destaque; embaixo, quanto veio de cada rede (com barrinha de proporção nas visualizações).
  function porRede(r, campo, fmt, barra) {
    const vals = ativas.map((x) => r[x.k]?.[campo] ?? null), t = soma(...vals);
    if (t == null) return "—";
    const subs = `<span class="sub">${ativas.map((x, i) => `<span class="${x.k}-t"><i class="d"></i>${vals[i] == null ? "—" : fmt(vals[i])}</span>`).join("")}</span>`;
    if (!barra || t === 0) return `<b class="tot">${fmt(t)}</b>${subs}`;
    // Barrinha com a participação de cada rede escrita dentro; embaixo, o número de cada rede na mesma ordem.
    const p = pcts(vals, t);
    const seg = (rede, pct) => (pct ? `<i class="${rede}-b" style="width:${pct}%">${pct >= 15 ? pct + "%" : ""}</i>` : "");
    return `<span class="cel"><b class="tot">${fmt(t)}</b><span class="bar" title="${dica(p)}">${ativas.map((x, i) => seg(x.k, p[i])).join("")}</span>${subs}</span>`;
  }
  const totRede = (L, campo, fmt) => {
    const vals = ativas.map((x) => L.reduce((a, r) => a + (r[x.k]?.[campo] ?? 0), 0));
    return `${fmt(vals.reduce((a, v) => a + v, 0))}<span class="sub">${ativas.map((x, i) => `<span class="${x.k}-t"><i class="d"></i>${fmt(vals[i])}</span>`).join("")}</span>`;
  };

  function render() {
    kpis();
    const L = filtradas();
    // O tom das duas colunas segue só as visualizações.
    const maxV = Math.max(1, ...linhas.map((r) => views(r) ?? 0));
    $("#cont").textContent = `${L.length} ${L.length === 1 ? "post" : "posts"}`;
    const cols = 10;
    const corpo = L.map((r) => {
      const ab = abertos.has(r.chave);
      return `<tr class="row" data-x="${r.chave}" tabindex="0" aria-expanded="${ab}"><td><span class="car">▸</span></td><td class="id">${rotuloId(r)}</td>
        <td class="t" title="${esc(r.titulo)}">${esc(r.titulo)}</td><td>${esc(r.tipo ?? "—")}</td><td>${pill(r)}</td><td class="pg">${esc(r.resp)}</td><td>${links(r)}</td>
        <td class="num rede">${porRede(r, "visualizacoes", k, true)}</td><td class="num rede">${porRede(r, "curtidas", k, false)}</td><td class="num">${dt(r.publicado)}</td></tr>`
        + (ab ? `<tr class="det-row"><td colspan="${cols}">${detalhe(r)}</td></tr>` : "");
    }).join("");
    const pl = L.filter((r) => curt(r) != null);
    const tabela = `<div class="scroll"><table><thead><tr><th style="width:28px"></th>${th("chave", "ID")}${th("titulo", "Título")}${th("tipo", "Formato")}${th("sit", "Situação")}${th("resp", "Página")}<th>Links</th>${th("views", "Visualizações", "num")}${th("curt", "Curtidas", "num")}${th("publicado", "Publicado")}</tr></thead>
      <tbody>${corpo || `<tr><td colspan="${cols}" class="wait">Nenhum post neste filtro.</td></tr>`}</tbody>
      <tfoot><tr><td colspan="7">${L.length} posts · ${pl.length} com números</td><td class="num rede">${totRede(pl, "visualizacoes", k)}</td><td class="num rede">${totRede(pl, "curtidas", n)}</td><td></td></tr></tfoot></table></div>`;
    const cel = `<div class="mlist">${L.map((r) => `<div class="mi"><button class="mi-h" data-x="${r.chave}" aria-expanded="${abertos.has(r.chave)}"><div class="r"><span>${rotuloId(r)} · ${esc(r.tipo ?? "—")} · ${esc(r.resp)}</span>${pill(r)}</div><b>${esc(r.titulo)}</b>
      ${curt(r) != null ? `<div class="nums"><span style="--h:${heat(views(r), maxV)}">▶ ${k(views(r))} ${ativas.map((x) => `<small class="${x.k}-t">${k(r[x.k]?.visualizacoes)}</small>`).join(" ")}</span><span style="--h:${heat(views(r), maxV)}">♥ ${k(curt(r))} ${ativas.map((x) => `<small class="${x.k}-t">${k(r[x.k]?.curtidas)}</small>`).join(" ")}</span></div>` : ""}</button>${links(r)}${abertos.has(r.chave) ? detalhe(r) : ""}</div>`).join("") || '<p class="wait">Nenhum post neste filtro.</p>'}</div>`;
    $("#view").innerHTML = tabela + cel;
  }

  // ---------- detalhe ----------
  function curva(r) {
    const pubs = pubsDe(r);
    if (pubs.some((p) => !series.has(p.id))) return '<p class="wait">Carregando a curva…</p>';
    const pontos = pubs.map((p) => ({ rede: p.rede, pts: series.get(p.id).filter((l) => l.visualizacoes != null).map((l) => ({ h: (Date.parse(l.lido_em) - Date.parse(p.publicado_em)) / H, v: l.visualizacoes })) }));
    const todos = pontos.flatMap((s) => s.pts);
    if (todos.length < 1) return '<p class="wait">Ainda sem leituras.</p>';
    // Eixo x uniforme dentro da janela escolhida; abre na menor janela que cobre a idade do post e as maiores ficam apagadas.
    const idade = Math.max(...pubs.map((p) => (Date.now() - Date.parse(p.publicado_em)) / H));
    const cabe = (JANELAS.find(([j]) => j >= idade) ?? JANELAS.at(-1))[0];
    const jan = zoom.get(r.chave) ?? cabe;
    const passoH = JANELAS.find(([j]) => j === jan)[1];
    const botoes = `<div class="zoom">${JANELAS.map(([j]) => `<button class="f" type="button" data-zoom="${j}" data-x-zoom="${r.chave}" aria-pressed="${j === jan}"${j > cabe ? " disabled" : ""}>${j}h</button>`).join("")}</div>`;
    for (const s of pontos) s.pts = s.pts.filter((p) => p.h <= jan + 1 / 60);
    const vis = pontos.flatMap((s) => s.pts);
    const W = 560, A = 192, E = 46, D = 14, B = 30, T = 28; // T: faixa de cima, onde fica o placar
    const maxV = Math.max(1, ...vis.map((p) => p.v));
    const marcas = Array.from({ length: Math.round(jan / passoH) + 1 }, (_, i) => i * passoH);
    const rot = (h) => (!h ? "" : h < 1 ? `${h * 60}m` : Number.isInteger(h) ? `${h}h` : `${Math.floor(h)}h${(h % 1) * 60}`);
    const x = (h) => E + (Math.max(0, h) / jan) * (W - E - D);
    const y = (v) => A - B - (v / maxV) * (A - B - T);
    let g = marcas.map((h) => `<line x1="${x(h)}" x2="${x(h)}" y1="${T - 6}" y2="${A - B}" stroke="var(--line)" stroke-opacity=".5"/>`).join("");
    g += [0, 0.5, 1].map((f) => `<line x1="${E}" x2="${W - D}" y1="${y(maxV * f)}" y2="${y(maxV * f)}" stroke="var(--line)"/><text x="${E - 6}" y="${y(maxV * f) + 4}" text-anchor="end" font-size="10" fill="var(--muted)">${k(Math.round(maxV * f))}</text>`).join("");
    g += marcas.map((h) => `<text x="${x(h)}" y="${A - 12}" text-anchor="middle" font-size="9" fill="var(--muted)">${rot(h)}</text>`).join("");
    // Placar em cima de cada marca (quando há duas redes): quantas vezes a rede da frente tinha a outra, com a leitura mais próxima daquela marca.
    const perto = (pts, h) => pts.filter((p) => Math.abs(p.h - h) <= passoH / 2).sort((p, q) => Math.abs(p.h - h) - Math.abs(q.h - h))[0];
    const cor = (rede) => `var(--${daRede(rede).k})`;
    const [s1, s2] = pontos;
    if (pontos.length === 2) g += marcas.map((h) => {
      const a = perto(s1.pts, h), b = perto(s2.pts, h);
      if (!h || !a || !b || !a.v || !b.v) return "";
      const m = Math.max(a.v, b.v) / Math.min(a.v, b.v), c = a.v === b.v ? "var(--muted)" : a.v > b.v ? cor(s1.rede) : cor(s2.rede);
      return `<text x="${x(h)}" y="${T - 12}" text-anchor="middle" font-size="9" font-weight="700" fill="${c}">${m.toLocaleString("pt-BR", { maximumFractionDigits: m < 10 ? 1 : 0 })}×</text>`;
    }).join("");
    for (const s of pontos) {
      if (!s.pts.length) continue;
      const cs = cor(s.rede);
      g += `<polyline fill="none" stroke="${cs}" stroke-width="1.25" stroke-linejoin="round" points="${s.pts.map((p) => `${x(p.h).toFixed(1)},${y(p.v).toFixed(1)}`).join(" ")}"/>`;
      const raio = s.pts.length > 40 ? 2 : 3.5; // com muitos pontos (janelas longas) a bolinha diminui para a linha aparecer
      g += s.pts.map((p) => `<circle cx="${x(p.h).toFixed(1)}" cy="${y(p.v).toFixed(1)}" r="${raio}" fill="${cs}" stroke="var(--surface)" stroke-width="${raio / 2.5}"><title>${dt(new Date(Date.parse(r.publicado ?? 0) + p.h * H).toISOString())}: ${n(p.v)}</title></circle>`).join("");
    }
    return `${botoes}<svg class="chart" viewBox="0 0 ${W} ${A}" role="img" aria-label="Visualizações ao longo do tempo">${g}</svg>
      <div class="legend">${pontos.map((s) => `<span><i style="background:${cor(s.rede)}"></i>${daRede(s.rede).nome}</span>`).join("")}<span>tempo desde a publicação</span></div>`;
  }

  function detalhe(r) {
    const datas = `<dl><dt>Criado</dt><dd>${dt(r.criado)}</dd><dt>Finalizado</dt><dd>${dt(r.finalizado)}</dd><dt>Publicado</dt><dd>${dt(r.publicado)}</dd><dt>Última coleta</dt><dd>${dt(pubsDe(r).map((p) => p.ultima_leitura_em).filter(Boolean).sort().at(-1))}</dd></dl>`;
    const leg = r.legenda ? `<h3>Legenda</h3><div class="cap">${esc(r.legenda)}</div>` : "";
    const vinc = r.sit === "sem" ? `<h3>Ligar a um post</h3><div class="vincular"><select id="vinc-${r.chave}" aria-label="Escolher o post">${dados.conteudos.filter((c) => c.pagina === r.pagina).map((c) => `<option value="${c.id}">${infoPagina(c.pagina).prefixo}${id4(c.id)} – ${esc(c.titulo)}</option>`).join("")}</select><button class="btn" type="button" data-vincular="${r.pubIds.join(",")}" data-x-vinc="${r.chave}">Ligar</button></div>` : "";
    if (!pubsDe(r).length) {
      return `<div class="det"><div>${datas}<p class="wait">${r.sit === "fin" ? `Pronto para publicar. Links e números aparecem quando o coletor encontrar [${rotuloId(r)}] na legenda publicada.` : r.sit === "desc" ? "Descartado: não vai ser publicado." : "Em produção. Os números aparecem depois da publicação."}</p></div><div>${leg}</div></div>`;
    }
    const linha = (rot, c) => `<tr><td>${rot}</td>${ativas.map((x) => `<td>${r[x.k] ? n(r[x.k][c]) : "—"}</td>`).join("")}</tr>`;
    const mt = `<table class="mt"><thead><tr><th></th>${ativas.map((x) => `<th class="num ${x.k}-t">${x.nome}</th>`).join("")}</tr></thead><tbody>
      ${linha("Visualizações", "visualizacoes")}${linha("Curtidas", "curtidas")}${linha("Comentários", "comentarios")}${linha("Compartilhamentos", "compartilhamentos")}${linha("Salvamentos", "salvamentos")}${linha("Alcance", "alcance")}</tbody></table>`;
    return `<div class="det"><div>${datas}<h3>Visualizações ao longo do tempo</h3>${curva(r)}</div>
      <div>${mt}${vinc}${leg}</div></div>`;
  }

  async function carregarSerie(r) {
    const faltam = pubsDe(r).filter((p) => !series.has(p.id)).map((p) => p.id);
    if (!faltam.length) return;
    const { data, error } = await db.from("leituras").select("publicacao_id,lido_em,visualizacoes").in("publicacao_id", faltam).eq("situacao", "valida").order("lido_em");
    if (error) return;
    for (const id of faltam) series.set(id, data.filter((l) => l.publicacao_id === id));
    render();
  }

  // ---------- eventos ----------
  document.querySelector(".tools").onclick = (e) => {
    const b = e.target.closest(".f"); if (!b) return;
    if (b.dataset.p) { periodo = b.dataset.p; document.querySelectorAll("[data-p]").forEach((x) => x.setAttribute("aria-pressed", String(x === b))); }
    else { filtro = b.dataset.f; document.querySelectorAll("[data-f]").forEach((x) => x.setAttribute("aria-pressed", String(x === b))); }
    render();
  };
  $("#kpis").onclick = (e) => {
    const b = e.target.closest("[data-top]"); if (!b) return;
    topPeriodo = b.dataset.top; kpis();
  };
  $("#busca").oninput = (e) => { busca = e.target.value; render(); };

  $("#view").addEventListener("click", async (e) => {
    const s = e.target.closest("[data-sort]");
    if (s) { const c = s.dataset.sort; ordem = { c, d: ordem.c === c ? -ordem.d : (["titulo", "resp", "tipo", "sit"].includes(c) ? 1 : -1) }; render(); return; }

    const v = e.target.closest("[data-vincular]");
    if (v) {
      const conteudo = Number(document.getElementById(`vinc-${v.dataset.xVinc}`).value);
      v.disabled = true;
      const res = await Promise.all(v.dataset.vincular.split(",").map((id) => db.rpc("vincular_publicacao", { p_publicacao: Number(id), p_conteudo: conteudo })));
      const erro = res.find((x) => x.error);
      if (erro) { v.disabled = false; alert(erro.error.message); return; }
      abertos.clear(); abertos.add(`${linhas.find((x) => x.chave === v.dataset.xVinc)?.pagina}:${conteudo}`);
      await carregar();
      return;
    }

    const z = e.target.closest("[data-zoom]");
    if (z) { zoom.set(z.dataset.xZoom, Number(z.dataset.zoom)); render(); return; }

    if (e.target.closest("a, .ico, .det, select")) return;
    const linha = e.target.closest("[data-x]");
    if (!linha) return;
    const chave = linha.dataset.x;
    if (abertos.has(chave)) abertos.delete(chave); else abertos.add(chave);
    render();
    const r = linhas.find((x) => x.chave === chave);
    if (abertos.has(chave) && r) carregarSerie(r);
  });
  $("#view").addEventListener("keydown", (e) => { if ((e.key === "Enter" || e.key === " ") && e.target.matches("tr.row")) { e.preventDefault(); e.target.click(); } });

  // Atualiza sozinho a cada 5 minutos enquanto a página estiver aberta.
  setInterval(() => { if (!$("#painel").hidden && document.visibilityState === "visible") carregar(); }, 5 * 60_000);

  iniciar();
})();
