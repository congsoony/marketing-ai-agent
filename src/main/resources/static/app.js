// ───────────────────────────────────────────────────────────
// 유튜브 댓글 에이전트 대시보드 — 프론트 로직
// ① 영상 불러오기 → ② 선택/초안 → ③ 게시(진행상태 폴링)
// ───────────────────────────────────────────────────────────
let CONFIG = null;
let selected = null;       // {videoId, title, channel, thumbnail}
let pollTimer = null;

const $ = (id) => document.getElementById(id);
const api = async (url, opts) => {
  const res = await fetch(url, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || ('요청 실패 (' + res.status + ')'));
  return data;
};

function toast(msg, isErr) {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'toast' + (isErr ? ' err' : '');
  setTimeout(() => t.classList.add('hidden'), 4200);
}

// ── 초기 설정 로드 ──────────────────────────────────────────
async function loadConfig() {
  CONFIG = await api('/api/config');
  const b = $('badges');
  b.innerHTML =
    `<span class="badge">브랜드 <b>${CONFIG.brand}</b></span>` +
    `<span class="badge">글자수 <b>${CONFIG.minChars}~${CONFIG.maxChars}</b></span>` +
    `<span class="badge">하루 상한 <b>${CONFIG.dailyMax}</b></span>` +
    `<span class="badge" id="profBadge">프로필 로딩…</span>`;

  // 프로필 로그인 상태 로드(드롭다운 + 뱃지)
  await loadProfiles();

  // 키 미설정 경고
  const warn = [];
  if (!CONFIG.youtubeKeyConfigured) warn.push('YOUTUBE_API_KEY');
  if (!CONFIG.openaiKeyConfigured) warn.push('OPENAI_API_KEY');
  if (warn.length) {
    const w = $('keyWarn');
    w.textContent = '⚠ 환경변수 미설정: ' + warn.join(', ') +
      ' — 설정 후 재시작해야 실제 호출이 됩니다.';
    w.classList.remove('hidden');
  }
}

// 프로필별 로그인 상태 → 드롭다운 라벨 + 뱃지 점(초록=로그인/회색=미로그인)
async function loadProfiles() {
  let profs;
  try { profs = await api('/api/profiles'); }
  catch (e) { return; }
  // 드롭다운(현재 선택값 유지)
  const sel = $('profileSel');
  const cur = sel.value;
  sel.innerHTML = '<option value="">자동 (로테이션)</option>' +
    profs.map(p => `<option value="${esc(p.name)}">${esc(p.name)} ${p.loggedIn ? '● 로그인됨' : '○ 로그인 필요'}</option>`).join('');
  sel.value = cur;
  // 뱃지: 프로필별 로그인 점 + 요약
  const badge = $('profBadge');
  if (badge) {
    const n = profs.filter(p => p.loggedIn).length;
    badge.innerHTML = '프로필 ' +
      profs.map(p => `<span class="pdot ${p.loggedIn ? 'on' : 'off'}" title="${esc(p.name)} ${p.loggedIn ? '로그인됨' : '미로그인'}">${esc(p.name)}</span>`).join('') +
      ` <b>(${n}/${profs.length})</b>`;
  }
}

// ── ① 영상 불러오기 ────────────────────────────────────────
async function loadVideos(trending) {
  const list = $('videoList');
  list.innerHTML = '<p class="empty"><span class="spinner"></span> 불러오는 중…</p>';
  try {
    const url = trending ? '/api/trending'
      : '/api/videos?query=' + encodeURIComponent($('query').value || '');
    const videos = await api(url);
    if (!videos.length) { list.innerHTML = '<p class="empty">결과가 없습니다.</p>'; return; }
    list.innerHTML = '';
    videos.forEach((v, i) => list.appendChild(videoItem(v, i, trending)));
  } catch (e) {
    list.innerHTML = '<p class="empty">불러오기 실패: ' + e.message + '</p>';
    toast(e.message, true);
  }
}

