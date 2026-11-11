'use strict';

const state = {
  courts: [],
  matches: [],
  clips: [],
  selected: new Set(),
  currentCourt: null,
  currentMatch: null,
  courtQuery: {},
  location: null,
  teamFilter: 'all',
};

const $ = (id) => document.getElementById(id);
const content = $('content');
const breadcrumb = $('breadcrumb');
const downloadBar = $('download-bar');
const downloadInfo = $('download-info');
const downloadBtn = $('download-btn');

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}

function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => t.classList.remove('show'), 2600);
}

async function getJson(url) {
  const resp = await fetch(url);
  if (!resp.ok) {
    let msg = 'HTTP ' + resp.status;
    try { const e = await resp.json(); msg = e.message || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  return resp.json();
}

function setBreadcrumb(items) {
  breadcrumb.innerHTML = items.map((it, i) =>
    i === items.length - 1
      ? `<span class="crumb current">${esc(it.label)}</span>`
      : `<a class="crumb" data-action="${it.action}">${esc(it.label)}</a>`
  ).join(' › ');
}

// ---- 数据加载 ----

async function loadCities() {
  try {
    const cities = await getJson('/api/cities');
    setBreadcrumb([{ label: '选择城市' }]);
    content.innerHTML = cities.map((c) =>
      `<div class="card city" data-action="city" data-id="${c.cityid}"><span>${esc(c.cityname)}</span></div>`
    ).join('');
  } catch (e) {
    toast('加载城市失败：' + e.message);
  }
}

async function loadCourts(params) {
  state.courtQuery = params || {};
  const qs = new URLSearchParams();
  Object.entries(state.courtQuery).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, v);
  });
  try {
    const page = await getJson('/api/courts?' + qs.toString());
    renderCourts(page.rows || []);
  } catch (e) {
    toast('加载场馆失败：' + e.message);
  }
}

async function loadMatches(court) {
  state.currentCourt = court;
  try {
    const page = await getJson(`/api/courts/${court.courtid}/matches?size=100`);
    renderMatches(page.rows || []);
  } catch (e) {
    toast('加载比赛失败：' + e.message);
  }
}

async function loadClips(match) {
  state.currentMatch = match;
  content.innerHTML = '<p class="empty">加载片段中…</p>';
  try {
    const clips = await getJson(`/api/matches/${match.actid}/clips`);
    renderClips(clips);
    updateDownloadBar();
  } catch (e) {
    toast('加载片段失败：' + e.message);
  }
}

// ---- 渲染 ----

function renderCourts(courts) {
  state.courts = courts;
  setBreadcrumb([{ label: '场馆' }]);
  if (!courts.length) {
    content.innerHTML = '<p class="empty">未找到场馆</p>';
    return;
  }
  content.innerHTML = courts.map((c) => `
    <div class="card court" data-action="court" data-id="${c.courtid}">
      ${c.banner ? `<img class="court-banner" src="${esc(c.banner)}" loading="lazy" alt="">` : ''}
      <div class="court-info">
        <div class="court-name">${esc(c.courtname)}</div>
        <div class="court-addr">${esc(c.geoaddr || '')}</div>
        ${c.juli ? `<div class="court-dist">距你 ${formatDist(c.juli)}</div>` : ''}
      </div>
    </div>`).join('');
}

function renderMatches(matches) {
  state.matches = matches;
  setBreadcrumb([
    { label: '场馆', action: 'courts' },
    { label: state.currentCourt.courtname },
  ]);
  if (!matches.length) {
    content.innerHTML = '<p class="empty">该场馆暂无比赛</p>';
    return;
  }
  content.innerHTML = matches.map((m) => `
    <div class="card match" data-action="match" data-id="${m.actid}">
      <div class="match-teams">${esc(m.team1)} <b>vs</b> ${esc(m.team2)}</div>
      <div class="match-score">${m.score1 ?? '-'} : ${m.score2 ?? '-'}</div>
      <div class="match-date">${esc(m.actdate)} ${esc(m.stime || '')}</div>
    </div>`).join('');
}