function videoItem(v, i, trending) {
  const el = document.createElement('div');
  el.className = 'vitem';
  el.dataset.id = v.videoId;
  el.style.setProperty('--i', i || 0);   // 순차 등장(stagger) 딜레이
  const date = (v.publishedAt || '').slice(0, 10);
  // 뜨기 직전 탐색: 🔥 조회 N · 시간당 X / 일반 불러오기: 조회 N 만
  let stat = '';
  if (v.viewCount != null) {
    stat = trending
      ? `<span class="vstat">🔥 조회 ${v.viewCount.toLocaleString()} · 시간당 ${v.viewsPerHour}</span>`
      : `<span class="vstat">조회 ${v.viewCount.toLocaleString()}</span>`;
  }
  el.innerHTML =
    `<img src="${v.thumbnailUrl}" alt=""/>` +
    `<div class="vmeta"><span class="vtitle">${esc(v.title)}</span>` +
    `<span class="vsub">${esc(v.channelTitle)} · ${date}</span>${stat}</div>`;
  el.onclick = () => selectVideo(v, el);
  return el;
}

// ── ② 영상 선택 → 고민 한 줄 + 댓글 초안 ────────────────────
async function selectVideo(v, el) {
  document.querySelectorAll('.vitem.active').forEach(x => x.classList.remove('active'));
  if (el) el.classList.add('active');
  selected = { videoId: v.videoId, title: v.title, channel: v.channelTitle, thumbnail: v.thumbnailUrl };

  const sel = $('selected');
  sel.classList.remove('empty-state');
  sel.innerHTML =
    `<div class="vitem"><img src="${v.thumbnailUrl || ''}" alt=""/>` +
    `<div class="vmeta"><span class="vtitle">${esc(v.title || v.videoId)}</span>` +
    `<span class="vsub">${esc(v.channelTitle || '')}</span></div></div>`;

  await generateDraft();
}

async function generateDraft() {
  if (!selected) return;
  const area = $('draftArea');
  area.classList.remove('hidden');
  $('concern').innerHTML = '<span class="spinner"></span> AI가 영상 맥락을 읽는 중…';
  $('comment').value = '';
  $('charCount').textContent = '';
  $('btnPost').disabled = true;

  try {
    const d = await api('/api/draft?videoId=' + encodeURIComponent(selected.videoId));
    $('concern').textContent = d.concernLine || '(고민 요약 없음)';
    $('comment').value = d.comment || '';
    updateCharCount();
    $('btnPost').disabled = false;
  } catch (e) {
    $('concern').textContent = '초안 생성 실패: ' + e.message;
    toast(e.message, true);
  }
}

function updateCharCount() {
  const len = [...$('comment').value.trim()].length;
  const c = $('charCount');
  const ok = CONFIG && len >= CONFIG.minChars && len <= CONFIG.maxChars;
  c.textContent = `${len}자` + (CONFIG ? ` / ${CONFIG.minChars}~${CONFIG.maxChars}` : '');
  c.className = 'char ' + (ok ? 'good' : 'bad');
}

// 링크 직접 입력(가점)
async function selectByLink() {
  const link = $('linkInput').value.trim();
  if (!link) return;
  const sel = $('selected');
  sel.classList.remove('empty-state');
  sel.innerHTML = '<p class="empty"><span class="spinner"></span> 링크 영상 분석 중…</p>';
  $('draftArea').classList.add('hidden');
  try {
    const d = await api('/api/draft?url=' + encodeURIComponent(link));
    selected = { videoId: d.videoId, title: '(링크 입력 영상)', channel: '', thumbnail: '' };
    sel.innerHTML = `<div class="vitem" style="padding:10px">영상 ID <b>${d.videoId}</b></div>`;
    $('draftArea').classList.remove('hidden');
    $('concern').textContent = d.concernLine || '';
    $('comment').value = d.comment || '';
    updateCharCount();
    $('btnPost').disabled = false;
  } catch (e) {
    sel.innerHTML = '<p class="empty">' + e.message + '</p>';
    toast(e.message, true);
  }
}

// ── ③ 댓글 달기 + 진행상태 폴링 ────────────────────────────
async function post() {
  if (!selected) return;
  const body = {
    videoId: selected.videoId,
    videoTitle: selected.title,
    channelTitle: selected.channel,
    comment: $('comment').value.trim(),
    profileName: $('profileSel').value || null
  };
  $('btnPost').disabled = true;
  $('failReason').classList.add('hidden');
  $('shotLink').classList.add('hidden');
  $('progress').classList.remove('hidden');
  setSteps('OPENING');
  $('progressNote').innerHTML = '<span class="spinner"></span> 작업 시작…';

  try {
    const s = await api('/api/post', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    pollStatus(s.id);
  } catch (e) {
    $('progressNote').textContent = '';
    showFail(e.message);
    $('btnPost').disabled = false;
    toast(e.message, true);
  }
}

function pollStatus(id) {
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    try {
      const s = await api('/api/post/' + id);
      setSteps(s.status);
      $('progressNote').textContent = s.note || '';
      if (s.screenshotUrl) {
        $('shot').src = s.screenshotUrl;
        $('shotLink').href = s.screenshotUrl;
        $('shotLink').classList.remove('hidden');
      }
      if (s.status === 'SUCCESS' || s.status === 'FAILED') {
        clearInterval(pollTimer);
        if (s.status === 'FAILED') showFail(s.failReason || s.note);
        else toast('게시 완료 · 내 댓글 확인됨 ✓');
        $('btnPost').disabled = false;
        loadLogs();
      }
    } catch (e) {
      clearInterval(pollTimer);
      showFail(e.message);
      $('btnPost').disabled = false;
    }
  }, 1500);
}

function setSteps(status) {
  const order = ['OPENING', 'TYPING', 'SUCCESS'];
  const idx = order.indexOf(status);
  document.querySelectorAll('.pstep').forEach(el => {
    const s = el.dataset.s;
    el.className = 'pstep';
    if (status === 'FAILED') { if (order.indexOf(s) >= 0) {} }
    const si = order.indexOf(s);
    if (status === 'SUCCESS') el.classList.add('done');
    else if (si < idx) el.classList.add('done');
    else if (si === idx) el.classList.add('active');
  });
  if (status === 'FAILED') {
    document.querySelectorAll('.pstep').forEach(el => {
      if (!el.classList.contains('done')) el.classList.add('failed');
    });
  }
}

function showFail(msg) {
  const f = $('failReason');
  f.textContent = '실패: ' + (msg || '알 수 없는 오류');
  f.classList.remove('hidden');
}

// ── 기록 ────────────────────────────────────────────────────
async function loadLogs() {
  try {
    const logs = await api('/api/logs');
    const body = $('logBody');
    if (!logs.length) { body.innerHTML = '<tr><td colspan="6" class="empty">아직 기록이 없습니다.</td></tr>'; return; }
    body.innerHTML = logs.map(l => {
      const time = new Date(l.createdAt).toLocaleString('ko-KR', { hour12: false });
      const shot = l.screenshotUrl ? `<a href="${l.screenshotUrl}" target="_blank">보기</a>` : '-';
      return `<tr>
        <td>${time}</td>
        <td>${esc(l.videoTitle || l.videoId)}</td>
        <td>${esc(l.profileName || '')}</td>
        <td class="comment" title="${esc(l.comment || '')}">${esc(l.comment || '')}</td>
        <td><span class="tag ${l.status}"${l.failReason ? ` title="${esc(l.failReason)}"` : ''}>${l.status}</span>${l.status === 'FAILED' && l.failReason ? `<div class="failmini" title="${esc(l.failReason)}">${esc(l.failReason)}</div>` : ''}</td>
        <td>${shot}</td></tr>`;
    }).join('');
  } catch (e) { /* 조용히 무시 */ }
}

function esc(s) {
  return (s || '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

// ── 이벤트 바인딩 ───────────────────────────────────────────
$('btnLoad').onclick = () => loadVideos(false);
$('btnTrending').onclick = () => loadVideos(true);
$('btnLink').onclick = selectByLink;
$('btnRegen').onclick = generateDraft;
$('btnPost').onclick = post;
$('btnRefreshLogs').onclick = () => { loadLogs(); loadProfiles(); };
$('comment').addEventListener('input', updateCharCount);

loadConfig().then(loadLogs);