function renderClips(clips) {
  state.clips = clips;
  state.selected = new Set();
  state.teamFilter = 'all';
  const m = state.currentMatch;
  setBreadcrumb([
    { label: '场馆', action: 'courts' },
    { label: state.currentCourt.courtname, action: 'matches' },
    { label: `${m.team1} vs ${m.team2}` },
  ]);
  content.innerHTML = `
    <div class="clips-header">
      <div class="match-title">${esc(m.team1)} ${m.score1 ?? '-'} : ${m.score2 ?? '-'} ${esc(m.team2)} · ${esc(m.actdate)}</div>
      <div class="clips-toolbar">
        <button id="select-all">全选</button>
        <div class="team-filter">
          <button data-team="all" class="active">全部</button>
          <button data-team="1">${esc(m.team1)}</button>
          <button data-team="2">${esc(m.team2)}</button>
        </div>
        <span class="clip-count">共 ${clips.length} 个片段</span>
      </div>
    </div>
    <div id="clip-list" class="clip-list"></div>`;
  bindClipsToolbar();
  renderClipList();
}

function renderClipList() {
  const m = state.currentMatch;
  const list = $('clip-list');
  const filtered = state.clips
    .map((c, i) => ({ c, i }))
    .filter(({ c }) => {
      if (state.teamFilter === '1') return c.teamid === m.teamid1;
      if (state.teamFilter === '2') return c.teamid === m.teamid2;
      return true;
    });
  list.innerHTML = filtered.map(({ c, i }) => {
    const checked = state.selected.has(i) ? 'checked' : '';
    const selectedCls = state.selected.has(i) ? 'selected' : '';
    const team = c.teamid === m.teamid1 ? m.team1 : (c.teamid === m.teamid2 ? m.team2 : '');
    return `
      <div class="clip ${selectedCls}" data-index="${i}">
        <input type="checkbox" class="clip-check" ${checked} data-index="${i}">
        <div class="clip-body" data-preview="${i}">
          ${c.poster ? `<img class="clip-poster" src="${esc(c.poster)}" loading="lazy" alt="">` : ''}
          <div class="clip-meta">
            <div class="clip-line1">
              ${team ? `<span class="clip-team">${esc(team)}</span>` : ''}
              ${c.jersey != null ? `<span class="clip-jersey">${esc(c.jersey)}号</span>` : ''}
              <span class="clip-jieguo">${esc(c.jieguo || '')}</span>
              <span class="clip-cam">${esc(c.camname || '')}</span>
              ${c.paid ? '<span class="clip-paid">付费</span>' : ''}
            </div>
            <div class="clip-line2">${esc(c.keytime || '')}</div>
          </div>
        </div>
        <div class="clip-preview hidden"><video controls preload="none"></video></div>
      </div>`;
  }).join('');
}

function formatDist(juli) {
  const meters = Number(juli);
  if (!Number.isFinite(meters)) return '';
  return meters >= 1000 ? (meters / 1000).toFixed(1) + ' km' : Math.round(meters) + ' m';
}

// ---- 交互 ----

function bindClipsToolbar() {
  $('select-all').addEventListener('click', () => {
    const m = state.currentMatch;
    const visible = state.clips
      .map((c, i) => ({ c, i }))
      .filter(({ c }) => {
        if (state.teamFilter === '1') return c.teamid === m.teamid1;
        if (state.teamFilter === '2') return c.teamid === m.teamid2;
        return true;
      });
    const allSelected = visible.every(({ i }) => state.selected.has(i));
    visible.forEach(({ i }) => {
      if (allSelected) state.selected.delete(i);
      else state.selected.add(i);
    });
    renderClipList();
    updateDownloadBar();
  });

  document.querySelectorAll('.team-filter button').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.teamFilter = btn.dataset.team;
      document.querySelectorAll('.team-filter button').forEach((b) => b.classList.toggle('active', b === btn));
      renderClipList();
      updateDownloadBar();
    });
  });
}

function togglePreview(clipEl, idx) {
  const preview = clipEl.querySelector('.clip-preview');
  const video = preview.querySelector('video');
  if (preview.classList.contains('hidden')) {
    if (!video.getAttribute('src')) video.setAttribute('src', state.clips[idx].path);
    preview.classList.remove('hidden');
    video.play();
  } else {
    video.pause();
    preview.classList.add('hidden');
  }
}

function updateDownloadBar() {
  const n = state.selected.size;
  downloadInfo.textContent = `已选 ${n} 个片段`;
  downloadBar.classList.toggle('hidden', n === 0);
  downloadBtn.textContent = n > 0 ? `下载已选 (${n})` : '下载已选';
}

// ---- 下载 ----

async function download() {
  const selected = [...state.selected].sort((a, b) => a - b);
  if (!selected.length) {
    toast('请先勾选片段');
    return;
  }
  const clips = selected.map((i) => {
    const c = state.clips[i];
    return { path: c.path, jieguo: c.jieguo, jersey: c.jersey, camname: c.camname, keytime: c.keytime };
  });
  const m = state.currentMatch;
  const filename = `${m.team1}vs${m.team2}_${m.actdate}.zip`;
  const jobId = crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random();

  downloadBtn.disabled = true;
  const timer = setInterval(() => pollProgress(jobId), 500);
  try {
    const resp = await fetch('/api/download', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ jobId, filename, clips }),
    });
    if (!resp.ok) {
      clearInterval(timer);
      let msg = 'HTTP ' + resp.status;
      try { const e = await resp.json(); msg = e.message || msg; } catch { /* ignore */ }
      throw new Error(msg);
    }
    const reader = resp.body.getReader();
    const chunks = [];
    let received = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      received += value.length;
      downloadInfo.textContent = `打包中 · 已写 ${(received / 1048576).toFixed(1)} MB`;
    }
    clearInterval(timer);
    const blob = new Blob(chunks, { type: 'application/zip' });
    triggerDownload(blob, filename);
    toast('下载完成');
  } catch (e) {
    clearInterval(timer);
    toast('下载失败：' + e.message);
  } finally {
    downloadBtn.disabled = false;
    updateDownloadBar();
  }
}

async function pollProgress(jobId) {
  try {
    const resp = await fetch(`/api/download/${jobId}/progress`);
    if (resp.ok) {
      const p = await resp.json();
      const phaseLabel = p.phase === 'zip' ? '打包中' : '下载中';
      downloadInfo.textContent = `${phaseLabel} · ${p.downloaded}/${p.total} 片段 · ${(p.bytes / 1048576).toFixed(1)} MB`;
    }
    // 404 = 任务尚未就绪，继续轮询
  } catch { /* ignore */ }
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

// ---- 事件委托与入口 ----

content.addEventListener('click', (e) => {
  const card = e.target.closest('[data-action]');
  if (card) {
    const id = card.dataset.id;
    if (card.dataset.action === 'city') {
      loadCourts({ cityid: id });
    } else if (card.dataset.action === 'court') {
      const court = state.courts.find((c) => String(c.courtid) === id);
      if (court) loadMatches(court);
    } else if (card.dataset.action === 'match') {
      const match = state.matches.find((m) => String(m.actid) === id);
      if (match) loadClips(match);
    }
    return;
  }
  const check = e.target.closest('.clip-check');
  if (check) {
    const idx = Number(check.dataset.index);
    if (check.checked) state.selected.add(idx);
    else state.selected.delete(idx);
    check.closest('.clip').classList.toggle('selected', check.checked);
    updateDownloadBar();
    return;
  }
  const body = e.target.closest('.clip-body');
  if (body) {
    togglePreview(body.closest('.clip'), Number(body.dataset.preview));
  }
});

breadcrumb.addEventListener('click', (e) => {
  const a = e.target.closest('a[data-action]');
  if (!a) return;
  if (a.dataset.action === 'courts') loadCourts(state.courtQuery || {});
  else if (a.dataset.action === 'matches') loadMatches(state.currentCourt);
});

function init() {
  $('search-btn').addEventListener('click', () => {
    const kw = $('court-search').value.trim();
    const params = { keyword: kw || undefined };
    if (state.location) {
      params.lat = state.location.lat;
      params.lon = state.location.lon;
    }
    loadCourts(params);
  });
  $('court-search').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') $('search-btn').click();
  });
  downloadBtn.addEventListener('click', download);

  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        state.location = { lat: pos.coords.latitude, lon: pos.coords.longitude };
        loadCourts({ lat: state.location.lat, lon: state.location.lon });
      },
      () => loadCities()
    );
  } else {
    loadCities();
  }
}

document.addEventListener('DOMContentLoaded', init);
