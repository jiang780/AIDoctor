/* ===========================================================
   AIDoctor · AI 智能诊疗助手 —— 前端主逻辑
   =========================================================== */

const USER_KEY = 'aiDoctorUser';

/* ---------------------- 基础请求 / 状态 ---------------------- */
async function apiGet(url) {
  const res = await fetch(memberQuery(url), { headers: { Accept: 'application/json' } });
  const text = await res.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (e) { throw new Error('数据解析失败'); }
  if (!res.ok) throw new Error(data.message || '请求失败');
  return data;
}

async function apiPost(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(withMemberBody(url, body)),
  });
  const text = await res.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch (e) { throw new Error('数据解析失败'); }
  if (!res.ok) throw new Error(data.message || '请求失败');
  return data;
}

/* ---------------------- 家庭成员 ---------------------- */
const MEMBER_KEY = 'aiDoctorMemberId';
const MEMBER_SCOPED = ['/api/cabinet', '/api/plans', '/api/lab-report', '/api/safety', '/api/profile'];
let MEMBERS = [];
let CURRENT_MEMBER = 0;

function memberQuery(url) {
  if (!CURRENT_MEMBER) return url;
  if (!MEMBER_SCOPED.some((prefix) => url.indexOf(prefix) === 0)) return url;
  if (/[?&]memberId=/.test(url)) return url;
  return url + (url.indexOf('?') >= 0 ? '&' : '?') + 'memberId=' + CURRENT_MEMBER;
}

function withMemberBody(url, body) {
  const payload = Object.assign({}, body || {});
  if (!CURRENT_MEMBER) return payload;
  if (!MEMBER_SCOPED.some((prefix) => url.indexOf(prefix) === 0)) return payload;
  if (payload.memberId == null) payload.memberId = CURRENT_MEMBER;
  return payload;
}

function currentMember() {
  for (const m of MEMBERS) if (m.id === CURRENT_MEMBER) return m;
  return MEMBERS.length ? MEMBERS[0] : null;
}

function memberAgeText(m) {
  if (!m || m.ageMonths == null || m.ageMonths < 0) return '';
  if (m.ageMonths < 24) return m.ageMonths + ' 个月';
  return m.age + ' 岁';
}

async function loadMembers() {
  const user = getUser();
  if (!user || !user.username) return;
  try {
    const data = await apiGet('/api/members?username=' + encodeURIComponent(user.username));
    MEMBERS = data.members || [];
    const saved = Number(localStorage.getItem(MEMBER_KEY) || 0);
    const hit = MEMBERS.some((m) => m.id === saved);
    CURRENT_MEMBER = hit ? saved : (data.currentId || (MEMBERS[0] ? MEMBERS[0].id : 0));
    localStorage.setItem(MEMBER_KEY, String(CURRENT_MEMBER || ''));
    renderMemberSwitch();
  } catch (e) {
    MEMBERS = [];
  }
}

function renderMemberSwitch() {
  const menu = document.getElementById('msMenu');
  if (!menu) return;
  const cur = currentMember();
  const nameEl = document.getElementById('msName');
  const avatarEl = document.getElementById('msAvatar');
  if (nameEl) nameEl.textContent = cur ? cur.name : '未设置';
  if (avatarEl) avatarEl.textContent = cur ? String(cur.name).slice(0, 1) : '＋';
  menu.innerHTML = MEMBERS.map((m) => `
    <button class="ms-item${m.id === CURRENT_MEMBER ? ' active' : ''}" type="button" data-member="${m.id}">
      <span class="ms-avatar">${esc(String(m.name).slice(0, 1))}</span>
      <span class="ms-text"><b>${esc(m.name)}</b><em>${esc(m.relation)}${memberAgeText(m) ? ' · ' + memberAgeText(m) : ''}</em></span>
      ${m.isDefault ? '<i class="ms-def">默认</i>' : ''}
    </button>`).join('') + `
    <a class="ms-manage" href="profile.html">管理家庭成员 →</a>`;
  menu.querySelectorAll('[data-member]').forEach((btn) => {
    btn.addEventListener('click', () => switchMember(Number(btn.dataset.member)));
  });
}

function switchMember(id) {
  const ms = document.getElementById('memberSwitch');
  if (ms) ms.classList.remove('open');
  if (!id || id === CURRENT_MEMBER) return;
  CURRENT_MEMBER = id;
  localStorage.setItem(MEMBER_KEY, String(id));
  window.location.reload();
}

function getUser() {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch (e) { return null; }
}

function displayName() {
  const user = getUser();
  if (!user) return '访客';
  if (state.profile && state.profile.name) return state.profile.name;
  return user.username === 'admin' ? '系统管理员' : user.username;
}

function isAdmin() {
  const user = getUser();
  return !!user && user.username === 'admin';
}

function esc(str) {
  return String(str == null ? '' : str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function nowText() {
  return '现在';
}

function logout() {
  localStorage.removeItem(USER_KEY);
  window.location.href = 'login.html';
}

const state = { profile: {}, diseases: [], medicines: [] };

/* ---------------------- SVG 图标 / 头像 ---------------------- */
const SPRITE = `
<svg xmlns="http://www.w3.org/2000/svg" style="display:none">
  <symbol id="i-bell" viewBox="0 0 24 24"><path d="M18 15.2V9.8a6 6 0 1 0-12 0v5.4L4.4 18h15.2L18 15.2Z"/><path d="M9.6 20.6a2.6 2.6 0 0 0 4.8 0"/></symbol>
  <symbol id="i-user" viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.6"/><path d="M4.8 20c0-3.6 3.2-5.6 7.2-5.6s7.2 2 7.2 5.6"/></symbol>
  <symbol id="i-chat" viewBox="0 0 24 24"><path d="M5.2 5.4h13.6A1.6 1.6 0 0 1 20.4 7v8.2a1.6 1.6 0 0 1-1.6 1.6h-9.4L5.4 20.4v-3.6h-.2A1.6 1.6 0 0 1 3.6 15.2V7a1.6 1.6 0 0 1 1.6-1.6Z"/><path d="M7.6 9.6h8.8M7.6 13h6"/></symbol>
  <symbol id="i-search" viewBox="0 0 24 24"><circle cx="10.8" cy="10.8" r="6.6"/><path d="m15.8 15.8 4.2 4.2"/></symbol>
  <symbol id="i-virus" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.4"/><path d="M12 3.2v2.6M12 18.2v2.6M3.2 12h2.6M18.2 12h2.6M5.8 5.8l1.8 1.8M16.4 16.4l1.8 1.8M18.2 5.8l-1.8 1.8M7.6 16.4l-1.8 1.8"/><circle cx="12" cy="12" r="1.1" fill="currentColor" stroke="none"/></symbol>
  <symbol id="i-box" viewBox="0 0 24 24"><path d="M4.4 8.2 12 4.4l7.6 3.8v7.6L12 19.6 4.4 15.8V8.2Z"/><path d="M4.4 8.2 12 12l7.6-3.8M12 12v7.6"/></symbol>
  <symbol id="i-feedback" viewBox="0 0 24 24"><path d="M5 4.6h14A1.6 1.6 0 0 1 20.6 6.2v8.4a1.6 1.6 0 0 1-1.6 1.6H9.8L5.6 19.8v-3.6H5a1.6 1.6 0 0 1-1.6-1.6V6.2A1.6 1.6 0 0 1 5 4.6Z"/><path d="M12 8v3.4M12 13.8v.2"/></symbol>
  <symbol id="i-logout" viewBox="0 0 24 24"><path d="M13 4.4H5.8A1.6 1.6 0 0 0 4.2 6v12a1.6 1.6 0 0 0 1.6 1.6H13"/><path d="M9.6 12h10.2M16.8 8.8 20 12l-3.2 3.2"/></symbol>
  <symbol id="i-phone" viewBox="0 0 24 24"><path d="M7.4 4.2h2l1.4 3.4-1.6 1.2a10.4 10.4 0 0 0 4.8 4.8l1.2-1.6 3.4 1.4v2a1.8 1.8 0 0 1-2 1.8C10.8 16 8 13.2 6.6 6.2a1.8 1.8 0 0 1 .8-2Z"/></symbol>
  <symbol id="i-mail" viewBox="0 0 24 24"><rect x="2.6" y="6.2" width="18.8" height="11.6" rx="1.4"/><path d="m3.4 7.4 8.6 5.6 8.6-5.6"/></symbol>
  <symbol id="i-eye" viewBox="0 0 24 24"><path d="M12 5.6c4.7 0 8.4 3.8 9.2 6.4-.8 2.6-4.5 6.4-9.2 6.4S3.6 14.6 2.8 12C3.6 9.4 7.3 5.6 12 5.6Z"/><circle cx="12" cy="12" r="2.8"/></symbol>
  <symbol id="i-chevron" viewBox="0 0 24 24"><path d="m7.4 10 4.6 4.6L16.6 10"/></symbol>
  <symbol id="i-shield" viewBox="0 0 24 24"><path d="M12 3.4 19 6v5.6c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6l7-2.6Z"/><path d="m9 12 2.2 2.2 4-4.2"/></symbol>
  <symbol id="i-stethoscope" viewBox="0 0 24 24"><path d="M6.2 4.2v5.6a4.4 4.4 0 0 0 8.8 0V4.2"/><path d="M4.6 4.2h3.2M13.4 4.2h3.2"/><path d="M10.6 14.2v2.2a4 4 0 0 0 4 4 3.4 3.4 0 0 0 3.4-3.4v-1.6"/><circle cx="18" cy="13.6" r="1.8"/></symbol>
  <symbol id="i-pill" viewBox="0 0 24 24"><path d="M9.4 4.2 19.8 14.6a3.9 3.9 0 0 1-5.5 5.5L3.9 9.7a3.9 3.9 0 0 1 5.5-5.5Z"/><path d="m7.4 7.3 6.9 6.9"/></symbol>
  <symbol id="i-book" viewBox="0 0 24 24"><path d="M12 7.6C10.2 6 8 5.2 4.6 5.2v12.4c3.4 0 5.6.7 7.4 2.2 1.8-1.5 4-2.2 7.4-2.2V5.2c-3.4 0-5.6.8-7.4 2.4Z"/><path d="M12 7.6v12.2"/></symbol>
  <symbol id="i-robot" viewBox="0 0 24 24"><rect x="4" y="8.2" width="16" height="11.4" rx="2.6"/><path d="M12 4.4v3.8M10 13.2v1.4M14 13.2v1.4M9.8 16.8h4.4"/><circle cx="12" cy="3.4" r="1.3"/><path d="M2.2 12.4v3.4M21.8 12.4v3.4"/></symbol>
  <symbol id="i-camera" viewBox="0 0 24 24"><path d="M4 8.4h3l1.4-2h7.2l1.4 2h3a1.2 1.2 0 0 1 1.2 1.2v8.2a1.2 1.2 0 0 1-1.2 1.2H4a1.2 1.2 0 0 1-1.2-1.2V9.6A1.2 1.2 0 0 1 4 8.4Z"/><circle cx="12" cy="13.8" r="3.2"/></symbol>
  <symbol id="i-clipboard" viewBox="0 0 24 24"><rect x="5" y="5.4" width="14" height="14.8" rx="2"/><path d="M9 3.6h6v3.4H9z"/><path d="M8.8 11.4h6.4M8.8 15h4.4"/></symbol>
  <symbol id="i-pulse" viewBox="0 0 24 24"><path d="M3 12.6h3.6l2-4 2.8 7.8 2-3.8H21"/></symbol>
  <symbol id="i-list" viewBox="0 0 24 24"><path d="M9 7h10M9 12h10M9 17h6.6"/><circle cx="5.2" cy="7" r="1.1" fill="currentColor" stroke="none"/><circle cx="5.2" cy="12" r="1.1" fill="currentColor" stroke="none"/><circle cx="5.2" cy="17" r="1.1" fill="currentColor" stroke="none"/></symbol>
  <symbol id="i-heart" viewBox="0 0 24 24"><path d="M12 20.2c-1.7-1.4-7.4-5.3-7.4-9.7A4.4 4.4 0 0 1 12 7.6a4.4 4.4 0 0 1 7.4 2.9c0 4.4-5.7 8.3-7.4 9.7Z"/></symbol>
  <symbol id="i-refresh" viewBox="0 0 24 24"><path d="M19.6 12a7.6 7.6 0 1 1-2.2-5.4"/><path d="M19.8 4.6v4.6h-4.6"/></symbol>
  <symbol id="i-plus" viewBox="0 0 24 24"><path d="M12 5.4v13.2M5.4 12h13.2"/></symbol>
  <symbol id="i-trash" viewBox="0 0 24 24"><path d="M4.8 7.4h14.4M9.4 7.4V5.6h5.2v1.8"/><path d="m6.6 7.4.9 11.1A1.6 1.6 0 0 0 9.1 20h5.8a1.6 1.6 0 0 0 1.6-1.5l.9-11.1"/></symbol>
  <symbol id="i-edit" viewBox="0 0 24 24"><path d="M15.2 4.6l4.2 4.2L8.8 19.4 4.2 20l.6-4.6L15.2 4.6Z"/><path d="m13.4 6.4 4.2 4.2"/></symbol>
  <symbol id="i-close" viewBox="0 0 24 24"><path d="m6.4 6.4 11.2 11.2M17.6 6.4 6.4 17.6"/></symbol>
  <symbol id="i-send" viewBox="0 0 24 24"><path d="M20.6 3.4 3.4 10.6l6.8 2.6 2.6 6.8 7.8-16.6Z"/><path d="m10.4 13.4 4.6-4.4"/></symbol>
  <symbol id="i-leaf" viewBox="0 0 32 32"><path d="M16 3c6.6 1.6 11 6.4 11 12.4 0 6.6-5.4 11.6-12.6 12.9C9.7 26.9 6 22.6 6 17.3 6 10.6 10.3 5.2 16 3Z" fill="#1668b3" stroke="none"/><path d="M16 7.4c.8 4.6.4 9.2-2.2 13.4-1.9 3-3.4 4.4-4.8 5.4" stroke="#fff" stroke-width="1.8" fill="none" stroke-linecap="round"/><path d="M17.6 12.8c1.6-1.2 3.4-1.9 5.4-2.2" stroke="#fff" stroke-width="1.5" fill="none" stroke-linecap="round"/><path d="M16.6 18c1.7-1 3.4-1.6 5.3-1.9" stroke="#fff" stroke-width="1.5" fill="none" stroke-linecap="round"/></symbol>
</svg>`;

function icon(id, cls) {
  return `<svg class="ico ${cls || ''}"><use href="#${id}"></use></svg>`;
}

function avatarSvg() {
  return `<svg viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <radialGradient id="avbg" cx="50%" cy="42%" r="62%">
        <stop offset="0%" stop-color="#eaf7ff"/><stop offset="100%" stop-color="#cfe8fb"/>
      </radialGradient>
      <linearGradient id="coat" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#ffffff"/><stop offset="100%" stop-color="#eaf1f7"/>
      </linearGradient>
      <clipPath id="avclip"><circle cx="60" cy="60" r="59"/></clipPath>
    </defs>
    <g clip-path="url(#avclip)">
      <circle cx="60" cy="60" r="60" fill="url(#avbg)"/>
      <g transform="translate(60 70) scale(1.76) translate(-60 -70)">
        <path d="M28 108c3-15 15-23 32-23s29 8 32 23z" fill="url(#coat)"/>
        <path d="M52 82h16v10H52z" fill="#f7cdb8"/>
        <path d="M47 88c4 6 22 6 26 0l4 6H43z" fill="#ffffff"/>
        <path d="M60 96v10" stroke="#c9d7e2" stroke-width="2"/>
        <circle cx="60" cy="18" r="6" fill="#2b3f4e"/>
        <ellipse cx="60" cy="56" rx="23" ry="24" fill="#ffe3d3"/>
        <path d="M36 52c0-15 11-24 24-24s24 9 24 24c0 3-1 6-2 8-1-7-4-10-8-10-3 0-5-3-11-3s-9 3-11 3c-4 0-8 3-9 10-1-2-7-5-7-8z" fill="#2b3f4e"/>
        <path d="M34 55c-3 0-5 3-4 6 1 3 3 4 5 4zM86 55c3 0 5 3 4 6-1 3-3 4-5 4z" fill="#ffe3d3"/>
        <ellipse cx="50" cy="57" rx="3.6" ry="4.6" fill="#243746"/>
        <ellipse cx="70" cy="57" rx="3.6" ry="4.6" fill="#243746"/>
        <circle cx="51.3" cy="55.2" r="1.3" fill="#ffffff"/>
        <circle cx="71.3" cy="55.2" r="1.3" fill="#ffffff"/>
        <ellipse cx="42" cy="66" rx="4.2" ry="2.5" fill="#ffc2c0" opacity=".9"/>
        <ellipse cx="78" cy="66" rx="4.2" ry="2.5" fill="#ffc2c0" opacity=".9"/>
        <path d="M56 68c1.6 1.9 6.4 1.9 8 0" stroke="#d98d7c" stroke-width="1.8" fill="none" stroke-linecap="round"/>
        <path d="M44 84c5-3 8-3 10 0M76 84c-5-3-8-3-10 0" stroke="#5f7d94" stroke-width="2.4" fill="none" stroke-linecap="round"/>
        <circle cx="60" cy="100" r="4" fill="#c9d7e2"/>
      </g>
    </g>
  </svg>`;
}

function doctorAvatarSvg() {
  return `<svg viewBox="0 0 60 60" xmlns="http://www.w3.org/2000/svg">
    <defs><clipPath id="dclip"><circle cx="30" cy="30" r="30"/></clipPath></defs>
    <g clip-path="url(#dclip)">
      <circle cx="30" cy="30" r="30" fill="#e8f6ef"/>
      <g transform="translate(30 36) scale(1.42) translate(-30 -36)">
        <path d="M15 56c2-8 8-11 15-11s13 3 15 11z" fill="#fff"/>
        <ellipse cx="30" cy="33" rx="12" ry="13" fill="#ffe3d3"/>
        <path d="M17 31c0-8 6-13 13-13s13 5 13 13c-1-4-3-6-6-6-2 0-4-2-7-2s-5 2-7 2c-3 0-5 2-6 6z" fill="#3a4a57"/>
        <circle cx="26" cy="34" r="1.9" fill="#2b3a45"/><circle cx="34" cy="34" r="1.9" fill="#2b3a45"/>
        <path d="M28 39c1.2 1 3.8 1 5 0" stroke="#d98d7c" stroke-width="1.4" fill="none" stroke-linecap="round"/>
      </g>
    </g>
  </svg>`;
}

/* ---------------------- 药品缩略图（内联 SVG） ---------------------- */
const MED_PALETTE = {
  '感冒类': ['#2f9e6f', '#238157'],
  '退烧止痛': ['#3f7fbf', '#2c6299'],
  '抗感染': ['#c0555a', '#a13f45'],
  '抗病毒': ['#4b8f8a', '#37716d'],
  '止咳类': ['#3f8fb0', '#2c728f'],
  '止咳化痰': ['#3f8fb0', '#2c728f'],
  '过敏类': ['#6f7fbe', '#556398'],
  '消化类': ['#c98a3d', '#a96f2b'],
  '肠胃调理': ['#b8813a', '#9a6a2c'],
  '慢病类': ['#4f6f9f', '#3a567f'],
  '中成药': ['#a25f3f', '#834a2f'],
  '营养补充': ['#d29a35', '#b17f22'],
  '皮肤外用': ['#b06a86', '#90526c'],
  '五官科': ['#4f97b8', '#3a7a97'],
  '儿童用药': ['#e08a5a', '#c46f40'],
  '止痛类': ['#3f7fbf', '#2c6299'],
};

// 真实药品照片清单（tools/fetch_med_images.py 生成），未命中时回退到 SVG 示意图
let MED_IMAGES = {};
async function ensureMedImages() {
  if (Object.keys(MED_IMAGES).length) return;
  try {
    const res = await fetch('images/medicines/index.json', { cache: 'no-cache' });
    if (res.ok) MED_IMAGES = await res.json();
  } catch (e) {
    MED_IMAGES = {};
  }
}

function medThumb(item) {
  const real = MED_IMAGES[item.name];
  if (real) return real;
  const palette = MED_PALETTE[item.category] || ['#4f8fa8', '#357388'];
  const name = String(item.name || '').replace(/[（(].*?[）)]/g, '');
  const short = name.length > 6 ? name.slice(0, 6) + '…' : name;
  const brand = String(item.brand || '').replace('牌', '');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180" viewBox="0 0 180 180">
    <defs>
      <linearGradient id="mg" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="${palette[0]}"/>
        <stop offset="1" stop-color="${palette[1]}"/>
      </linearGradient>
    </defs>
    <rect width="180" height="180" fill="#ffffff"/>
    <ellipse cx="92" cy="154" rx="60" ry="10" fill="#16242f" opacity="0.10"/>
    <polygon points="40,56 124,56 142,43 58,43" fill="${palette[0]}"/>
    <polygon points="40,56 124,56 142,43 58,43" fill="#ffffff" opacity="0.22"/>
    <polygon points="124,56 142,43 142,129 124,142" fill="${palette[1]}"/>
    <polygon points="124,56 142,43 142,129 124,142" fill="#000000" opacity="0.18"/>
    <rect x="40" y="56" width="84" height="86" fill="url(#mg)"/>
    <rect x="40" y="56" width="84" height="16" fill="#ffffff" opacity="0.14"/>
    <circle cx="56" cy="74" r="8" fill="#ffffff" opacity="0.92"/>
    <text x="56" y="78" text-anchor="middle" font-size="10" font-weight="700" fill="${palette[0]}" font-family="Arial">${esc(brand.slice(0, 2))}</text>
    <text x="82" y="106" text-anchor="middle" font-size="14" font-weight="700" fill="#ffffff" font-family="Microsoft YaHei, PingFang SC, sans-serif">${esc(short)}</text>
    <rect x="52" y="116" width="60" height="1.5" fill="#ffffff" opacity="0.5"/>
    <text x="82" y="132" text-anchor="middle" font-size="9" fill="#ffffff" opacity="0.9" font-family="Microsoft YaHei, PingFang SC, sans-serif">${esc(item.category || '')}</text>
    <rect x="104" y="64" width="18" height="14" rx="3" fill="#ffffff" opacity="0.92"/>
    <text x="113" y="74" text-anchor="middle" font-size="9" fill="${palette[0]}" font-family="Arial" font-weight="700">OTC</text>
  </svg>`;
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

/* ---------------------- 页面骨架渲染 ---------------------- */
const NAV_ITEMS = [
  { key: 'site-home', href: 'index.html', label: '首页' },
  { key: 'home', href: 'disease.html', label: '疾病百科' },
  { key: 'medicine', href: 'medicine.html', label: '查找药品' },
  { key: 'identify', href: 'identify.html', label: '拍照识药' },
  { key: 'cabinet', href: 'cabinet.html', label: '家庭药箱' },
  { key: 'doctor', href: 'doctor.html', label: '智能医生' },
  { key: 'about', href: 'about.html', label: '关于我们' },
];

const SIDE_ITEMS = [
  { key: 'profile', href: 'profile.html', label: '我的资料', icon: 'i-user' },
  { key: 'cabinet', href: 'cabinet.html', label: '家庭药箱', icon: 'i-box' },
  { key: 'lab-report', href: 'lab-report.html', label: '化验单解读', icon: 'i-clipboard' },
  { key: 'favorites', href: 'favorites.html', label: '我的收藏', icon: 'i-heart' },
  { key: 'doctor', href: 'doctor.html', label: '智能医生', icon: 'i-chat' },
  { key: 'medicine', href: 'medicine.html', label: '查找药品', icon: 'i-search' },
  { key: 'manage-disease', href: 'manage-disease.html', label: '疾病管理', icon: 'i-clipboard' },
  { key: 'manage-medicine', href: 'manage-medicine.html', label: '药品管理', icon: 'i-box' },
  { key: 'feedback', href: 'feedback.html', label: '反馈管理', icon: 'i-feedback' },
];

function renderHeader(active) {
  const host = document.getElementById('appHeader');
  if (!host) return;
  const user = getUser();
  const withStrip = host.dataset.strip === '1';
  const strip = withStrip ? `
    <div class="top-strip">
      <div class="inner">
        <div class="marquee">丹心擎划宏图展，医界绽奇葩【欢迎来到<b>AIDoctor · AI 智能诊疗助手</b>】</div>
        <form class="strip-search" id="stripSearchForm">
          <input id="stripSearchInput" type="search" placeholder="搜索一下" aria-label="搜索" />
          <button type="submit" aria-label="搜索">${icon('i-search')}</button>
        </form>
      </div>
    </div>` : '';

  host.innerHTML = `
    ${strip}
    <header class="site-header">
      <div class="inner">
        <a class="brand" href="index.html">
          <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" fill="#1668b3"/><path d="M13.2 6.6h5.6v6.6h6.6v5.6h-6.6v6.6h-5.6v-6.6H6.6v-5.6h6.6V6.6Z" fill="#ffffff"/></svg>
          <span class="name">AI<span>Doctor</span></span>
        </a>
        <nav class="main-nav">
          ${NAV_ITEMS.map((item) => `<a href="${item.href}" class="${item.key === active ? 'active' : ''}">${item.label}</a>`).join('')}
        </nav>
        <div class="header-right">
          <div class="member-switch" id="memberSwitch">
            <button class="ms-trigger" type="button" id="msBtn" aria-haspopup="true">
              <span class="ms-avatar" id="msAvatar">＋</span>
              <span class="ms-text"><em>当前服药对象</em><b id="msName">未设置</b></span>
              ${icon('i-chevron')}
            </button>
            <div class="ms-menu" id="msMenu"></div>
          </div>
          <div class="notify-wrap" id="notifyWrap">
            <button class="notify-bell" id="notifyBell" type="button" aria-label="消息通知">
              ${icon('i-bell')}
              <span class="notify-badge" id="notifyBadge" style="display:none"></span>
            </button>
            <div class="notify-panel" id="notifyPanel">
              <div class="notify-head">消息通知</div>
              <div class="notify-list" id="notifyList"><p class="notify-empty">暂无新消息</p></div>
            </div>
          </div>
          <div class="user-chip" id="userChip">
            <span class="avatar-mini">${user && user.username === 'admin' ? '系' : esc((displayName() || '访').slice(0, 1))}</span>
            <span class="chip-text">欢迎你【${esc(displayName())}】</span>
            ${icon('i-chevron')}
            <div class="user-menu" id="userMenu">
              <a href="profile.html">${icon('i-user')}我的资料</a>
              <a href="cabinet.html">${icon('i-box')}家庭药箱</a>
              <a href="favorites.html">${icon('i-heart')}我的收藏</a>
              <a href="lab-report.html">${icon('i-clipboard')}化验单解读</a>
              <a href="records.html">${icon('i-chat')}问诊记录</a>
              <a href="feedback.html">${icon('i-feedback')}反馈管理</a>
              <a href="javascript:void(0)" id="menuLogout">${icon('i-logout')}退出登录</a>
            </div>
          </div>
        </div>
      </div>
    </header>`;

  const chip = document.getElementById('userChip');
  const menu = document.getElementById('userMenu');
  if (chip && menu) {
    chip.addEventListener('click', (event) => {
      if (event.target.closest('.user-menu')) return;
      menu.classList.toggle('open');
    });
    document.addEventListener('click', (event) => {
      if (!chip.contains(event.target)) menu.classList.remove('open');
    });
  }
  const logoutLink = document.getElementById('menuLogout');
  if (logoutLink) logoutLink.addEventListener('click', logout);

  const ms = document.getElementById('memberSwitch');
  if (ms) {
    const msBtn = document.getElementById('msBtn');
    if (msBtn) {
      msBtn.addEventListener('click', (event) => {
        event.stopPropagation();
        ms.classList.toggle('open');
      });
    }
    document.addEventListener('click', (event) => {
      if (!ms.contains(event.target)) ms.classList.remove('open');
    });
    renderMemberSwitch();
  }

  const form = document.getElementById('stripSearchForm');
  if (form) {
    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const value = document.getElementById('stripSearchInput').value.trim();
      if (!value) return;
      window.location.href = 'search.html?q=' + encodeURIComponent(value);
    });
  }

  // —— 医生回复消息通知（右上角铃铛） ——
  const notifyBell = document.getElementById('notifyBell');
  const notifyPanel = document.getElementById('notifyPanel');
  const notifyBadge = document.getElementById('notifyBadge');
  const notifyList = document.getElementById('notifyList');
  const notifyUser = getUser();
  if (notifyBell && notifyPanel && notifyUser && notifyUser.username) {
    const unreadName = encodeURIComponent(notifyUser.username);
    let notifyOpen = false;

    async function refreshNotifyBadge() {
      try {
        const data = await apiGet('/api/notifications?username=' + unreadName + '&_=' + Date.now());
        const count = Number(data.count || 0);
        if (count > 0) {
          notifyBadge.textContent = count > 99 ? '99+' : String(count);
          notifyBadge.style.display = 'flex';
          notifyBell.classList.add('has-unread');
        } else {
          notifyBadge.style.display = 'none';
          notifyBell.classList.remove('has-unread');
        }
      } catch (e) { /* 通知获取失败时静默 */ }
    }

    function renderNotifyItems(items) {
      if (!items.length) {
        notifyList.innerHTML = '<p class="notify-empty">暂无新消息，医生回复或下达用药方案后会第一时间通知你。</p>';
        return;
      }
      notifyList.innerHTML = items.map((it) => it.type === 'plan' ? `
        <div class="notify-item plan">
          <div class="ni-top">
            <b>${esc(it.doctorName || '医生')}</b><span class="ni-spec">用药方案</span>
            <time>${esc(it.createdAt || '')}</time>
          </div>
          <div class="ni-reply">给你开了 <strong>${esc(it.medicineName || '')}</strong> · ${esc(it.dosage || '按医嘱')}</div>
          <div class="ni-cond">服用时间：${esc(it.times || '')}${it.endDate ? ' · 至 ' + esc(it.endDate) : ''}</div>
          ${it.note ? `<div class="ni-cond">医嘱：${esc(it.note)}</div>` : ''}
          <a class="ni-link" href="cabinet.html">去家庭药箱查看 →</a>
        </div>` : `
        <div class="notify-item">
          <div class="ni-top">
            <b>${esc(it.doctorName)}</b><span class="ni-spec">${esc(it.specialty)}</span>
            <time>${esc(it.repliedAt || '')}</time>
          </div>
          <div class="ni-cond">病情描述：${esc(String(it.condition || '').slice(0, 42))}</div>
          <div class="ni-reply">${esc(it.reply || '')}</div>
        </div>`).join('');
    }

    async function openNotifyPanel() {
      notifyOpen = true;
      notifyPanel.classList.add('open');
      notifyList.innerHTML = '<p class="notify-empty">加载中…</p>';
      try {
        const data = await apiGet('/api/notifications?username=' + unreadName + '&detail=1&_=' + Date.now());
        renderNotifyItems(Array.isArray(data.items) ? data.items : []);
        // 已查看：标记已读并清空角标
        await apiPost('/api/notifications?username=' + unreadName, {});
        notifyBadge.style.display = 'none';
        notifyBell.classList.remove('has-unread');
      } catch (e) {
        notifyList.innerHTML = '<p class="notify-empty">通知加载失败，请稍后重试。</p>';
      }
    }

    function closeNotifyPanel() {
      notifyOpen = false;
      notifyPanel.classList.remove('open');
    }

    notifyBell.addEventListener('click', (event) => {
      event.stopPropagation();
      if (notifyOpen) closeNotifyPanel(); else openNotifyPanel();
    });
    notifyPanel.addEventListener('click', (event) => event.stopPropagation());
    document.addEventListener('click', () => { if (notifyOpen) closeNotifyPanel(); });
    setInterval(refreshNotifyBadge, 15000);
    refreshNotifyBadge();
  }
}

function renderSidebar(active) {
  const host = document.getElementById('sidebar');
  if (!host) return;
  const profile = state.profile || {};
  const phone = profile.phone || (isAdmin() ? '15696969153' : '未填写手机号');
  const email = profile.email || (isAdmin() ? 'admin@example.com' : '未填写邮箱');
  host.className = 'panel sidebar';
  host.innerHTML = `
    <div class="profile-head">
      <div class="avatar-ring">${avatarSvg()}</div>
      <h3>${esc(displayName())}</h3>
      <div class="profile-meta">
        <div>${icon('i-phone')}<span>${esc(phone)}</span></div>
        <div>${icon('i-mail')}<span>${esc(email)}</span></div>
      </div>
    </div>
    <nav class="side-menu">
      ${SIDE_ITEMS.map((item) => `
        <a href="${item.href}" class="${item.key === active ? 'active' : ''}">
          ${icon(item.icon)}<span>${item.label}</span>
        </a>`).join('')}
      <a href="javascript:void(0)" class="danger" id="sideLogout">${icon('i-logout')}<span>退出登录</span></a>
    </nav>`;
  const sideLogout = document.getElementById('sideLogout');
  if (sideLogout) sideLogout.addEventListener('click', logout);
}

function renderFooter() {
  const host = document.getElementById('appFooter');
  if (!host) return;
  host.innerHTML = `
    <div class="feedback-band">
      <div class="inner">
        <h3>哪里不对？</h3>
        <p>用着别扭的地方、写错的内容、想要的功能，都写在这里。</p>
        <div class="spacer"></div>
        <a class="band-btn" href="feedback.html">提交反馈 ${icon('i-chevron')}</a>
      </div>
    </div>
    <footer class="site-footer">
      <div class="inner">
        <span>© 2026 AIDoctor · AI 智能诊疗助手</span>
        <span>AI 诊断仅供参考，严重症状请及时就医</span>
      </div>
    </footer>`;
}

function setupChrome(active) {
  document.body.insertAdjacentHTML('afterbegin', SPRITE);
  renderHeader(active);
  if (document.getElementById('sidebar')) renderSidebar(active);
  renderFooter();
}

async function loadProfile() {
  const user = getUser();
  if (!user || !user.username) return {};
  try {
    const data = await apiGet('/api/profile?username=' + encodeURIComponent(user.username));
    state.profile = data.profile || {};
  } catch (e) {
    state.profile = {};
  }
  return state.profile;
}

/* ---------------------- 疾病百科 ---------------------- */
const CATEGORY_COLORS = {
  '呼吸系统疾病': ['#2f9ec4', '#49b8d8'],
  '精神心理疾病': ['#5a5c7d', '#73759b'],
  '心血管疾病': ['#c14b5c', '#dd7180'],
  '消化系统疾病': ['#12857a', '#28a699'],
  '内分泌疾病': ['#2f8f6d', '#4aab88'],
  '神经系统疾病': ['#7a6ca8', '#9587c2'],
  '皮肤疾病': ['#c2803c', '#dda05c'],
  '耳鼻喉疾病': ['#3f78a8', '#5d95c2'],
  '眼科疾病': ['#4a7fb5', '#6f9ed1'],
  '骨关节疾病': ['#8a6a4f', '#a98768'],
  '泌尿系统疾病': ['#3d8f96', '#5aaeb4'],
  '血液系统疾病': ['#a8556b', '#c4748a'],
};

function diseaseCard(item) {
  const colors = CATEGORY_COLORS[item.category] || ['#2f9ec4', '#49b8d8'];
  const views = Number(item.views || 0);
  return `
  <article class="disease-card" style="--cat:${colors[0]};--cat2:${colors[1]}">
    <div class="dc-head">
      <span class="left">${icon('i-shield')}${esc(item.category || '常见疾病')}</span>
      <span class="dc-views">${icon('i-eye')}${views}</span>
    </div>
    <div class="dc-body">
      <h3>${esc(item.name)}</h3>
      <p class="dc-desc">${esc(item.causes || item.description || '')}</p>
      <ul class="dc-list">
        <li class="ok">${esc(item.symptoms || '症状信息待完善')}</li>
        <li class="warn">${esc(item.focus || '如症状持续或加重请及时就医')}</li>
      </ul>
      <div class="dc-tags">
        <span class="tag tag-green">常见症状</span>
        <span class="tag tag-red">重点关注</span>
      </div>
    </div>
    <div class="dc-foot">
      <span>${esc(item.createdAt || '')}</span>
      <a href="disease-detail.html?name=${encodeURIComponent(item.name)}">查看详情 &gt;</a>
    </div>
  </article>`;
}

async function initHome() {
  setupChrome('home');
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">全部病种</h1>
    <div class="page-visual pv-warm">
      <img src="images/disease-visual.jpg" alt="木桌上的医学图谱、听诊器与绿植" />
      <div class="pv-text">
        <strong>常见病种，按系统归类</strong>
        <span>病因、典型症状与重点关注提示放在一起，先判断要不要去医院。</span>
      </div>
    </div>
    <div class="toolbar">
      <label class="search-field">
        ${icon('i-search')}
        <input id="diseaseSearch" type="search" placeholder="搜索病种名称、分类或症状" />
      </label>
      <div class="chips" id="categoryChips"></div>
    </div>
    <div class="disease-grid" id="diseaseGrid"><p class="empty-tip">加载中…</p></div>`;

  try {
    state.diseases = await apiGet('/api/diseases');
  } catch (e) {
    document.getElementById('diseaseGrid').innerHTML = '<p class="empty-tip">病种数据加载失败，请稍后重试</p>';
    return;
  }

  const categories = ['全部', ...new Set(state.diseases.map((d) => d.category).filter(Boolean))];
  const chips = document.getElementById('categoryChips');
  chips.innerHTML = categories.map((c, i) => `<button class="chip-btn ${i === 0 ? 'active' : ''}" data-cat="${esc(c)}">${esc(c)}</button>`).join('');

  let activeCat = '全部';
  let keyword = '';

  const paint = () => {
    const list = state.diseases.filter((d) => {
      const catOk = activeCat === '全部' || d.category === activeCat;
      const keyOk = !keyword || [d.name, d.category, d.tag, d.causes, d.symptoms, d.description].join(' ').includes(keyword);
      return catOk && keyOk;
    });
    document.getElementById('diseaseGrid').innerHTML = list.length
      ? list.map(diseaseCard).join('')
      : '<p class="empty-tip">没有找到相关病种，换个关键词试试</p>';
  };

  chips.querySelectorAll('button').forEach((btn) => {
    btn.addEventListener('click', () => {
      activeCat = btn.dataset.cat;
      chips.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b === btn));
      paint();
    });
  });
  document.getElementById('diseaseSearch').addEventListener('input', (event) => {
    keyword = event.target.value.trim();
    paint();
  });

  paint();
}

/* ---------------------- 药品列表 ---------------------- */
function medicineRow(item) {
  return `
  <a class="med-row" href="medicine-detail.html?name=${encodeURIComponent(item.name)}">
    <div class="med-thumb"><img src="${medThumb(item)}" alt="${esc(item.name)}" /></div>
    <div class="med-main">
      <div class="med-head">
        <h3>${esc(item.name)}</h3>
        <span class="kind ${item.kind === '西药' ? 'western' : ''}">${esc(item.kind || '西药')}</span>
        <span class="med-quote">${esc(item.brand || '')}</span>
      </div>
      <p class="med-use">${esc(item.use)}</p>
    </div>
    <div class="med-brand">${esc(item.price || '')}</div>
  </a>`;
}

async function initMedicine(activeKey) {
  setupChrome(activeKey || 'medicine');
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">全部药品</h1>
    <div class="page-visual pv-warm">
      <img src="images/pharmacy-visual.jpg" alt="药店木质货架上排列整齐的药品" />
      <div class="pv-text">
        <strong>154 种常用药，按症状就能找</strong>
        <span>药名、品牌、症状都能搜。中成药和西药分开标，价格在右边。</span>
      </div>
    </div>
    <div class="toolbar">
      <label class="search-field">
        ${icon('i-search')}
        <input id="medicineSearch" type="search" placeholder="搜药名、品牌或症状" />
      </label>
      <a class="btn btn-ghost btn-sm" href="identify.html">拍照识药</a>
    </div>
    <div class="toolbar"><div class="chips" id="medicineChips"></div></div>
    <div class="med-list" id="medicineList"><p class="empty-tip">加载中…</p></div>`;

  try {
    state.medicines = await apiGet('/api/medicines');
  } catch (e) {
    document.getElementById('medicineList').innerHTML = '<p class="empty-tip">药品数据加载失败，请稍后重试</p>';
    return;
  }

  const categories = ['全部', ...new Set(state.medicines.map((m) => m.category).filter(Boolean))];
  const chips = document.getElementById('medicineChips');
  chips.innerHTML = categories.map((c, i) => `<button class="chip-btn ${i === 0 ? 'active' : ''}" data-cat="${esc(c)}">${esc(c)}</button>`).join('');

  let activeCat = '全部';
  let keyword = '';
  const paint = () => {
    const list = state.medicines.filter((m) => {
      const catOk = activeCat === '全部' || m.category === activeCat;
      const keyOk = !keyword || [m.name, m.category, m.use, m.brand, m.price].join(' ').includes(keyword);
      return catOk && keyOk;
    });
    document.getElementById('medicineList').innerHTML = list.length
      ? list.map(medicineRow).join('')
      : '<p class="empty-tip">没有找到相关药品，换个关键词试试</p>';
  };
  chips.querySelectorAll('button').forEach((btn) => {
    btn.addEventListener('click', () => {
      activeCat = btn.dataset.cat;
      chips.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b === btn));
      paint();
    });
  });
  document.getElementById('medicineSearch').addEventListener('input', (event) => {
    keyword = event.target.value.trim();
    paint();
  });
  paint();
}

/* ---------------------- 拍照识药 ---------------------- */
/* ---------------------- 拍照识药 ---------------------- */
/* 拍照/相册图片先压到最长边 1600px，既控制上传体积也提高 OCR 识别率 */
async function compressImage(file, maxEdge = 1600, quality = 0.86) {
  const source = await loadImageFile(file);
  const ratio = Math.min(1, maxEdge / Math.max(source.width, source.height));
  const width = Math.max(1, Math.round(source.width * ratio));
  const height = Math.max(1, Math.round(source.height * ratio));
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(source, 0, 0, width, height);
  if (source.close) source.close();
  return canvas.toDataURL('image/jpeg', quality);
}

function loadImageFile(file) {
  if (window.createImageBitmap) {
    // imageOrientation: from-image 用于修正手机竖拍照片的 EXIF 旋转
    return createImageBitmap(file, { imageOrientation: 'from-image' }).catch(() => loadImageByTag(file));
  }
  return loadImageByTag(file);
}

function loadImageByTag(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('图片读取失败，请换一张试试'));
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('图片格式不支持，请使用 JPG / PNG'));
      img.onload = () => resolve(img);
      img.src = String(reader.result || '');
    };
    reader.readAsDataURL(file);
  });
}

async function initIdentify() {
  setupChrome('identify');
  const panel = document.getElementById('mainPanel');
  const isMobile = /Android|iPhone|iPad|iPod|HarmonyOS|Mobile/i.test(navigator.userAgent);
  panel.innerHTML = `
    <h1 class="panel-title">拍照识药</h1>
    <div class="page-visual">
      <img src="images/identify-visual.jpg" alt="用手机拍摄药盒进行识别" />
      <div class="pv-text">
        <strong>拍一张药盒，先看清成分</strong>
        <span>拍药盒正面，或者把说明书上的字粘进来。认不准的字会单独标出来让你核对。</span>
      </div>
    </div>
    <div class="identify-head">
      <p>上传药盒/药瓶照片，系统将识别文字并自动检索药品候选</p>
      <p class="identify-tip">拍的是化验单？<a href="lab-report.html">去「化验单解读」→</a></p>
    </div>
    <div class="identify-grid">
      <div>
        <div class="file-row">
          ${isMobile ? `<label class="file-btn" for="cameraFile">拍照</label>
          <input id="cameraFile" type="file" accept="image/*" capture="environment" hidden />` : ''}
          <label class="file-btn" for="imageFile">${isMobile ? '从相册选择' : '选择文件'}</label>
          <input id="imageFile" type="file" accept="image/*" hidden />
          <span class="file-name" id="fileName">未选择任何文件</span>
          <button class="btn btn-primary btn-sm" id="startOcr" type="button">开始识别</button>
        </div>
        <div class="preview-box" id="previewBox">图片预览区，请先选择药盒照片</div>
      </div>
      <div class="ocr-side">
        <div class="label">识别文本 <small>（可手动修改后再检索）</small></div>
        <textarea class="ocr-text" id="ocrText" placeholder="识别出的药盒文字会显示在这里，也可以直接把药名、批准文号、用法用量粘贴进来"></textarea>
        <div class="ocr-status" id="ocrStatus"></div>
        <button class="btn btn-primary" id="searchByText" type="button">用文本检索</button>
      </div>
    </div>
    <h3 class="section-heading">候选药品</h3>
    <div class="med-list" id="candidateList"><p class="empty-tip">识别或输入文字后，点击「用文本检索」查看候选药品</p></div>`;

  let imageData = '';
  const fileInput = document.getElementById('imageFile');
  const cameraInput = document.getElementById('cameraFile');
  const previewBox = document.getElementById('previewBox');
  const fileName = document.getElementById('fileName');
  const ocrText = document.getElementById('ocrText');
  const status = document.getElementById('ocrStatus');
  const candidateList = document.getElementById('candidateList');

  // 页面进入时先查一次引擎状态，没配密钥就提前说清楚，避免用户以为功能坏了
  apiGet('/api/ocr/status').then((info) => {
    if (!info.ready) {
      status.textContent = '当前未配置 OCR 密钥，仅支持手动输入文字检索。配置方法见 config.properties。';
      status.classList.add('hint');
    }
  }).catch(() => {});

  const handleFile = async (input) => {
    const file = input.files && input.files[0];
    if (!file) return;
    fileName.textContent = file.name;
    status.classList.remove('error', 'hint');
    status.textContent = '正在处理图片…';
    try {
      imageData = await compressImage(file);
      previewBox.innerHTML = `<img src="${imageData}" alt="药盒预览" />`;
      status.textContent = '图片已就绪，点【开始识别】';
    } catch (error) {
      imageData = '';
      status.textContent = error.message;
      status.classList.add('error');
    }
  };

  fileInput.addEventListener('change', () => handleFile(fileInput));
  if (cameraInput) cameraInput.addEventListener('change', () => handleFile(cameraInput));

  document.getElementById('startOcr').addEventListener('click', async () => {
    if (!imageData) {
      status.textContent = '请先选择一张药盒或药瓶照片';
      status.classList.add('error');
      return;
    }
    status.classList.remove('error', 'hint');
    status.textContent = '正在识别图片文字…';
    try {
      const data = await apiPost('/api/ocr', { image: imageData, fileName: fileName.textContent });
      if (data.text) ocrText.value = data.text;
      status.textContent = data.message || (data.success ? '识别完成' : '识别未完成');
      if (!data.success) {
        status.classList.add('error');
        ocrText.focus();
      }
    } catch (error) {
      status.textContent = error.message;
      status.classList.add('error');
    }
  });

  document.getElementById('searchByText').addEventListener('click', async () => {
    const text = ocrText.value.trim();
    if (!text) {
      status.textContent = '请先输入或识别药盒文字';
      status.classList.add('error');
      return;
    }
    status.classList.remove('error');
    status.textContent = '正在检索候选药品…';
    try {
      const data = await apiPost('/api/identify', { text });
      const list = data.candidates || [];
      candidateList.innerHTML = list.length
        ? list.map((item) => `
          <a class="med-row" href="medicine-detail.html?name=${encodeURIComponent(item.name)}">
            <div class="med-thumb"><img src="${medThumb(item)}" alt="${esc(item.name)}" /></div>
            <div class="med-main">
              <div class="med-head">
                <h3>${esc(item.name)}</h3>
                <span class="kind ${item.kind === '西药' ? 'western' : ''}">${esc(item.kind)}</span>
              </div>
              <p class="med-use">${esc(item.use)}</p>
            </div>
            <span class="med-score">匹配 ${item.score}</span>
            <span class="med-brand">${esc(item.brand || '')}</span>
          </a>`).join('')
        : '<p class="empty-tip">未匹配到药品，可只保留药名或品牌名再试一次</p>';
      status.textContent = data.message;
    } catch (error) {
      status.textContent = error.message;
      status.classList.add('error');
    }
  });
}

/* ---------------------- 化验单解读 ---------------------- */
function labLevelLabel(level, fallback) {
  if (fallback) return fallback;
  if (level === 'red') return '建议尽快就医';
  if (level === 'amber') return '需要复查';
  return '基本正常';
}

function renderLabReport(box, data) {
  const level = data.level === 'red' || data.level === 'green' ? data.level : 'amber';
  const items = data.items || [];
  const unverified = items.filter((i) => i.verified === false).length;
  const carried = items.filter((i) => i.flagFrom === 'original').length;
  const flagHint = carried
    ? `箭头按参考范围计算，其中 ${carried} 项参考范围没读出来，沿用原单箭头`
    : '箭头由系统按参考范围重新算过';
  const total = data.totalCount || items.length;
  const abnormal = data.abnormalCount || 0;

  const rows = items.map((item) => {
    const flag = item.flag === '↑' ? '<i class="lab-flag up">↑</i>'
      : (item.flag === '↓' ? '<i class="lab-flag down">↓</i>' : '');
    const unknown = item.verified === false ? ' unverified' : '';
    return `<tr class="${item.abnormal ? 'abn' : ''}${unknown}">
      <td class="lab-name">${esc(item.name)}${item.verified === false ? '<em class="lab-tocheck">待核对</em>' : ''}</td>
      <td class="lab-val"><b>${esc(item.value)}</b> ${flag}${item.unit ? `<span class="lab-unit">${esc(item.unit)}</span>` : ''}</td>
      <td class="lab-range">${esc(item.range || '—')}</td>
    </tr>`;
  }).join('');

  const groups = (data.groups || []).map((group) => `
    <section class="lab-group">
      <h4>${esc(group.system)}</h4>
      <ul>${(group.points || []).map((p) => `<li>${esc(p)}</li>`).join('')}</ul>
    </section>`).join('');

  const warnings = (data.drugWarnings || []).map((w) => `
    <div class="lab-drugwarn">
      <div class="dw-head">${icon('i-pulse')}用药提醒${w.drug ? ' · ' + esc(w.drug) : ''}</div>
      <p>${esc(w.text)}</p>
    </div>`).join('');

  const advice = (data.advice || []).map((a) => `<li>${esc(a)}</li>`).join('');

  box.innerHTML = `
    <div class="lab-card level-${level}">
      <div class="lab-head">
        <span class="lab-badge">${esc(labLevelLabel(level, data.levelLabel))}</span>
        <span class="lab-count">${total} 项检验，${abnormal} 项异常</span>
      </div>
      <p class="lab-summary">${esc(data.summary || '')}</p>
      ${data.recheck ? `<p class="lab-recheck">${icon('i-info')}${esc(data.recheck)}</p>` : ''}
    </div>

    <div class="lab-block">
      <div class="block-head"><h3 class="section-heading">检验明细</h3>
        <span class="block-hint">${flagHint}</span></div>
      <div class="lab-scroll">
        <table class="lab-table">
          <thead><tr><th>项目</th><th>结果</th><th>参考范围</th></tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
      ${unverified ? `<p class="lab-note">${icon('i-info')}有 ${unverified} 个数值没能在原文里核对上，已在表格里标灰并注明「待核对」，请自己看一眼是否正确。</p>` : ''}
    </div>

    ${groups ? `<div class="lab-block"><h3 class="section-heading">逐项解读</h3><div class="lab-groups">${groups}</div></div>` : ''}
    ${warnings}
    ${advice ? `<div class="lab-block"><h3 class="section-heading">接下来怎么做</h3><ul class="lab-advice">${advice}</ul></div>` : ''}

    <div class="lab-actions">
      <a class="btn btn-primary btn-sm" href="doctor.html">带着这份结果问医生</a>
      <a class="btn btn-ghost btn-sm" href="cabinet.html">打开家庭药箱</a>
      <button class="btn btn-ghost btn-sm" type="button" id="labAgain">再拍一张</button>
    </div>
    <p class="lab-disclaimer">本解读由 AI 生成，只说明指标含义与是否需要复诊，不能替代医生诊断。</p>`;
}

async function initLabReport() {
  setupChrome('lab-report');
  const panel = document.getElementById('mainPanel');
  const user = getUser() || {};
  const isMobile = /Android|iPhone|iPad|iPod|HarmonyOS|Mobile/i.test(navigator.userAgent);

  panel.innerHTML = `
    <h1 class="panel-title">化验单解读</h1>
    <div class="page-visual">
      <img src="images/lab-visual.jpg" alt="检验科血样与化验报告单" />
      <div class="pv-text">
        <strong>数值原样照抄，异常项逐个标出</strong>
        <span>识别错或读不到的数字单独提示核对，不猜测、不换算。</span>
      </div>
    </div>
    <p class="panel-desc">拍一张血常规、肝肾功、血糖血脂报告单，系统读出数值、按参考范围标出异常项，再结合你的档案和在吃的药解释一遍。识别错或读不到的数字会单独标出来让你核对，不会硬猜。</p>

    <div class="identify-grid">
      <div>
        <div class="file-row">
          ${isMobile ? `<label class="file-btn" for="labCamera">拍照</label>
          <input id="labCamera" type="file" accept="image/*" capture="environment" hidden />` : ''}
          <label class="file-btn" for="labFile">${isMobile ? '从相册选择' : '选择报告单'}</label>
          <input id="labFile" type="file" accept="image/*" hidden />
          <span class="file-name" id="labFileName">未选择任何文件</span>
          <button class="btn btn-primary btn-sm" id="labOcrBtn" type="button">识别文字</button>
        </div>
        <div class="preview-box" id="labPreview">报告单预览区，建议只拍「检验结果」那一块</div>
        <div class="ocr-status" id="labStatus"></div>
      </div>
      <div class="ocr-side">
        <div class="label">报告单文字 <small>（可手动修改，只改错的数字就行）</small></div>
        <textarea class="ocr-text" id="labText" placeholder="识别出的文字会出现在这里。也可以直接粘贴，例如：&#10;肌酐 128 umol/L 44-106 ↑&#10;ALT 62 U/L 9-50 ↑"></textarea>
        <button class="btn btn-primary" id="labGo" type="button">开始解读</button>
        <p class="lab-hintline">数值会逐项回原文核对，对不上的不参与判断</p>
      </div>
    </div>

    <div id="labResult"></div>

    <h3 class="section-heading">历史报告</h3>
    <div class="lab-history" id="labHistory"><p class="empty-tip">还没有解读过报告单</p></div>`;

  const fileInput = document.getElementById('labFile');
  const cameraInput = document.getElementById('labCamera');
  const preview = document.getElementById('labPreview');
  const fileName = document.getElementById('labFileName');
  const textarea = document.getElementById('labText');
  const status = document.getElementById('labStatus');
  const resultBox = document.getElementById('labResult');
  const historyBox = document.getElementById('labHistory');
  let imageData = '';

  const say = (text, kind) => {
    status.textContent = text || '';
    status.classList.remove('error', 'hint');
    if (kind) status.classList.add(kind);
  };

  const handleFile = async (input) => {
    const file = input.files && input.files[0];
    if (!file) return;
    fileName.textContent = file.name;
    say('正在处理图片…');
    try {
      imageData = await compressImage(file);
      preview.innerHTML = `<img src="${imageData}" alt="报告单预览" />`;
      say('图片已就绪，点【识别文字】');
    } catch (error) {
      imageData = '';
      say(error.message, 'error');
    }
  };

  fileInput.addEventListener('change', () => handleFile(fileInput));
  if (cameraInput) cameraInput.addEventListener('change', () => handleFile(cameraInput));

  document.getElementById('labOcrBtn').addEventListener('click', async () => {
    if (!imageData) {
      say('请先拍照或选择一张报告单', 'error');
      return;
    }
    say('正在识别报告单文字…');
    try {
      const data = await apiPost('/api/ocr', { image: imageData, fileName: fileName.textContent });
      if (data.text) textarea.value = data.text;
      say(data.message || '识别完成，确认文字无误后点【开始解读】', data.success ? '' : 'error');
    } catch (error) {
      say(error.message, 'error');
    }
  });

  const runAnalyze = async () => {
    const text = textarea.value.trim();
    if (text.length < 8) {
      say('先把报告单文字识别出来，或手动粘贴检验项目', 'error');
      return;
    }
    const button = document.getElementById('labGo');
    button.disabled = true;
    say('正在读取数值并解读，大约需要几秒…');
    resultBox.innerHTML = '<div class="lab-loading"><span class="sf-spin"></span>正在读取检验项目、核对数值、生成解读…</div>';
    try {
      const data = await apiPost('/api/lab-report', { username: user.username, text });
      if (!data.success) {
        resultBox.innerHTML = '';
        say(data.message || '解读失败', 'error');
        return;
      }
      say('解读完成');
      renderLabReport(resultBox, data);
      bindResultActions();
      loadHistory();
      resultBox.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) {
      resultBox.innerHTML = '';
      say(error.message, 'error');
    } finally {
      button.disabled = false;
    }
  };

  document.getElementById('labGo').addEventListener('click', runAnalyze);

  function bindResultActions() {
    const again = document.getElementById('labAgain');
    if (again) {
      again.addEventListener('click', () => {
        imageData = '';
        textarea.value = '';
        fileInput.value = '';
        if (cameraInput) cameraInput.value = '';
        fileName.textContent = '未选择任何文件';
        preview.innerHTML = '报告单预览区，建议只拍「检验结果」那一块';
        say('');
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    }
    resultBox.querySelectorAll('[data-del]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!window.confirm('删除这条历史报告？')) return;
        try {
          await apiPost('/api/lab-report', { action: 'delete', id: btn.dataset.del, username: user.username });
          loadHistory();
        } catch (error) {
          window.alert(error.message);
        }
      });
    });
  }

  async function loadHistory() {
    try {
      const data = await apiGet('/api/lab-report?username=' + encodeURIComponent(user.username) + '&_=' + Date.now());
      const list = data.reports || [];
      if (!list.length) {
        historyBox.innerHTML = '<p class="empty-tip">还没有解读过报告单</p>';
        return;
      }
      historyBox.innerHTML = list.map((report) => `
        <button class="lab-hist" type="button" data-open="${report.id}">
          <span class="lab-dot ${report.level === 'red' ? 'red' : (report.level === 'green' ? 'green' : 'amber')}"></span>
          <span class="lab-hist-main">
            <span class="lab-hist-head">${esc(String(report.createdAt || '').slice(5, 16))}
              <em>${report.abnormalCount || 0} 项异常</em></span>
            <span class="lab-hist-text">${esc(report.summary || '')}</span>
          </span>
          <span class="lab-hist-del" data-del="${report.id}" role="button" aria-label="删除">删除</span>
        </button>`).join('');
      historyBox.querySelectorAll('[data-open]').forEach((item) => {
        item.addEventListener('click', async (event) => {
          if (event.target.closest('[data-del]')) return;
          const id = item.dataset.open;
          say('正在打开历史报告…');
          try {
            const detail = await apiGet('/api/lab-report?username=' + encodeURIComponent(user.username) + '&id=' + id);
            if (!detail.success) {
              say(detail.message || '打开失败', 'error');
              return;
            }
            renderLabReport(resultBox, detail);
            bindResultActions();
            say('已打开 ' + String(detail.createdAt || '').slice(0, 16) + ' 的报告');
            resultBox.scrollIntoView({ behavior: 'smooth', block: 'start' });
          } catch (error) {
            say(error.message, 'error');
          }
        });
      });
      historyBox.querySelectorAll('[data-del]').forEach((btn) => {
        btn.addEventListener('click', async (event) => {
          event.stopPropagation();
          if (!window.confirm('删除这条历史报告？')) return;
          try {
            await apiPost('/api/lab-report', { action: 'delete', id: btn.dataset.del, username: user.username });
            loadHistory();
          } catch (error) {
            window.alert(error.message);
          }
        });
      });
    } catch (error) {
      historyBox.innerHTML = '<p class="empty-tip">历史记录加载失败</p>';
    }
  }

  loadHistory();
}

/* ---------------------- 智能医生（对话） ---------------------- */
function chatKey() {
  const user = getUser();
  return 'aiDoctorChat:' + (user ? user.username : 'guest');
}

function loadChatHistory() {
  try { return JSON.parse(localStorage.getItem(chatKey()) || '[]'); } catch (e) { return []; }
}

function saveChatHistory(list) {
  localStorage.setItem(chatKey(), JSON.stringify(list.slice(-60)));
}

function initChat() {
  setupChrome('doctor');
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <div class="chat-panel">
      <div class="chat-head">
        <div class="doc-avatar">${doctorAvatarSvg()}</div>
        <div>
          <h3>智能在线医生 <span class="online">在线</span></h3>
          <p>常见病先聊，判断不了会转真人医生</p>
        </div>
        <div class="chat-mode">
          <button class="active" data-mode="ai" type="button">AI 智能医生</button>
          <button data-mode="real" type="button">真人医生</button>
        </div>
      </div>
      <div class="chat-body" id="chatBody"></div>
      <div class="chat-input-area" id="inputArea">
        <div class="chat-quick" id="chatQuick">
          <button type="button" data-q="我最近发热，体温 38.5℃，还有咳嗽和咽痛">发热咳嗽</button>
          <button type="button" data-q="我这两天腹痛、腹泻，一天三四次">腹痛腹泻</button>
          <button type="button" data-q="我最近总是头痛、睡不好，白天没精神">头痛失眠</button>
          <button type="button" data-q="我身上起了红疹，有点痒">皮肤起疹</button>
          <button type="button" data-q="我有点鼻塞、打喷嚏，可能是过敏">鼻塞过敏</button>
        </div>
        <div class="box">
          <textarea id="chatInput" placeholder="描述症状、持续时间、服药情况，说得越具体建议越准…"></textarea>
        </div>
        <div class="chat-actions">
          <button class="btn btn-ghost" id="clearChat" type="button">清空对话</button>
          <button class="btn btn-primary" id="sendChat" type="button">发送</button>
        </div>
      </div>
      <div class="chat-input-area" id="realArea" hidden>
        <div class="box">
          <textarea id="consultCondition" placeholder="请描述症状、持续时间等，系统会按科室自动分配给真人医生"></textarea>
        </div>
        <div class="chat-actions">
          <span class="form-msg" id="consultMsg"></span>
          <button class="btn btn-primary" id="consultSubmit" type="button">提交接诊申请</button>
        </div>
        <div class="record-list" id="myConsultList" style="margin-top:16px"></div>
      </div>
    </div>`;

  const body = document.getElementById('chatBody');
  const input = document.getElementById('chatInput');
  let history = loadChatHistory();

  const greeting = [
    { role: 'bot', text: '哪里不舒服？说个大概位置、多久了，就够了。' },
    { role: 'bot', text: '说不清也没关系，我一条一条问。' },
  ];

  const TRIAGE_META = {
    red: { cls: 'tri-red', label: '建议立即就医' },
    amber: { cls: 'tri-amber', label: '建议近期门诊' },
    green: { cls: 'tri-green', label: '可先居家观察' },
  };

  /* 结构化问诊卡片：分诊等级 + 可能原因 + 建议 + 就医信号 + 追问 */
  const consultCard = (card) => {
    if (!card) return '';
    const tri = TRIAGE_META[card.triage] || TRIAGE_META.green;
    const join = (arr, fn) => (Array.isArray(arr) && arr.length ? arr.map(fn).join('') : '');
    return `<div class="ai-card">
      <div class="ai-triage ${tri.cls}"><b>${esc(card.triageLabel || tri.label)}</b>${card.suggestDoctor ? '<em>建议同时咨询真人医生</em>' : ''}</div>
      ${card.summary ? `<p class="ai-summary">${esc(card.summary)}</p>` : ''}
      ${join(card.possible, (x) => `<span class="ai-chip">${esc(x)}</span>`).length
        ? `<div class="ai-row"><span class="ai-label">可能原因</span><div class="ai-chips">${join(card.possible, (x) => `<span>${esc(x)}</span>`)}</div></div>` : ''}
      ${join(card.advice, (x) => `<li>${esc(x)}</li>`).length
        ? `<div class="ai-row"><span class="ai-label">可以这样做</span><ol class="ai-list">${join(card.advice, (x) => `<li>${esc(x)}</li>`)}</ol></div>` : ''}
      ${join(card.redFlags, (x) => `<li>${esc(x)}</li>`).length
        ? `<div class="ai-row ai-danger"><span class="ai-label">就医信号</span><ul class="ai-list">${join(card.redFlags, (x) => `<li>${esc(x)}</li>`)}</ul></div>` : ''}
      ${join(card.medicines, (x) => `<span>${esc(x)}</span>`).length
        ? `<div class="ai-row"><span class="ai-label">可参考用药</span><div class="ai-chips med">${join(card.medicines, (x) => `<span>${esc(x)}</span>`)}</div></div>` : ''}
      ${card.cabinetHint ? `<p class="ai-hint">${icon('i-pulse')}${esc(card.cabinetHint)}</p>` : ''}
      ${join(card.followups, (x) => `<button type="button" class="ai-follow" data-followup="${esc(x)}">${esc(x)}</button>`).length
        ? `<div class="ai-followups">${join(card.followups, (x) => `<button type="button" class="ai-follow" data-followup="${esc(x)}">${esc(x)}</button>`)}</div>` : ''}
      <div class="ai-actions">
        <button type="button" class="ai-link" data-to-real="1">转真人医生 →</button>
        <a class="ai-link" href="cabinet.html">打开家庭药箱 →</a>
      </div>
    </div>`;
  };

  const bubble = (msg) => {
    if (msg.role === 'user') {
      return `<div class="msg user">
        <div class="bubble-avatar">我</div>
        <div class="msg-text">
          <span class="time">${esc(msg.time || nowText())}</span>
          <div class="bubble">${esc(msg.text)}</div>
        </div>
      </div>`;
    }
    if (msg.card) {
      return `<div class="msg bot has-card">
        <div class="bubble-avatar">${doctorAvatarSvg()}</div>
        <div class="msg-text">
          <span class="time">${esc(msg.time || nowText())}</span>
          ${consultCard(msg.card)}
          <div class="msg-source">—— 来源：${esc(msg.source || 'AI 健康顾问')} · 仅供参考，不构成医疗诊断</div>
        </div>
      </div>`;
    }
    return `<div class="msg bot">
      <div class="bubble-avatar">${doctorAvatarSvg()}</div>
      <div class="msg-text">
        <span class="time">${esc(msg.time || nowText())}</span>
        <div class="bubble">${esc(msg.text)}${msg.source ? `<div class="msg-source">—— 来源：${esc(msg.source)} · 仅供参考，不构成医疗诊断</div>` : ''}</div>
      </div>
    </div>`;
  };

  const paint = () => {
    const list = history.length ? history : greeting;
    body.innerHTML = list.map(bubble).join('');
    body.scrollTop = body.scrollHeight;
  };
  paint();

  const send = async (preset) => {
    const text = (typeof preset === 'string' ? preset : input.value).trim();
    if (!text) return;
    // 上下文：最近 8 条已完成的消息，让 AI 记得前面说过什么
    const context = history
      .filter((m) => !m.thinking)
      .slice(-8)
      .map((m) => ({ role: m.role, text: m.text || (m.card && m.card.summary) || '' }))
      .filter((m) => m.text);

    history.push({ role: 'user', text, time: nowText() });
    input.value = '';
    paint();

    const thinking = { role: 'bot', text: '正在分析你的情况…', time: nowText(), thinking: true };
    history.push(thinking);
    paint();

    const user = getUser() || {};
    try {
      const data = await apiPost('/api/ai-consult', {
        question: text,
        basicQuestions: [],
        symptoms: [],
        history: context,
        username: user.username || '',
      });
      delete thinking.thinking;
      thinking.text = data.answer || data.summary || '抱歉，暂时没有给出建议。';
      thinking.source = data.source || 'AI 健康顾问';
      if (data.triage) {
        thinking.card = {
          triage: data.triage,
          triageLabel: data.triageLabel,
          summary: data.summary,
          possible: data.possible,
          advice: data.advice,
          redFlags: data.redFlags,
          followups: data.followups,
          medicines: data.medicines,
          cabinetHint: data.cabinetHint,
          suggestDoctor: data.suggestDoctor,
        };
      }
      saveChatHistory(history);
      apiPost('/api/records', {
        username: user.username,
        content: text + (data.summary ? ' → ' + data.summary : ''),
      }).catch(() => {});
    } catch (error) {
      delete thinking.thinking;
      thinking.text = error.message || '出错了，请稍后重试。';
    }
    paint();
  };

  /* 追问 / 转真人 都在气泡里，用事件委托统一处理 */
  body.addEventListener('click', (event) => {
    const follow = event.target.closest('[data-followup]');
    if (follow) {
      send(follow.dataset.followup);
      return;
    }
    if (event.target.closest('[data-to-real]')) {
      setMode('real');
      const lastUser = [...history].reverse().find((m) => m.role === 'user');
      const lastCard = [...history].reverse().find((m) => m.card);
      const box = document.getElementById('consultCondition');
      if (box && !box.value.trim()) {
        const summary = lastCard && lastCard.card ? lastCard.card.summary : '';
        box.value = [lastUser ? lastUser.text : '', summary ? '（AI 初步判断：' + summary + '）' : '']
          .filter(Boolean).join('\n');
      }
      box && box.focus();
    }
  });

  document.querySelectorAll('#chatQuick [data-q]').forEach((btn) => {
    btn.addEventListener('click', () => send(btn.dataset.q));
  });

  document.getElementById('sendChat').addEventListener('click', () => send());
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      send();
    }
  });

  document.getElementById('clearChat').addEventListener('click', () => {
    history = [];
    localStorage.removeItem(chatKey());
    paint();
  });

  function setMode(mode) {
    document.querySelectorAll('.chat-mode button').forEach((b) => b.classList.toggle('active', b.dataset.mode === mode));
    document.getElementById('inputArea').hidden = mode !== 'ai';
    document.getElementById('realArea').hidden = mode !== 'real';
    if (mode === 'real') loadMyConsultations();
  }

  document.querySelectorAll('.chat-mode button').forEach((btn) => {
    btn.addEventListener('click', () => setMode(btn.dataset.mode));
  });

  const submitBtn = document.getElementById('consultSubmit');
  submitBtn.addEventListener('click', async () => {
    const user = getUser();
    const condition = document.getElementById('consultCondition').value.trim();
    const msg = document.getElementById('consultMsg');
    if (!condition) {
      msg.textContent = '请填写症状描述';
      msg.classList.add('error');
      return;
    }
    try {
      const result = await apiPost('/api/consult', { patient: user.username, condition });
      msg.textContent = result.message || '已提交，请等待医生回复';
      msg.classList.remove('error');
      document.getElementById('consultCondition').value = '';
      loadMyConsultations();
    } catch (error) {
      msg.textContent = error.message;
      msg.classList.add('error');
    }
  });

  async function loadMyConsultations() {
    const list = document.getElementById('myConsultList');
    list.innerHTML = '';
    try {
      const data = await apiGet('/api/consultations?username=' + encodeURIComponent(getUser().username));
      const items = data.consultations || [];
      list.innerHTML = items.length ? items.slice(0, 5).map((c) => `
        <div class="consult-card">
          <div class="consult-head">
            <span class="consult-badge">${esc(c.specialty)}</span>
            <span class="consult-status">${esc(c.status)}</span>
          </div>
          <div class="consult-meta">医生：${esc(c.doctorName)} · ${esc(c.createdAt)}</div>
          <div class="consult-condition">${esc(c.condition)}</div>
          ${c.status === '已回复'
            ? `<div class="consult-replied"><strong>医生回复：</strong>${esc(c.reply)}</div>`
            : '<div class="consult-meta" style="margin-top:8px">等待医生回复…</div>'}
        </div>`).join('') : '<p class="empty-tip">暂无接诊记录</p>';
    } catch (error) {
      list.innerHTML = '<p class="empty-tip">接诊记录加载失败</p>';
    }
  }
}

/* ---------------------- 我的资料 ---------------------- */
let editingMember = 0;

function renderMemberCards() {
  const box = document.getElementById('memberGrid');
  if (!box) return;
  box.innerHTML = MEMBERS.map((m) => `
    <button class="member-card${m.id === editingMember ? ' active' : ''}" type="button" data-mid="${m.id}">
      <span class="mc-avatar">${esc(String(m.name).slice(0, 1))}</span>
      <span class="mc-body">
        <b>${esc(m.name)}${m.isDefault ? '<i class="mc-def">默认</i>' : ''}</b>
        <em>${esc(m.relation)}${memberAgeText(m) ? ' · ' + memberAgeText(m) : ''}${m.weightKg ? ' · ' + esc(m.weightKg) + ' kg' : ''}</em>
        <span class="mc-tags">${m.allergies ? `<i class="mc-tag warn">过敏 ${esc(m.allergies)}</i>` : ''}${m.chronic ? `<i class="mc-tag">${esc(m.chronic)}</i>` : ''}</span>
      </span>
    </button>`).join('')
    + `<button class="member-card add" type="button" id="addMember">
         <span class="mc-avatar">＋</span>
         <span class="mc-body"><b>添加成员</b><em>老人、孩子各自建档</em></span>
       </button>`;
  box.querySelectorAll('[data-mid]').forEach((card) => {
    card.addEventListener('click', () => fillMemberForm(Number(card.dataset.mid)));
  });
  const add = document.getElementById('addMember');
  if (add) add.addEventListener('click', () => fillMemberForm(0));
}

function fillMemberForm(id) {
  const form = document.getElementById('profileForm');
  if (!form) return;
  editingMember = id;
  const m = MEMBERS.find((x) => x.id === id) || null;
  const title = document.getElementById('memberFormTitle');
  const delBtn = document.getElementById('memberDelete');
  const defBtn = document.getElementById('memberDefault');
  if (title) title.textContent = m ? m.name + ' · 健康档案' : '新增家庭成员';
  if (delBtn) delBtn.style.display = m ? '' : 'none';
  if (defBtn) defBtn.style.display = m && !m.isDefault ? '' : 'none';

  const set = (name, value) => {
    const field = form.elements[name];
    if (field) field.value = value == null ? '' : value;
  };
  set('memberId', m ? m.id : '');
  set('name', m ? m.name : '');
  set('relation', m ? m.relation : '子女');
  set('gender', m ? m.gender : '');
  set('birthDate', m ? m.birthDate : '');
  set('heightCm', m ? m.heightCm : '');
  set('weightKg', m ? m.weightKg : '');
  set('bloodType', m ? m.bloodType : '');
  set('pregnant', m && m.pregnant ? m.pregnant : '无');
  set('allergies', m ? m.allergies : '');
  set('chronic', m ? m.chronic : '');
  set('egfr', m ? m.egfr : '');
  set('alt', m ? m.alt : '');
  const msg = document.getElementById('profileMsg');
  if (msg) msg.textContent = '';
  renderMemberCards();
}

async function initProfile() {
  setupChrome('profile');
  const user = getUser();
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">家庭成员</h1>
    <p class="panel-desc">同一个账号下可以建多份档案，药箱、服药提醒、打卡记录和化验单都按成员分开保存。切到谁，看到的就是谁的。</p>
    <div class="member-grid" id="memberGrid"><p class="empty-tip">加载中…</p></div>

    <div class="member-editor">
      <div class="me-head">
        <h3 id="memberFormTitle">健康档案</h3>
        <span class="me-hint">生日用来算年龄和儿童剂量，比直接填年龄准</span>
      </div>
      <form id="profileForm" class="form-grid">
        <input type="hidden" name="memberId" />
        <div class="field"><label for="name">姓名</label><input id="name" name="name" placeholder="成员姓名" /></div>
        <div class="field"><label for="relation">与我的关系</label>
          <select id="relation" name="relation"><option>本人</option><option>配偶</option><option>父亲</option><option>母亲</option><option>子女</option><option>其他</option></select>
        </div>
        <div class="field"><label for="gender">性别</label>
          <select id="gender" name="gender"><option value="">请选择</option><option>男</option><option>女</option></select>
        </div>
        <div class="field"><label for="birthDate">出生日期</label><input id="birthDate" name="birthDate" type="date" /></div>
        <div class="field"><label for="heightCm">身高（cm）</label><input id="heightCm" name="heightCm" type="number" min="30" max="250" step="0.1" placeholder="例如 172" /></div>
        <div class="field"><label for="weightKg">体重（kg）</label><input id="weightKg" name="weightKg" type="number" min="1" max="300" step="0.1" placeholder="儿童按体重算剂量" /></div>
        <div class="field"><label for="bloodType">血型</label>
          <select id="bloodType" name="bloodType"><option value="">请选择</option><option>A</option><option>B</option><option>AB</option><option>O</option><option>未知</option></select>
        </div>
        <div class="field"><label for="pregnant">孕哺状态</label>
          <select id="pregnant" name="pregnant"><option>无</option><option>备孕</option><option>孕早期</option><option>孕中期</option><option>孕晚期</option><option>哺乳期</option></select>
        </div>
        <div class="field full"><label for="allergies">过敏史</label><textarea id="allergies" name="allergies" placeholder="例如：青霉素过敏、磺胺类过敏。药箱加药时会按这里比对"></textarea></div>
        <div class="field full"><label for="chronic">基础病</label><textarea id="chronic" name="chronic" placeholder="例如：高血压、2 型糖尿病、慢性肾病 3 期。加药时会检查是否冲突"></textarea></div>
        <div class="field"><label for="egfr">最近 eGFR</label><input id="egfr" name="egfr" type="number" min="0" max="200" step="0.1" placeholder="可留空" /></div>
        <div class="field"><label for="alt">最近 ALT</label><input id="alt" name="alt" type="number" min="0" max="5000" step="0.1" placeholder="可留空" /></div>
        <div class="form-footer">
          <button class="btn btn-primary" type="submit">保存档案</button>
          <button class="btn btn-outline" type="button" id="memberDefault" style="display:none">设为默认</button>
          <button class="btn btn-ghost danger" type="button" id="memberDelete" style="display:none">删除成员</button>
          <span class="form-msg" id="profileMsg"></span>
        </div>
      </form>
    </div>

    <div class="member-editor account-card">
      <div class="me-head"><h3>账号资料</h3><span class="me-hint">电话和邮箱不属于某个人，整账号共用</span></div>
      <form id="accountForm" class="form-grid">
        <div class="field"><label for="phone">手机号</label><input id="phone" name="phone" placeholder="用于医生回访联系" /></div>
        <div class="field"><label for="email">邮箱</label><input id="email" name="email" type="email" placeholder="用于接收健康报告" /></div>
        <div class="field full"><label for="notes">备注</label><textarea id="notes" name="notes" placeholder="其他需要记录的信息"></textarea></div>
        <div class="form-footer">
          <button class="btn btn-outline" type="submit">保存账号资料</button>
          <span class="form-msg" id="accountMsg"></span>
        </div>
      </form>
    </div>

    <h3 class="section-heading">最近问诊记录</h3>
    <div class="record-list" id="profileRecords"><p class="empty-tip">加载中…</p></div>`;

  renderSidebar('profile');
  renderMemberCards();
  fillMemberForm(CURRENT_MEMBER || (MEMBERS[0] ? MEMBERS[0].id : 0));

  const form = document.getElementById('profileForm');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const msg = document.getElementById('profileMsg');
    const payload = { action: 'save', username: user.username };
    for (const field of form.elements) {
      if (field.name) payload[field.name] = String(field.value || '').trim();
    }
    if (payload.memberId) payload.id = Number(payload.memberId);
    try {
      const res = await apiPost('/api/members', payload);
      msg.textContent = res.message || '已保存';
      msg.classList.remove('error');
      await loadMembers();
      fillMemberForm(res.id || Number(payload.memberId) || CURRENT_MEMBER);
      if (!CURRENT_MEMBER && res.id) CURRENT_MEMBER = res.id;
    } catch (error) {
      msg.textContent = error.message;
      msg.classList.add('error');
    }
  });

  const delBtn = document.getElementById('memberDelete');
  if (delBtn) {
    delBtn.addEventListener('click', async () => {
      const m = MEMBERS.find((x) => x.id === editingMember);
      if (!m) return;
      const typed = window.prompt(`删除「${m.name}」会一并删掉这个人的药箱、服药提醒、打卡记录和化验单，无法恢复。\n请输入姓名确认：`, '');
      if (typed == null) return;
      if (typed.trim() !== m.name) {
        document.getElementById('profileMsg').textContent = '姓名不一致，已取消';
        return;
      }
      try {
        await apiPost('/api/members', { action: 'delete', username: user.username, id: m.id, confirmName: typed.trim() });
        await loadMembers();
        fillMemberForm(MEMBERS[0] ? MEMBERS[0].id : 0);
        document.getElementById('profileMsg').textContent = '已删除';
      } catch (error) {
        document.getElementById('profileMsg').textContent = error.message;
      }
    });
  }

  const defBtn = document.getElementById('memberDefault');
  if (defBtn) {
    defBtn.addEventListener('click', async () => {
      if (!editingMember) return;
      try {
        await apiPost('/api/members', { action: 'default', username: user.username, id: editingMember });
        await loadMembers();
        fillMemberForm(editingMember);
      } catch (error) {
        document.getElementById('profileMsg').textContent = error.message;
      }
    });
  }

  try {
    await loadProfile();
    const account = document.getElementById('accountForm');
    if (account && state.profile) {
      ['phone', 'email', 'notes'].forEach((k) => {
        if (account.elements[k] && state.profile[k]) account.elements[k].value = state.profile[k];
      });
    }
    if (account) {
      account.addEventListener('submit', async (event) => {
        event.preventDefault();
        const msg = document.getElementById('accountMsg');
        const payload = { username: user.username };
        ['phone', 'email', 'notes'].forEach((k) => { payload[k] = String(account.elements[k].value || '').trim(); });
        try {
          await apiPost('/api/profile', payload);
          msg.textContent = '已保存';
          msg.classList.remove('error');
        } catch (error) {
          msg.textContent = error.message;
          msg.classList.add('error');
        }
      });
    }
  } catch (e) {
    // 账号资料读取失败不影响主流程
  }

  try {
    const data = await apiGet('/api/records?username=' + encodeURIComponent(user.username));
    const items = (data.records || []).slice(0, 4);
    document.getElementById('profileRecords').innerHTML = items.length
      ? items.map((r) => `<div class="record-item"><div class="record-time">${esc(r.createdAt)}</div><div class="record-content">${esc(r.content)}</div></div>`).join('')
        + '<a class="btn btn-outline btn-sm" href="records.html" style="justify-self:start">查看全部记录</a>'
      : '<p class="empty-tip">暂无问诊记录</p>';
  } catch (e) {
    document.getElementById('profileRecords').innerHTML = '<p class="empty-tip">记录加载失败</p>';
  }
}

/* ---------------------- 问诊记录 ---------------------- */
async function initRecords() {
  setupChrome('records');
  const user = getUser();
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">问诊记录</h1>
    <div class="page-visual pv-warm pv-left">
      <img src="images/consult-visual.jpg" alt="医生与患者面对面沟通" />
      <div class="pv-text">
        <strong>每次问诊都留痕</strong>
        <span>和 AI、和医生的对话都留着，复诊时能翻到当时怎么说的。</span>
      </div>
    </div>
    <div class="record-list" id="recordsList"><p class="empty-tip">加载中…</p></div>`;
  try {
    const data = await apiGet('/api/records?username=' + encodeURIComponent(user.username));
    const items = data.records || [];
    document.getElementById('recordsList').innerHTML = items.length
      ? items.map((r) => `<div class="record-item"><div class="record-time">${esc(r.createdAt)}</div><div class="record-content">${esc(r.content)}</div></div>`).join('')
      : '<p class="empty-tip">暂无问诊记录，去 <a href="doctor.html" style="color:#1a63a8">智能医生</a> 咨询一下吧</p>';
  } catch (e) {
    document.getElementById('recordsList').innerHTML = '<p class="empty-tip">记录加载失败</p>';
  }
}

/* ---------------------- 家庭药箱 / 用药提醒 ---------------------- */
const CAB_TAG = { '正常': 'tag-green', '即将过期': 'tag-amber', '已过期': 'tag-red' };

async function loadCabinet(username) {
  const data = await apiGet('/api/cabinet?username=' + encodeURIComponent(username) + '&_=' + Date.now());
  return data.items || [];
}

async function loadPlans(username) {
  return apiGet('/api/plans?username=' + encodeURIComponent(username) + '&_=' + Date.now());
}

/** 首页「今日用药」小卡：统计 + 前三条待服 */
/* 页面重新可见 / 从后台切回时执行一次同步：医生端改了用药方案后，患者端不用手动刷新 */
function onPageResume(callback) {
  let busy = false;
  const run = async () => {
    if (document.visibilityState !== 'visible' || busy) return;
    busy = true;
    try { await callback(); } catch (e) { /* 同步失败时忽略，保留旧数据 */ }
    finally { busy = false; }
  };
  document.addEventListener('visibilitychange', run);
  window.addEventListener('focus', run);
  window.addEventListener('pageshow', (event) => { if (event.persisted) run(); });
}

async function renderHomeDoses() {
  const host = document.getElementById('homeDoses');
  if (!host) return;
  const user = getUser();
  if (!user || !user.username) { host.innerHTML = ''; return; }
  try {
    const data = await loadPlans(user.username);
    const today = data.today || {};
    const doses = data.doses || [];
    const pending = doses.filter((d) => !d.taken).slice(0, 3);
    host.innerHTML = `
      <div class="db-title">
        <span class="chip-ok">今日用药</span>
        <strong>${today.total ? `待服 ${today.pending} 次 / 共 ${today.total} 次` : '今天还没有用药安排'}</strong>
      </div>
      <div class="db-list">
        ${pending.length
          ? pending.map((d) => `<span class="db-item"><em>${esc(d.slot)}</em>${esc(d.medicineName)}<b>${esc(d.dosage || '按医嘱')}</b></span>`).join('')
          : `<span class="db-empty">${today.total ? '今天的药都吃完啦，注意多喝水。' : '把在吃的药录进药箱，就能按时提醒你。'}</span>`}
      </div>
      <div class="db-rate">
        <span>今日完成率 ${today.rate || 0}%</span>
        <div class="dose-progress"><i style="width:${today.rate || 0}%"></i></div>
      </div>
      <a class="home-more" href="cabinet.html">管理药箱 →</a>`;
  } catch (e) {
    host.innerHTML = '';
  }
}

async function initCabinet() {
  setupChrome('cabinet');
  const user = getUser();
  const username = user ? user.username : '';
  const panel = document.getElementById('mainPanel');

  panel.innerHTML = `
    <h1 class="panel-title">家庭药箱</h1>
    <div class="page-visual pv-warm">
      <img src="images/cabinet-visual.jpg" alt="家中木柜里常备的药品与急救用品" />
      <div class="pv-text">
        <strong>先看效期，再查用药冲突</strong>
        <span>过期、即将过期与重复成分会被单独标出，吃药前先过一遍安全检查。</span>
      </div>
    </div>
    <div class="stat-row" id="cabStats"></div>
    <div class="sf-card" id="safetyCard">
      <div class="sf-loading"><span class="sf-spin"></span>正在检查用药安全…</div>
    </div>
    <div class="cab-block">
      <div class="block-head">
        <h3 class="section-heading">今日用药</h3>
        <span class="block-hint" id="todayHint"></span>
      </div>
      <div class="dose-list" id="todayDoses"><p class="empty-tip">加载中…</p></div>
    </div>
    <div class="cab-grid">
      <div class="cab-block">
        <div class="block-head">
          <h3 class="section-heading">药箱药品</h3>
          <a class="btn btn-ghost btn-sm" href="lab-report.html">${icon('i-clipboard')}化验单解读</a>
          <button class="btn btn-primary btn-sm" id="cabAddBtn" type="button">${icon('i-plus')}添加药品</button>
        </div>
        <form class="cab-form" id="cabForm" hidden>
          <div class="form-grid">
            <div class="field"><label for="cabName">药品名称</label><input id="cabName" placeholder="例：对乙酰氨基酚缓释片" /></div>
            <div class="field"><label for="cabSpec">规格</label><input id="cabSpec" placeholder="例：6 片/盒" /></div>
            <div class="field"><label for="cabQty">数量</label><input id="cabQty" type="number" min="0" value="1" /></div>
            <div class="field"><label for="cabExpire">有效期至</label><input id="cabExpire" type="date" /></div>
            <div class="field full"><label for="cabNote">备注</label><input id="cabNote" placeholder="例：发烧时服用，饭后吃" /></div>
            <div class="field full"><label for="cabDose">单次剂量<span class="opt">（选填）</span></label><input id="cabDose" placeholder="如：10 mL / 1 袋 / 半片" /></div>
            <div class="dose-hint" id="cabDoseHint" hidden></div>
            <div class="form-footer">
              <button class="btn btn-primary" type="submit">保存到药箱</button>
              <button class="btn btn-ghost" type="button" id="cabCancel">取消</button>
              <span class="form-msg" id="cabMsg"></span>
            </div>
            <div class="cab-check" id="cabCheck" hidden></div>
          </div>
        </form>
        <div class="cab-list" id="cabinetList"><p class="empty-tip">加载中…</p></div>
      </div>
      <div class="cab-block">
        <div class="block-head">
          <h3 class="section-heading">用药提醒</h3>
          <button class="btn btn-outline btn-sm" id="planAddBtn" type="button">${icon('i-plus')}新建提醒</button>
        </div>
        <form class="cab-form" id="planForm" hidden>
          <div class="form-grid">
            <div class="field full"><label for="planName">药品</label><input id="planName" list="cabOptions" placeholder="可从药箱选，也可直接输入" /></div>
            <datalist id="cabOptions"></datalist>
            <div class="field"><label for="planDosage">每次剂量</label><input id="planDosage" placeholder="例：1 片 / 10ml" /></div>
            <div class="dose-hint" id="planDoseHint" hidden></div>
            <div class="field"><label for="planEnd">结束日期</label><input id="planEnd" type="date" /></div>
            <div class="field full"><label for="planTimes">提醒时间</label><input id="planTimes" placeholder="08:00,12:00,20:00" value="08:00,20:00" />
              <div class="time-chips">
                <button type="button" class="chip-btn" data-times="08:00,20:00">一日两次</button>
                <button type="button" class="chip-btn" data-times="08:00,12:00,20:00">一日三次</button>
                <button type="button" class="chip-btn" data-times="21:30">睡前一次</button>
              </div>
            </div>
            <div class="form-footer">
              <button class="btn btn-primary" type="submit">保存提醒</button>
              <button class="btn btn-ghost" type="button" id="planCancel">取消</button>
              <span class="form-msg" id="planMsg"></span>
            </div>
          </div>
        </form>
        <div class="plan-list" id="planList"><p class="empty-tip">加载中…</p></div>
      </div>
    </div>`;

  const $ = (id) => document.getElementById(id);
  let cabinetItems = [];
  let plans = [];
  let doses = [];
  let summary = {};

  function renderStats() {
    const expiring = cabinetItems.filter((i) => i.status !== '正常').length;
    $('cabStats').innerHTML = `
      <div class="stat-card"><span>药箱药品</span><strong>${cabinetItems.length}<em>种</em></strong></div>
      <div class="stat-card"><span>今日待服</span><strong>${summary.pending || 0}<em>次</em></strong></div>
      <div class="stat-card"><span>今日完成率</span><strong>${summary.rate || 0}<em>%</em></strong></div>
      <div class="stat-card ${expiring ? 'warn' : ''}"><span>需关注</span><strong>${expiring}<em>种</em></strong></div>`;
  }

  /* ---------------- 用药安全检查 ---------------- */
  const SF_META = {
    red: { label: '存在风险', color: '#e2615f', tip: '有需要马上处理的问题' },
    amber: { label: '需注意', color: '#e0a03a', tip: '有需要留意的用药问题' },
    green: { label: '用药安全', color: '#34c08b', tip: '未发现明显冲突' },
    none: { label: '待评估', color: '#9aa9b5', tip: '先录入在吃的药' },
  };

  function renderSafety(data) {
    const box = $('safetyCard');
    if (!data || data.success === false) {
      box.className = 'sf-card';
      box.innerHTML = '<div class="sf-empty">用药安全检查暂时不可用，稍后可在药箱页重新检查</div>';
      return;
    }
    const meta = SF_META[data.level] || SF_META.none;
    box.className = `sf-card level-${data.level || 'none'}`;

    const score = Number(data.score || 0);
    const dash = 201;
    const offset = dash - (dash * score) / 100;
    const profile = data.profile || {};
    const combined = []
      .concat(profile.chronic || [])
      .concat((profile.allergies || []).map((a) => `${a}过敏`));
    if (profile.age) combined.unshift(`${profile.age} 岁`);

    const head = `
      <div class="sf-main">
        <div class="sf-ring" style="--sf-color:${meta.color}">
          <svg viewBox="0 0 76 76" aria-hidden="true">
            <circle class="sf-track" cx="38" cy="38" r="32"></circle>
            <circle class="sf-bar" cx="38" cy="38" r="32"
                    stroke-dasharray="${dash}" stroke-dashoffset="${offset}"></circle>
          </svg>
          <div class="sf-score"><strong>${data.empty ? '--' : score}</strong></div>
        </div>
        <div class="sf-summary">
          <div class="sf-head">
            <h3>用药安全检查</h3>
            <span class="sf-badge">${esc(data.levelLabel || meta.label)}</span>
            ${data.aiUsed ? '<span class="sf-ai">AI 复核</span>' : ''}
          </div>
          <p class="sf-text">${esc(data.summary || meta.tip)}</p>
          ${combined.length
            ? `<p class="sf-basis">${icon('i-shield')}已结合：${esc(combined.join(' · '))}</p>`
            : '<p class="sf-basis">' + icon('i-shield') + '健康档案还没有过敏史与慢性病信息，<a href="profile.html">去完善</a>后检查更准</p>'}
        </div>
        <button class="btn btn-outline btn-sm sf-refresh" id="safetyRefresh" type="button">${icon('i-refresh')}重新检查</button>
      </div>`;

    const allIssues = data.issues || [];
    const shown = safetyExpanded ? allIssues : allIssues.slice(0, 3);
    const items = shown.map((issue) => `
      <li class="sf-item ${issue.level === 'red' ? 'red' : 'amber'}">
        <div class="sf-item-head">
          <strong>${esc(issue.title)}</strong>
          ${(issue.drugs || []).length
            ? `<span class="sf-drugs">${issue.drugs.map((d) => `<em>${esc(d)}</em>`).join('')}</span>`
            : ''}
        </div>
        <p class="sf-reason">${esc(issue.reason || '')}</p>
        ${issue.advice ? `<p class="sf-advice">${esc(issue.advice)}</p>` : ''}
      </li>`).join('');

    const more = allIssues.length > 3
      ? `<button class="sf-more" id="sfMore" type="button">${safetyExpanded
        ? '收起'
        : `展开其余 ${allIssues.length - 3} 条问题`}</button>`
      : '';

    const list = allIssues.length
      ? `<ul class="sf-issues">${items}</ul>${more}`
      : (data.empty ? '' : `<div class="sf-clear">${icon('i-shield')}目前没有发现需要处理的用药问题，继续保持</div>`);

    box.innerHTML = head + list;
    $('safetyRefresh').addEventListener('click', () => loadSafety(true));
    const moreBtn = $('sfMore');
    if (moreBtn) {
      moreBtn.addEventListener('click', () => {
        safetyExpanded = !safetyExpanded;
        renderSafety(data);
      });
    }
  }

  let safetyBusy = false;
  let safetyExpanded = false;
  async function loadSafety(force) {
    if (safetyBusy) return;
    safetyBusy = true;
    const box = $('safetyCard');
    if (force) box.innerHTML = '<div class="sf-loading"><span class="sf-spin"></span>正在重新检查用药安全…</div>';
    try {
      const data = await apiGet(`/api/safety?username=${encodeURIComponent(username)}${force ? '&refresh=1' : ''}`);
      renderSafety(data);
    } catch (error) {
      box.className = 'sf-card';
      box.innerHTML = `<div class="sf-empty">用药安全检查暂时不可用：${esc(error.message || '请稍后重试')}</div>`;
    }
    safetyBusy = false;
  }

  function renderDoses() {
    $('todayHint').textContent = summary.total ? `${summary.taken}/${summary.total} 已服用` : '';
    $('todayDoses').innerHTML = doses.length
      ? doses.map((d) => `
        <div class="dose-item ${d.taken ? 'done' : ''}">
          <span class="dose-time">${esc(d.slot)}</span>
          <div class="dose-main"><strong>${d.source === 'doctor' ? '<em class="dose-rx">医嘱</em>' : ''}${esc(d.medicineName)}</strong><span>${esc(d.dosage || '按医嘱服用')}</span></div>
          <button class="btn ${d.taken ? 'btn-outline' : 'btn-primary'} btn-sm" type="button"
                  data-dose="${d.planId}" data-slot="${esc(d.slot)}" data-taken="${d.taken ? '1' : '0'}">
            ${d.taken ? '✓ 已服用' : '打卡'}</button>
        </div>`).join('')
      : `<p class="empty-tip">今天没有用药任务，先在右侧新建一条提醒吧</p>`;
  }

  function renderCabinet() {
    $('cabinetList').innerHTML = cabinetItems.length
      ? cabinetItems.map((item) => `
        <div class="cab-item">
          <div class="cab-main">
            <div class="cab-title">
              <strong>${esc(item.medicineName)}</strong>
              <span class="tag ${CAB_TAG[item.status] || 'tag-green'}">${esc(item.status)}${item.daysLeft != null && item.daysLeft >= 0 && item.status !== '正常' ? ` ${item.daysLeft} 天` : ''}</span>
            </div>
            <div class="cab-meta">
              ${item.spec ? esc(item.spec) + ' · ' : ''}数量 ${item.quantity}
              ${item.expireDate ? ' · 有效期至 ' + esc(item.expireDate) : ''}
            </div>
            ${item.note ? `<div class="cab-note">${esc(item.note)}</div>` : ''}
          </div>
          <div class="cab-actions">
            <button class="btn btn-outline btn-sm" type="button" data-plan="${esc(item.medicineName)}">${icon('i-refresh')}设提醒</button>
            <button class="btn btn-ghost btn-sm" type="button" data-del="${item.id}">${icon('i-trash')}移除</button>
          </div>
        </div>`).join('')
      : `<p class="empty-tip">药箱还是空的，点右上角「添加药品」把在吃的药录进来</p>`;

    $('cabOptions').innerHTML = cabinetItems.map((i) => `<option value="${esc(i.medicineName)}"></option>`).join('');
  }

  function renderPlans() {
    $('planList').innerHTML = plans.length
      ? plans.map((p) => {
        const fromDoctor = p.source === 'doctor';
        return `
        <div class="plan-item ${p.active ? '' : 'off'}${fromDoctor ? ' from-doctor' : ''}">
          <div class="plan-head">
            <strong>${esc(p.medicineName)}</strong>
            <span class="plan-tags">
              ${fromDoctor ? `<span class="tag tag-blue">医生方案</span>` : ''}
              <span class="tag ${p.active ? 'tag-green' : 'tag-red'}">${p.active ? '提醒中' : '已暂停'}</span>
            </span>
          </div>
          ${fromDoctor && p.doctorName ? `<div class="plan-doctor">${icon('i-pulse')}${esc(p.doctorName)} 下达</div>` : ''}
          <div class="plan-times">${(p.times || []).map((t) => `<span>${esc(t)}</span>`).join('')}</div>
          <div class="plan-meta">${esc(p.dosage || '按医嘱')}${p.endDate ? ' · 至 ' + esc(p.endDate) : ' · 长期'}</div>
          ${fromDoctor && p.note ? `<div class="plan-note">医嘱：${esc(p.note)}</div>` : ''}
          <div class="plan-actions">
            <button class="btn btn-ghost btn-sm" type="button" data-toggle="${p.id}">${p.active ? '暂停' : '恢复'}</button>
            ${fromDoctor
              ? `<span class="plan-lock">请勿自行删除，如需调整请联系医生</span>`
              : `<button class="btn btn-ghost btn-sm" type="button" data-delplan="${p.id}">${icon('i-trash')}删除</button>`}
          </div>
        </div>`;
      }).join('')
      : `<p class="empty-tip">还没有提醒计划，点「新建提醒」设一个吃药时间</p>`;
  }

  async function refresh() {
    try {
      const [items, planData] = await Promise.all([loadCabinet(username), loadPlans(username)]);
      cabinetItems = items;
      plans = planData.plans || [];
      summary = planData.today || {};
      doses = planData.doses || [];
    } catch (e) {
      $('cabinetList').innerHTML = '<p class="empty-tip">药箱加载失败</p>';
    }
    renderStats();
    renderDoses();
    renderCabinet();
    renderPlans();
    loadSafety(false);
  }
  await refresh();
  // 医生端删除/停用方案后，切回本页自动同步，不会残留旧提醒
  onPageResume(refresh);

  /* —— 用药安全辅助：三层校验 + 按体重算剂量 + 超量预警 —— */
  function memberWeight() {
    const m = currentMember();
    return m && m.weightKg ? Number(m.weightKg) : 0;
  }
  function parseDose(str) {
    if (!str) return null;
    const m = String(str).trim().match(/^([0-9]+(?:\.[0-9]+)?)\s*([一-龥a-zA-Z]+)/);
    if (!m) return null;
    return { value: Number(m[1]), unit: m[2] };
  }
  function normUnit(u) {
    if (!u) return '';
    const s = String(u).toLowerCase().replace(/\s/g, '');
    if (s === 'ml') return 'ml';
    if (s === '毫克' || s === 'mg') return 'mg';
    if (s === '克' || s === 'g') return 'g';
    if (s === '袋' || s === '包') return '袋';
    if (s === '片') return '片';
    if (s === '粒') return '粒';
    return s;
  }
  function sameUnit(a, b) { return normUnit(a) && normUnit(a) === normUnit(b); }

  // 返回 {red, amber, msgs:[{level,title,reason,advice}], dose} ，msgs 含三层校验与剂量超量
  // timesCount 可选：传入时一并校验每日总量是否超上限（用于服药提醒）
  async function checkMedicineSafety(name, doseText, timesCount) {
    const msgs = [];
    let red = false, amber = false;
    const push = (level, title, reason, advice) => {
      msgs.push({ level, title, reason, advice });
      if (level === 'red') red = true; else amber = true;
    };
    try {
      const sp = await apiPost('/api/safety/preview', { username, medicine: name, memberId: CURRENT_MEMBER });
      (sp && sp.issues || []).forEach((it) => push(it.level || 'amber', it.title, it.reason, it.advice));
    } catch (e) { /* 校验不可用时放行保存 */ }
    const weight = memberWeight();
    let dose = null;
    if (weight > 0) {
      try {
        const ds = await apiGet('/api/dose-suggest?medicine=' + encodeURIComponent(name) + '&weightKg=' + weight);
        if (ds && ds.found) {
          dose = ds;
          const parsed = parseDose(doseText);
          if (parsed && ds.volUnit && sameUnit(parsed.unit, ds.volUnit) && ds.volMax) {
            if (parsed.value > ds.volMax + 1e-6) {
              push('red', '单次剂量可能超量',
                `按 ${weight}kg 体重，建议每次 ${ds.volMin}–${ds.volMax}${ds.volUnit}（${ds.doseMinMg}–${ds.doseMaxMg}mg），你填的 ${parsed.value}${parsed.unit} 已超过上限。`,
                '儿童肝肾功能尚未成熟，单次超量可能造成肝肾损伤或不良反应，请核对剂量或遵医嘱。');
            } else if (timesCount > 0 && ds.dayMaxVol && parsed.value * timesCount > ds.dayMaxVol + 1e-6) {
              push('red', '每日总量可能超量',
                `按 ${weight}kg 体重，每日最多 ${ds.dayMaxVol}${ds.volUnit}（${ds.dayMaxMg}mg），当前「${parsed.value}${parsed.unit} × ${timesCount} 次」≈ ${(Math.round(parsed.value * timesCount * 10) / 10)}${parsed.unit}，已超过每日上限。`,
                '儿童每日累计超量风险更高，请减少单次剂量或用药次数，或遵医嘱。');
            }
          }
        }
      } catch (e) { /* 剂量查询不可用时忽略 */ }
    }
    return { red, amber, msgs, dose };
  }

  // 把三层校验结论渲染成可确认框
  function renderCheckBox(box, res, onConfirm) {
    if (!res.msgs.length) { box.hidden = true; box.innerHTML = ''; return; }
    const items = res.msgs.map((it) => `
      <li class="ck-item ck-${it.level}">
        <span class="ck-tag">${it.level === 'red' ? '高风险' : '注意'}</span>
        <div class="ck-body">
          <b>${esc(it.title)}</b>
          <p>${esc(it.reason)}</p>
          ${it.advice ? `<p class="ck-advice">建议：${esc(it.advice)}</p>` : ''}
        </div>
      </li>`).join('');
    box.hidden = false;
    box.className = 'cab-check' + (res.red ? ' danger' : '');
    box.innerHTML = `
      <div class="ck-head">${res.red ? '发现需要留意的风险，确认无误再保存' : '有几点提示，确认后保存'}</div>
      <ul class="ck-list">${items}</ul>
      <div class="ck-actions">
        <button class="btn btn-primary btn-sm" id="ckSave" type="button">仍要保存</button>
        <button class="btn btn-ghost btn-sm" id="ckBack" type="button">返回修改</button>
      </div>`;
    box.querySelector('#ckSave').addEventListener('click', () => { box.hidden = true; onConfirm(); });
    box.querySelector('#ckBack').addEventListener('click', () => { box.hidden = true; });
  }

  // 实时剂量提示（非阻塞）
  let doseHintTimer = null;
  function liveDoseHint(hintEl, nameEl, doseEl) {
    clearTimeout(doseHintTimer);
    doseHintTimer = setTimeout(async () => {
      const name = nameEl.value.trim();
      const weight = memberWeight();
      if (!name || name.length < 2 || weight <= 0) { hintEl.hidden = true; hintEl.innerHTML = ''; return; }
      try {
        const ds = await apiGet('/api/dose-suggest?medicine=' + encodeURIComponent(name) + '&weightKg=' + weight);
        if (!ds || !ds.found) { hintEl.hidden = true; hintEl.innerHTML = ''; return; }
        const parsed = parseDose(doseEl.value);
        let over = '';
        if (parsed && ds.volUnit && sameUnit(parsed.unit, ds.volUnit) && ds.volMax && parsed.value > ds.volMax + 1e-6) {
          over = `<span class="dh-over">⚠ 当前剂量已超上限 ${ds.volMax}${ds.volUnit}</span>`;
        }
        hintEl.hidden = false;
        hintEl.innerHTML = `<b>${weight}kg 建议：</b>每次 ${ds.volMin}–${ds.volMax}${ds.volUnit}`
          + `（${ds.doseMinMg}–${ds.doseMaxMg}mg）${ds.maxFreq ? `，每日≤${ds.maxFreq}次` : ''}`
          + (ds.note ? ` · ${esc(ds.note)}` : '') + over;
        hintEl.classList.toggle('warn', !!over);
      } catch (e) { hintEl.hidden = true; }
    }, 400);
  }

  async function doCabSave(payload, msg) {
    try {
      const result = await apiPost('/api/cabinet', payload);
      msg.classList.remove('error');
      msg.textContent = result.message || '已保存';
      $('cabForm').reset();
      $('cabQty').value = 1;
      $('cabDose').value = '';
      $('cabDoseHint').hidden = true;
      $('cabCheck').hidden = true;
      $('cabForm').hidden = true;
      await refresh();
    } catch (error) {
      msg.textContent = error.message;
      msg.classList.add('error');
    }
  }

  async function doPlanSave(payload, msg) {
    try {
      const result = await apiPost('/api/plans', payload);
      msg.classList.remove('error');
      msg.textContent = result.message || '已保存';
      $('planForm').reset();
      $('planTimes').value = '08:00,20:00';
      $('planDoseHint').hidden = true;
      $('planForm').hidden = true;
      await refresh();
    } catch (error) {
      msg.textContent = error.message;
      msg.classList.add('error');
    }
  }

  /* —— 药箱表单 —— */
  $('cabAddBtn').addEventListener('click', () => {
    $('cabForm').hidden = !$('cabForm').hidden;
    if (!$('cabForm').hidden) $('cabName').focus();
  });
  $('cabCancel').addEventListener('click', () => { $('cabForm').hidden = true; });

  $('cabName').addEventListener('input', () => liveDoseHint($('cabDoseHint'), $('cabName'), $('cabDose')));
  $('cabDose').addEventListener('input', () => liveDoseHint($('cabDoseHint'), $('cabName'), $('cabDose')));

  $('cabForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const msg = $('cabMsg');
    const name = $('cabName').value.trim();
    if (!name) { msg.textContent = '请填写药品名称'; msg.classList.add('error'); return; }
    msg.textContent = ''; msg.classList.remove('error');
    const payload = {
      action: 'save', username, medicineName: name,
      spec: $('cabSpec').value.trim(),
      quantity: Number($('cabQty').value || 1),
      expireDate: $('cabExpire').value,
      note: $('cabNote').value.trim(),
      dosage: $('cabDose').value.trim(),
    };
    const res = await checkMedicineSafety(name, payload.dosage);
    if (res.red || res.amber) {
      renderCheckBox($('cabCheck'), res, () => doCabSave(payload, msg));
      return;
    }
    await doCabSave(payload, msg);
  });

  $('cabinetList').addEventListener('click', async (event) => {
    const planBtn = event.target.closest('[data-plan]');
    if (planBtn) {
      $('planForm').hidden = false;
      $('planName').value = planBtn.dataset.plan;
      $('planDosage').focus();
      $('planForm').scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const delBtn = event.target.closest('[data-del]');
    if (delBtn) {
      if (!confirm('从药箱移除该药品？关联的提醒也会一并删除。')) return;
      try {
        await apiPost('/api/cabinet', { action: 'delete', username, id: Number(delBtn.dataset.del) });
        await refresh();
      } catch (error) { alert(error.message); }
    }
  });

  /* —— 提醒表单 —— */
  $('planAddBtn').addEventListener('click', () => {
    $('planForm').hidden = !$('planForm').hidden;
    if (!$('planForm').hidden) $('planName').focus();
  });
  $('planCancel').addEventListener('click', () => { $('planForm').hidden = true; });
  document.querySelectorAll('.time-chips .chip-btn').forEach((btn) => {
    btn.addEventListener('click', () => { $('planTimes').value = btn.dataset.times; });
  });

  $('planName').addEventListener('input', () => liveDoseHint($('planDoseHint'), $('planName'), $('planDosage')));
  $('planDosage').addEventListener('input', () => liveDoseHint($('planDoseHint'), $('planName'), $('planDosage')));

  $('planForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const msg = $('planMsg');
    const name = $('planName').value.trim();
    const times = $('planTimes').value.trim();
    if (!name) { msg.textContent = '请填写药品名称'; msg.classList.add('error'); return; }
    if (!times) { msg.textContent = '请填写提醒时间，如 08:00,20:00'; msg.classList.add('error'); return; }
    msg.textContent = ''; msg.classList.remove('error');
    const payload = {
      action: 'save', username, medicineName: name,
      dosage: $('planDosage').value.trim(),
      times, endDate: $('planEnd').value,
    };
    const timesCount = times.split(/[，,;；\s]+/).filter(Boolean).length;
    const res = await checkMedicineSafety(name, payload.dosage, timesCount);
    if (res.red || res.amber) {
      renderCheckBox(makePlanCheck(), res, () => doPlanSave(payload, msg));
      return;
    }
    await doPlanSave(payload, msg);
  });

  // 提醒表单的确认框（放在表单底部）
  let _planCheckEl = null;
  function makePlanCheck() {
    if (_planCheckEl) return _planCheckEl;
    _planCheckEl = document.createElement('div');
    _planCheckEl.id = 'planCheck';
    _planCheckEl.className = 'cab-check';
    _planCheckEl.hidden = true;
    $('planForm').appendChild(_planCheckEl);
    return _planCheckEl;
  }

  $('todayDoses').addEventListener('click', async (event) => {
    const btn = event.target.closest('[data-dose]');
    if (!btn) return;
    const taken = btn.dataset.taken === '1';
    btn.disabled = true;
    try {
      await apiPost('/api/plans', {
        action: taken ? 'undo' : 'checkin',
        username, planId: Number(btn.dataset.dose), slot: btn.dataset.slot,
      });
      await refresh();
    } catch (error) {
      alert(error.message);
      btn.disabled = false;
    }
  });

  $('planList').addEventListener('click', async (event) => {
    const toggleBtn = event.target.closest('[data-toggle]');
    if (toggleBtn) {
      try {
        await apiPost('/api/plans', { action: 'toggle', username, id: Number(toggleBtn.dataset.toggle) });
        await refresh();
      } catch (error) { alert(error.message); }
      return;
    }
    const delBtn = event.target.closest('[data-delplan]');
    if (delBtn) {
      if (!confirm('删除这条提醒计划？')) return;
      try {
        await apiPost('/api/plans', { action: 'delete', username, id: Number(delBtn.dataset.delplan) });
        await refresh();
      } catch (error) { alert(error.message); }
    }
  });
}

/* ---------------------- 收藏 / 浏览历史 ---------------------- */
async function loadFavorites(type) {
  const user = getUser();
  if (!user || !user.username) return [];
  try {
    const data = await apiGet('/api/favorites?username=' + encodeURIComponent(user.username)
      + (type ? '&type=' + type : '') + '&_=' + Date.now());
    return data.favorites || [];
  } catch (e) { return []; }
}

/** 详情页收藏按钮：进入时回显状态，点击切换 */
async function bindFavoriteButton(btn, type, targetId, title) {
  const user = getUser();
  if (!btn || !user || !user.username) return;
  let on = false;
  const list = await loadFavorites(type);
  on = list.some((item) => item.targetId === targetId);
  const paint = () => {
    btn.classList.toggle('on', on);
    btn.innerHTML = `${icon('i-heart')}${on ? '已收藏' : '收藏'}`;
  };
  paint();
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    try {
      const result = await apiPost('/api/favorites', {
        action: 'toggle', username: user.username, type, targetId, title,
      });
      on = !!result.favorited;
      paint();
    } catch (error) {
      btn.textContent = '操作失败';
      setTimeout(paint, 1200);
    }
    btn.disabled = false;
  });
}

/** 浏览历史：静默上报，不打断页面 */
function recordHistory(type, targetId, title) {
  const user = getUser();
  if (!user || !user.username) return;
  apiPost('/api/history', { username: user.username, type, targetId, title }).catch(() => {});
}

async function initFavorites() {
  setupChrome('favorites');
  const user = getUser();
  const username = user ? user.username : '';
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">我的收藏</h1>
    <div class="fav-tabs" id="favTabs">
      <button class="fav-tab active" type="button" data-tab="all">全部收藏</button>
      <button class="fav-tab" type="button" data-tab="medicine">药品</button>
      <button class="fav-tab" type="button" data-tab="disease">病种</button>
      <button class="fav-tab" type="button" data-tab="history">浏览历史</button>
    </div>
    <div id="favBody"><p class="empty-tip">加载中…</p></div>`;

  let favorites = [];
  let history = [];
  let tab = 'all';

  const href = (item) => item.type === 'medicine'
    ? `medicine-detail.html?name=${encodeURIComponent(item.targetId)}`
    : `disease-detail.html?name=${encodeURIComponent(item.targetId)}`;

  function render() {
    const body = document.getElementById('favBody');
    if (tab === 'history') {
      body.innerHTML = history.length
        ? `<div class="fav-list">${history.map((h) => `
          <div class="fav-item">
            <span class="fav-icon">${icon(h.type === 'medicine' ? 'i-pill' : 'i-list')}</span>
            <div class="fav-main">
              <a href="${href(h)}">${esc(h.title)}</a>
              <span class="fav-meta">${h.type === 'medicine' ? '药品' : '病种'} · 浏览于 ${esc(h.viewedAt)}</span>
            </div>
          </div>`).join('')}</div>`
        : '<p class="empty-tip">还没有浏览记录，去看看药品或病种详情吧</p>';
      return;
    }
    const list = tab === 'all' ? favorites : favorites.filter((f) => f.type === tab);
    body.innerHTML = list.length
      ? `<div class="fav-list">${list.map((f) => `
        <div class="fav-item">
          <span class="fav-icon">${icon(f.type === 'medicine' ? 'i-pill' : 'i-list')}</span>
          <div class="fav-main">
            <a href="${href(f)}">${esc(f.title)}</a>
            <span class="fav-meta">${f.type === 'medicine' ? '药品' : '病种'} · 收藏于 ${esc(f.createdAt)}</span>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" data-remove="${f.id}" data-type="${f.type}" data-target="${esc(f.targetId)}">取消收藏</button>
        </div>`).join('')}</div>`
      : '<p class="empty-tip">还没有收藏，在药品或病种详情页点「收藏」就会出现在这里</p>';
  }

  async function refresh() {
    const [fav, his] = await Promise.all([
      loadFavorites(''),
      apiGet('/api/history?username=' + encodeURIComponent(username) + '&limit=50&_=' + Date.now()).catch(() => ({ items: [] })),
    ]);
    favorites = fav;
    history = his.items || [];
    render();
  }
  const initialTab = new URLSearchParams(location.search).get('tab');
  if (initialTab && ['all', 'medicine', 'disease', 'history'].includes(initialTab)) {
    tab = initialTab;
    document.querySelectorAll('.fav-tab').forEach((el) => el.classList.toggle('active', el.dataset.tab === tab));
  }
  await refresh();

  document.getElementById('favTabs').addEventListener('click', (event) => {
    const btn = event.target.closest('[data-tab]');
    if (!btn) return;
    tab = btn.dataset.tab;
    document.querySelectorAll('.fav-tab').forEach((el) => el.classList.toggle('active', el === btn));
    render();
  });

  document.getElementById('favBody').addEventListener('click', async (event) => {
    const btn = event.target.closest('[data-remove]');
    if (!btn) return;
    btn.disabled = true;
    try {
      await apiPost('/api/favorites', {
        action: 'remove', username, type: btn.dataset.type, targetId: btn.dataset.target,
      });
      await refresh();
    } catch (error) {
      alert(error.message);
      btn.disabled = false;
    }
  });
}

/* ---------------------- 反馈管理 ---------------------- */
async function initFeedback() {
  setupChrome('feedback');
  const user = getUser();
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">反馈管理</h1>
    <div class="feedback-layout">
    <form id="feedbackForm" class="form-grid">
      <div class="field"><label for="type">反馈类型</label>
        <select id="type" name="type"><option>功能建议</option><option>问题反馈</option><option>内容纠错</option><option>其他</option></select>
      </div>
      <div class="field"><label for="contact">联系方式</label><input id="contact" name="contact" placeholder="手机号或邮箱（选填）" /></div>
      <div class="field full"><label for="content">反馈内容</label><textarea id="content" name="content" placeholder="请描述您遇到的问题或想要的功能"></textarea></div>
      <div class="form-footer">
        <button class="btn btn-primary" type="submit">提交反馈</button>
        <span class="form-msg" id="feedbackMsg"></span>
      </div>
    </form>
    <aside class="feedback-aside">
      <figure class="aside-photo">
        <img src="images/consult-visual.jpg" alt="医生与患者面对面沟通" />
        <figcaption>每条反馈都会有人处理</figcaption>
      </figure>
      <div class="aside-card">
        <h5>怎么提更容易被处理</h5>
        <ul>
          <li>写清页面、操作步骤和你期望的结果</li>
          <li>内容纠错请附上原文与正确说法</li>
          <li>留下联系方式，处理完会回复你</li>
        </ul>
      </div>
    </aside>
    </div>
    <h3 class="section-heading" id="myFeedbackTitle">我的反馈</h3>
    <div class="record-list" id="myFeedback"><p class="empty-tip">加载中…</p></div>
    <div id="adminFeedbackBox"></div>`;

  const loadList = async () => {
    try {
      const data = await apiGet('/api/feedback?username=' + encodeURIComponent(user.username));
      const items = data.feedbacks || [];
      document.getElementById('myFeedback').innerHTML = items.length
        ? items.map((f) => `
          <div class="consult-card">
            <div class="consult-head">
              <span class="consult-badge">${esc(f.type)}</span>
              <span class="consult-status">${esc(f.status)}</span>
            </div>
            <div class="consult-meta">${esc(f.createdAt)}${f.contact ? ' · 联系方式：' + esc(f.contact) : ''}</div>
            <div class="consult-condition">${esc(f.content)}</div>
            ${f.reply ? `<div class="consult-replied"><strong>平台回复：</strong>${esc(f.reply)}</div>` : ''}
          </div>`).join('')
        : '<p class="empty-tip">还没有提交过反馈</p>';
    } catch (e) {
      document.getElementById('myFeedback').innerHTML = '<p class="empty-tip">反馈加载失败</p>';
    }
  };
  await loadList();

  document.getElementById('feedbackForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const msg = document.getElementById('feedbackMsg');
    const content = document.getElementById('content').value.trim();
    if (!content) {
      msg.textContent = '请填写反馈内容';
      msg.classList.add('error');
      return;
    }
    try {
      const result = await apiPost('/api/feedback', {
        username: user.username,
        type: document.getElementById('type').value,
        contact: document.getElementById('contact').value.trim(),
        content,
      });
      msg.textContent = result.message || '反馈已提交';
      msg.classList.remove('error');
      document.getElementById('content').value = '';
      loadList();
      if (isAdmin()) loadAdminList();
    } catch (error) {
      msg.textContent = error.message;
      msg.classList.add('error');
    }
  });

  async function loadAdminList() {
    const box = document.getElementById('adminFeedbackBox');
    if (!isAdmin()) { box.innerHTML = ''; return; }
    box.innerHTML = `<h3 class="section-heading">全部反馈（管理员）</h3><div class="record-list" id="allFeedback"><p class="empty-tip">加载中…</p></div>`;
    try {
      const data = await apiGet('/api/feedback?username=all');
      const items = data.feedbacks || [];
      const host = document.getElementById('allFeedback');
      host.innerHTML = items.length ? items.map((f) => `
        <div class="consult-card">
          <div class="consult-head">
            <span class="consult-badge">${esc(f.type)}</span>
            <span class="consult-status">${esc(f.username)} · ${esc(f.status)}</span>
          </div>
          <div class="consult-meta">${esc(f.createdAt)}</div>
          <div class="consult-condition">${esc(f.content)}</div>
          <div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap">
            <input class="ocr-text" style="min-height:38px;height:38px;flex:1" id="fbReply_${f.id}" placeholder="回复这条反馈" value="${esc(f.reply || '')}" />
            <button class="btn btn-outline btn-sm" data-reply="${f.id}" type="button"><svg class="ico"><use href="#i-send"></use></svg>保存回复</button>
            <button class="btn btn-danger btn-sm" data-close="${f.id}" type="button"><svg class="ico"><use href="#i-shield"></use></svg>标记已处理</button>
          </div>
        </div>`).join('') : '<p class="empty-tip">暂无反馈</p>';

      host.querySelectorAll('button[data-reply]').forEach((btn) => {
        btn.addEventListener('click', async () => {
          const id = btn.dataset.reply;
          const reply = document.getElementById('fbReply_' + id).value.trim();
          try {
            await apiPost('/api/admin/feedback', { id: Number(id), reply, status: reply ? '已处理' : '待处理' });
            loadAdminList();
            loadList();
          } catch (e) { alert(e.message); }
        });
      });
      host.querySelectorAll('button[data-close]').forEach((btn) => {
        btn.addEventListener('click', async () => {
          try {
            await apiPost('/api/admin/feedback', { id: Number(btn.dataset.close), reply: '', status: '已处理' });
            loadAdminList();
          } catch (e) { alert(e.message); }
        });
      });
    } catch (e) {
      document.getElementById('allFeedback').innerHTML = '<p class="empty-tip">全部反馈加载失败</p>';
    }
  }
  loadAdminList();
}

/* ---------------------- 疾病管理 ---------------------- */
function openModal(id) { document.getElementById(id).classList.add('open'); }
function closeModal(id) { document.getElementById(id).classList.remove('open'); }

async function initManageDisease() {
  setupChrome('manage-disease');
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">疾病管理</h1>
    <div class="toolbar">
      <label class="search-field">${icon('i-search')}<input id="mdSearch" placeholder="搜索病种名称或分类" /></label>
      <button class="btn btn-primary btn-sm" id="addDisease" type="button"><svg class="ico"><use href="#i-plus"></use></svg>新增病种</button>
    </div>
    <div class="table-wrap">
      <table class="data">
        <thead><tr><th>病种名称</th><th>分类</th><th>常见症状</th><th>浏览量</th><th>操作</th></tr></thead>
        <tbody id="diseaseTableBody"><tr><td colspan="5" class="empty-tip">加载中…</td></tr></tbody>
      </table>
    </div>
    <div class="modal-mask" id="diseaseModal">
      <div class="modal">
        <div class="modal-head"><h3 id="diseaseModalTitle">新增病种</h3><button type="button" data-close="diseaseModal" aria-label="关闭"><svg class="ico"><use href="#i-close"></use></svg></button></div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="field"><label>病种名称</label><input id="d_name" /></div>
            <div class="field"><label>分类</label>
              <select id="d_category">
                ${Object.keys(CATEGORY_COLORS).map((c) => `<option>${c}</option>`).join('')}
                <option>常见疾病</option>
              </select>
            </div>
            <div class="field full"><label>病因</label><input id="d_causes" /></div>
            <div class="field full"><label>常见症状</label><input id="d_symptoms" /></div>
            <div class="field full"><label>关注点</label><input id="d_focus" /></div>
            <div class="field full"><label>简介</label><textarea id="d_description"></textarea></div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-outline" type="button" data-close="diseaseModal">取消</button>
          <button class="btn btn-primary" id="saveDisease" type="button">保存</button>
        </div>
      </div>
    </div>`;

  let list = [];
  const body = document.getElementById('diseaseTableBody');
  const paint = (keyword) => {
    const filtered = list.filter((d) => !keyword || (d.name + d.category).includes(keyword));
    body.innerHTML = filtered.length ? filtered.map((d) => `
      <tr>
        <td><strong>${esc(d.name)}</strong></td>
        <td>${esc(d.category || '')}</td>
        <td>${esc(d.symptoms || '')}</td>
        <td>${Number(d.views || 0)}</td>
        <td class="actions">
          <button class="btn btn-outline btn-sm" data-edit="${esc(d.name)}" type="button"><svg class="ico"><use href="#i-edit"></use></svg>编辑</button>
          <button class="btn btn-danger btn-sm" data-del="${esc(d.name)}" type="button"><svg class="ico"><use href="#i-trash"></use></svg>删除</button>
        </td>
      </tr>`).join('') : '<tr><td colspan="5" class="empty-tip">暂无数据</td></tr>';

    body.querySelectorAll('button[data-edit]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const item = list.find((d) => d.name === btn.dataset.edit);
        if (!item) return;
        document.getElementById('diseaseModalTitle').textContent = '编辑病种';
        document.getElementById('d_name').value = item.name;
        document.getElementById('d_name').dataset.original = item.name;
        document.getElementById('d_category').value = item.category || '常见疾病';
        document.getElementById('d_causes').value = item.causes || '';
        document.getElementById('d_symptoms').value = item.symptoms || '';
        document.getElementById('d_focus').value = item.focus || '';
        document.getElementById('d_description').value = item.description || '';
        openModal('diseaseModal');
      });
    });
    body.querySelectorAll('button[data-del]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('确认删除病种「' + btn.dataset.del + '」？')) return;
        try {
          await apiPost('/api/admin/disease', { action: 'delete', name: btn.dataset.del });
          await load();
        } catch (e) { alert(e.message); }
      });
    });
  };

  const load = async () => {
    try {
      list = await apiGet('/api/diseases');
      paint(document.getElementById('mdSearch').value.trim());
    } catch (e) {
      body.innerHTML = '<tr><td colspan="5" class="empty-tip">加载失败</td></tr>';
    }
  };

  document.getElementById('mdSearch').addEventListener('input', (event) => paint(event.target.value.trim()));
  document.getElementById('addDisease').addEventListener('click', () => {
    document.getElementById('diseaseModalTitle').textContent = '新增病种';
    ['d_name', 'd_causes', 'd_symptoms', 'd_focus', 'd_description'].forEach((id) => { document.getElementById(id).value = ''; });
    document.getElementById('d_name').dataset.original = '';
    openModal('diseaseModal');
  });
  document.querySelectorAll('[data-close="diseaseModal"]').forEach((btn) => btn.addEventListener('click', () => closeModal('diseaseModal')));
  document.getElementById('saveDisease').addEventListener('click', async () => {
    const payload = {
      action: 'save',
      name: document.getElementById('d_name').value.trim(),
      category: document.getElementById('d_category').value,
      causes: document.getElementById('d_causes').value.trim(),
      symptoms: document.getElementById('d_symptoms').value.trim(),
      focus: document.getElementById('d_focus').value.trim(),
      description: document.getElementById('d_description').value.trim(),
    };
    if (!payload.name) { alert('请填写病种名称'); return; }
    const original = document.getElementById('d_name').dataset.original;
    try {
      if (original && original !== payload.name) {
        await apiPost('/api/admin/disease', { action: 'delete', name: original });
      }
      await apiPost('/api/admin/disease', payload);
      closeModal('diseaseModal');
      await load();
    } catch (e) { alert(e.message); }
  });

  await load();
}

/* ---------------------- 药品管理 ---------------------- */
async function initManageMedicine() {
  setupChrome('manage-medicine');
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">药品管理</h1>
    <div class="toolbar">
      <label class="search-field">${icon('i-search')}<input id="mmSearch" placeholder="搜索药品名称、品牌或分类" /></label>
      <button class="btn btn-primary btn-sm" id="addMedicine" type="button"><svg class="ico"><use href="#i-plus"></use></svg>新增药品</button>
    </div>
    <div class="table-wrap">
      <table class="data">
        <thead><tr><th>药品名称</th><th>类型</th><th>分类</th><th>品牌</th><th>价格</th><th>操作</th></tr></thead>
        <tbody id="medicineTableBody"><tr><td colspan="6" class="empty-tip">加载中…</td></tr></tbody>
      </table>
    </div>
    <div class="modal-mask" id="medicineModal">
      <div class="modal">
        <div class="modal-head"><h3 id="medicineModalTitle">新增药品</h3><button type="button" data-close="medicineModal" aria-label="关闭"><svg class="ico"><use href="#i-close"></use></svg></button></div>
        <div class="modal-body">
          <div class="form-grid">
            <div class="field"><label>药品名称</label><input id="m_name" /></div>
            <div class="field"><label>分类</label><input id="m_category" placeholder="如：感冒类" /></div>
            <div class="field"><label>品牌</label><input id="m_brand" placeholder="如：999牌" /></div>
            <div class="field"><label>参考价格</label><input id="m_price" placeholder="如：¥15.8" /></div>
            <div class="field full"><label>用途</label><textarea id="m_use"></textarea></div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-outline" type="button" data-close="medicineModal">取消</button>
          <button class="btn btn-primary" id="saveMedicine" type="button">保存</button>
        </div>
      </div>
    </div>`;

  let list = [];
  const body = document.getElementById('medicineTableBody');
  const paint = (keyword) => {
    const filtered = list.filter((m) => !keyword || [m.name, m.brand, m.category].join(' ').includes(keyword));
    const shown = filtered.slice(0, 200);
    body.innerHTML = shown.length ? shown.map((m) => `
      <tr>
        <td><strong>${esc(m.name)}</strong></td>
        <td><span class="kind ${m.kind === '西药' ? 'western' : ''}">${esc(m.kind)}</span></td>
        <td>${esc(m.category)}</td>
        <td>${esc(m.brand || '')}</td>
        <td>${esc(m.price)}</td>
        <td class="actions">
          <button class="btn btn-outline btn-sm" data-edit="${esc(m.name)}" type="button"><svg class="ico"><use href="#i-edit"></use></svg>编辑</button>
          <button class="btn btn-danger btn-sm" data-del="${esc(m.name)}" type="button"><svg class="ico"><use href="#i-trash"></use></svg>删除</button>
        </td>
      </tr>`).join('') : '<tr><td colspan="6" class="empty-tip">暂无数据</td></tr>';

    body.querySelectorAll('button[data-edit]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const item = list.find((m) => m.name === btn.dataset.edit);
        if (!item) return;
        document.getElementById('medicineModalTitle').textContent = '编辑药品';
        document.getElementById('m_name').value = item.name;
        document.getElementById('m_name').dataset.original = item.name;
        document.getElementById('m_category').value = item.category || '';
        document.getElementById('m_brand').value = item.brand || '';
        document.getElementById('m_price').value = item.price || '';
        document.getElementById('m_use').value = item.use || '';
        openModal('medicineModal');
      });
    });
    body.querySelectorAll('button[data-del]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('确认删除药品「' + btn.dataset.del + '」？')) return;
        try {
          await apiPost('/api/admin/medicine', { action: 'delete', name: btn.dataset.del });
          await load();
        } catch (e) { alert(e.message); }
      });
    });
  };

  const load = async () => {
    try {
      list = await apiGet('/api/medicines');
      paint(document.getElementById('mmSearch').value.trim());
    } catch (e) {
      body.innerHTML = '<tr><td colspan="6" class="empty-tip">加载失败</td></tr>';
    }
  };

  document.getElementById('mmSearch').addEventListener('input', (event) => paint(event.target.value.trim()));
  document.getElementById('addMedicine').addEventListener('click', () => {
    document.getElementById('medicineModalTitle').textContent = '新增药品';
    ['m_name', 'm_category', 'm_brand', 'm_price', 'm_use'].forEach((id) => { document.getElementById(id).value = ''; });
    document.getElementById('m_name').dataset.original = '';
    openModal('medicineModal');
  });
  document.querySelectorAll('[data-close="medicineModal"]').forEach((btn) => btn.addEventListener('click', () => closeModal('medicineModal')));
  document.getElementById('saveMedicine').addEventListener('click', async () => {
    const payload = {
      action: 'save',
      name: document.getElementById('m_name').value.trim(),
      category: document.getElementById('m_category').value.trim(),
      brand: document.getElementById('m_brand').value.trim(),
      price: document.getElementById('m_price').value.trim(),
      use: document.getElementById('m_use').value.trim(),
    };
    if (!payload.name) { alert('请填写药品名称'); return; }
    const original = document.getElementById('m_name').dataset.original;
    try {
      if (original && original !== payload.name) {
        await apiPost('/api/admin/medicine', { action: 'delete', name: original });
      }
      await apiPost('/api/admin/medicine', payload);
      closeModal('medicineModal');
      await load();
    } catch (e) { alert(e.message); }
  });

  await load();
}

/* ---------------------- 搜索页 ---------------------- */
async function initSearch() {
  setupChrome('');
  const query = new URLSearchParams(window.location.search).get('q') || '';
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = `
    <h1 class="panel-title">搜索结果</h1>
    <div class="toolbar">
      <label class="search-field">${icon('i-search')}<input id="pageSearch" value="${esc(query)}" placeholder="搜索病种或药品" /></label>
    </div>
    <div id="searchResult"><p class="empty-tip">搜索中…</p></div>`;

  const input = document.getElementById('pageSearch');
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') window.location.href = 'search.html?q=' + encodeURIComponent(input.value.trim());
  });

  if (!query) {
    document.getElementById('searchResult').innerHTML = '<p class="empty-tip">请输入关键词开始搜索</p>';
    return;
  }

  try {
    const data = await apiGet('/api/search?q=' + encodeURIComponent(query));
    const diseases = data.diseases || [];
    const medicines = data.medicines || [];
    document.getElementById('searchResult').innerHTML = `
      <h3 class="section-heading">相关病种（${diseases.length}）</h3>
      <div class="disease-grid">${diseases.map(diseaseCard).join('') || '<p class="empty-tip">没有匹配的病种</p>'}</div>
      <h3 class="section-heading">相关药品（${medicines.length}）</h3>
      <div class="med-list">${medicines.map(medicineRow).join('') || '<p class="empty-tip">没有匹配的药品</p>'}</div>`;
  } catch (e) {
    document.getElementById('searchResult').innerHTML = '<p class="empty-tip">搜索失败，请稍后重试</p>';
  }
}

/* ---------------------- 详情页 ---------------------- */
const DISEASE_ADVICE = {
  '常见感染': '注意休息与保暖，多喝温水；如发热超过3天或体温持续升高，请及时就医。',
  '咽喉疾病': '清淡饮食、避免辛辣刺激，多饮水；咽痛明显或吞咽困难时请及时就诊。',
  '过敏类': '尽量避免接触过敏原，保持室内通风；症状反复或加重时请咨询医生。',
  '消化类': '规律饮食、少油少辣、避免暴饮暴食；如腹痛、呕吐、血便等请及时就医。',
  '呼吸类': '注意保暖、避免烟雾和粉尘刺激；出现气促、胸痛或持续咳嗽请尽快就医。',
  '神经类': '保持规律作息、避免熬夜和精神紧张；症状频繁或加重时请及时就医。',
  '慢病类': '遵医嘱长期管理，定期监测相关指标，不要自行停药或调药。',
  '耳鼻喉类': '保持耳部清洁干燥，避免用力擤鼻；耳痛或听力下降请及时就诊。',
  '眼科类': '注意用眼卫生、避免揉眼；眼红、疼痛或视力下降请及时就医。',
  '皮肤类': '避免搔抓和刺激，保持皮肤清洁保湿；皮损加重或感染请及时就医。',
  '骨关节类': '避免长时间低头和固定姿势，适当活动；出现肢体麻木或头晕加重请就医。',
  '泌尿类': '多饮水、注意个人卫生；出现发热、腰痛或血尿请及时就医。',
  '精神心理类': '保持规律作息与适度运动，必要时寻求专业心理或精神科评估。',
};
const MEDICINE_ADVICE = {
  '退烧止痛': '请按说明书或医嘱服用，避免超量；用于退烧连续不超过3天，止痛不超过5天。',
  '感冒类': '对症治疗，注意休息、多饮水；如持续高热或症状加重请及时就医。',
  '抗感染': '抗生素需在医生指导下使用，按疗程服用，切勿自行停药或滥用。',
  '过敏类': '用药期间避免驾驶等需集中注意力的活动；出现皮疹、呼吸困难请立即停药就医。',
  '抗病毒': '抗病毒药应在发病早期使用效果更佳，具体请遵医嘱。',
  '消炎止痛': '饭后服用可减轻胃肠刺激；胃溃疡患者慎用，出现黑便或腹痛请及时就医。',
  '止咳类': '多喝水、保持空气湿润；咳嗽持续或伴胸痛、发热时请及时就医。',
  '止咳化痰': '多饮水、保持空气湿润，避免烟酒刺激；痰多色黄伴发热请及时就医。',
  '营养补充': '按推荐量服用，不能替代正常饮食；如需长期补充请咨询医生。',
  '肠胃调理': '按说明服用，注意饮食清淡；腹泻持续或出现脱水请及时就医。',
  '中成药': '辨证使用，孕妇、儿童及慢性病患者请在医生指导下服用。',
  '止痛类': '短期缓解症状，避免长期依赖；疼痛原因不明或加重请及时就医。',
  '慢病类': '需长期规律用药，定期复诊，不要自行停药或调整剂量。',
  '儿童用药': '儿童用药需按年龄体重精确给药，请遵医嘱并注意不要与成人药混用。',
  '皮肤外用': '外用药物避免接触眼睛和黏膜；皮损加重或出现过敏请停药就医。',
  '五官科': '滴眼/滴鼻药物请勿与他人共用，开封后注意有效期。',
};

// 病种 → 对应病症示意图（扁平插画，images/diseases/d01~d20.png，按后端病种顺序）
const DISEASE_IMAGES = {
  '上呼吸道感染': 'images/diseases/d01.png',
  '急性扁桃体炎': 'images/diseases/d02.png',
  '过敏性鼻炎': 'images/diseases/d03.png',
  '胃炎': 'images/diseases/d04.png',
  '急性肠胃炎': 'images/diseases/d05.png',
  '支气管炎': 'images/diseases/d06.png',
  '流行性感冒': 'images/diseases/d07.png',
  '哮喘': 'images/diseases/d08.png',
  '胃食管反流病': 'images/diseases/d09.png',
  '偏头痛': 'images/diseases/d10.png',
  '高血压': 'images/diseases/d11.png',
  '2型糖尿病': 'images/diseases/d12.png',
  '慢性咽炎': 'images/diseases/d13.png',
  '中耳炎': 'images/diseases/d14.png',
  '结膜炎': 'images/diseases/d15.png',
  '湿疹': 'images/diseases/d16.png',
  '颈椎病': 'images/diseases/d17.png',
  '泌尿系感染': 'images/diseases/d18.png',
  '肺炎': 'images/diseases/d19.png',
  '失眠症': 'images/diseases/d20.png',
  '焦虑症': 'images/diseases/d21.png',
  '抑郁症': 'images/diseases/d22.png',
};

async function initDiseaseDetail() {
  setupChrome('home');
  const name = new URLSearchParams(window.location.search).get('name') || '';
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = '<p class="empty-tip">加载中…</p>';
  try {
    const list = await apiGet('/api/diseases');
    const item = list.find((d) => d.name === name);
    if (!item) {
      panel.innerHTML = '<p class="empty-tip">未找到该病种，<a href="disease.html" style="color:#1a63a8">返回疾病百科</a></p>';
      return;
    }
    const colors = CATEGORY_COLORS[item.category] || ['#2f9ec4', '#49b8d8'];
    const advice = DISEASE_ADVICE[item.tag] || '如症状持续或加重，建议及时前往正规医疗机构就诊。';
    const checkpoints = [
      '症状持续 3 天以上或反复发作，建议就诊',
      '突然胸痛、呼吸困难、意识模糊，立即就医',
      '长期服药或有基础病，先问医生再调整',
    ];
    const aside = `
      <aside class="detail-aside">
        <figure class="aside-photo">
          <img src="${esc(DISEASE_IMAGES[item.name] || 'images/checkup-visual.jpg')}" alt="${esc(item.name)} 病症示意图" />
          <figcaption>${esc(item.name)} · 病症示意图</figcaption>
        </figure>
        <div class="aside-card">
          <h5>什么时候该去医院</h5>
          <ul>${checkpoints.map((t) => `<li>${t}</li>`).join('')}</ul>
        </div>
        <a class="aside-cta" href="doctor.html">症状说不清？问智能医生</a>
      </aside>`;
    panel.innerHTML = `
      <div class="detail-layout">
      <div class="detail-wrap">
        <div class="detail-badge" style="background:${colors[0]}1a;color:${colors[0]}">${esc(item.category || '常见疾病')}</div>
        <h2>${esc(item.name)}</h2>
        <p style="color:#7b8a97;font-size:13px">${esc(item.description || '')}</p>
        <div class="kv-grid" style="margin-top:20px">
          <div class="kv"><span>病种分类</span><strong>${esc(item.category || '')}</strong></div>
          <div class="kv"><span>关注程度</span><strong>${esc(item.tag || '')}</strong></div>
          <div class="kv"><span>累计浏览</span><strong>${Number(item.views || 0)}</strong></div>
        </div>
        <div class="detail-section"><h4>常见病因</h4><p>${esc(item.causes || '暂无相关资料')}</p></div>
        <div class="detail-section"><h4>典型症状</h4><p>${esc(item.symptoms || '暂无相关资料')}</p></div>
        <div class="detail-section"><h4>需要重点关注</h4><p>${esc(item.focus || '暂无相关资料')}</p></div>
        <div class="detail-section"><h4>医生建议</h4><p>${esc(advice)}</p></div>
        <div class="form-footer">
          <a class="btn btn-primary" href="doctor.html">咨询智能医生</a>
          <button class="btn btn-outline fav-btn" id="detailFav" type="button">${icon('i-heart')}收藏</button>
          <a class="btn btn-outline" href="disease.html">返回疾病百科</a>
        </div>
      </div>
      ${aside}
      </div>`;
    recordHistory('disease', item.name, item.name);
    bindFavoriteButton(document.getElementById('detailFav'), 'disease', item.name, item.name);
    apiPost('/api/diseases/view', { name: item.name }).catch(() => {});
  } catch (e) {
    panel.innerHTML = '<p class="empty-tip">病种信息加载失败</p>';
  }
}

async function initMedicineDetail() {
  setupChrome('medicine');
  const name = new URLSearchParams(window.location.search).get('name') || '';
  const panel = document.getElementById('mainPanel');
  panel.innerHTML = '<p class="empty-tip">加载中…</p>';
  try {
    const list = await apiGet('/api/medicines');
    const item = list.find((m) => m.name === name);
    if (!item) {
      panel.innerHTML = '<p class="empty-tip">未找到该药品，<a href="medicine.html" style="color:#1a63a8">返回查找药品</a></p>';
      return;
    }
    const advice = MEDICINE_ADVICE[item.category] || '请在医生或药师指导下使用，注意休息、均衡饮食；如症状持续或加重，请及时就医。';
    const medAside = `
      <aside class="detail-aside">
        <figure class="aside-photo">
          <img src="images/medicine-visual.jpg" alt="水杯、药片与药瓶摆放在浅色布面上" />
          <figcaption>服药前先看说明书与剂量</figcaption>
        </figure>
        <div class="aside-card">
          <h5>服药三查</h5>
          <ul>
            <li>查效期：过期药一律不用，外观变色也别吃</li>
            <li>查剂量：按说明书或医嘱，别自行加倍</li>
            <li>查冲突：正在吃的其他药先问医生或药师</li>
          </ul>
        </div>
        <a class="aside-cta" href="cabinet.html">放进家庭药箱，到期提醒</a>
      </aside>`;
    panel.innerHTML = `
      <div class="detail-layout">
      <div class="detail-wrap">
        <div style="display:flex;gap:22px;align-items:flex-start;flex-wrap:wrap">
          <div class="med-detail-thumb"><img src="${medThumb(item)}" alt="${esc(item.name)}" /></div>
          <div style="flex:1;min-width:240px">
            <span class="detail-badge">${esc(item.kind)} · ${esc(item.category)}</span>
            <h2 style="margin-bottom:8px">${esc(item.name)}</h2>
            <p style="color:#7b8a97;font-size:13px">${esc(item.use)}</p>
            <div class="kv-grid" style="margin-top:16px">
              <div class="kv"><span>品牌</span><strong>${esc(item.brand || '')}</strong></div>
              <div class="kv"><span>参考价格</span><strong>${esc(item.price)}</strong></div>
            </div>
          </div>
        </div>
        <div class="detail-section"><h4>适应症状 / 用途</h4><p>${esc(item.use)}</p></div>
        <div class="detail-section"><h4>用药提示</h4><p>${esc(advice)}</p></div>
        <div class="detail-section"><h4>安全提醒</h4><p>“是药三分毒，切忌不要乱吃药！”处方药请在医生指导下使用，孕妇、儿童、老年人及肝肾功能异常者用药前请咨询医师或药师。</p></div>
        <div class="form-footer">
          <a class="btn btn-primary" href="doctor.html">咨询智能医生</a>
          <button class="btn btn-outline fav-btn" id="detailFav" type="button">${icon('i-heart')}收藏</button>
          <button class="btn btn-outline" id="detailCabinet" type="button">${icon('i-box')}加入药箱</button>
          <a class="btn btn-outline" href="medicine.html">返回查找药品</a>
        </div>
      </div>
      ${medAside}
      </div>`;
    recordHistory('medicine', item.name, item.name);
    bindFavoriteButton(document.getElementById('detailFav'), 'medicine', item.name, item.name);

    const cabBtn = document.getElementById('detailCabinet');
    if (cabBtn) {
      cabBtn.addEventListener('click', async () => {
        const user = getUser();
        if (!user || !user.username) return;
        cabBtn.disabled = true;
        try {
          const result = await apiPost('/api/cabinet', {
            action: 'save', username: user.username,
            medicineName: item.name, spec: '', quantity: 1,
            note: '从药品详情加入',
          });
          cabBtn.innerHTML = '✓ 已加入药箱';
          setTimeout(() => { cabBtn.innerHTML = `${icon('i-box')}加入药箱`; cabBtn.disabled = false; }, 1600);
          console.info(result.message);
        } catch (error) {
          cabBtn.textContent = error.message;
          setTimeout(() => { cabBtn.innerHTML = `${icon('i-box')}加入药箱`; cabBtn.disabled = false; }, 1600);
        }
      });
    }
  } catch (e) {
    panel.innerHTML = '<p class="empty-tip">药品信息加载失败</p>';
  }
}

/* ---------------------- 首页 ---------------------- */
const HEALTH_TIPS = [
  { symptoms: '咳嗽、发热、浑身没劲', judgment: '上呼吸道感染的可能性最大', advice: '对乙酰氨基酚、含片，多喝水' },
  { symptoms: '鼻塞、流涕、一直打喷嚏', judgment: '更像是过敏性鼻炎，不是感冒', advice: '生理盐水喷鼻、抗过敏药' },
  { symptoms: '肚子疼、拉肚子、想吐', judgment: '急性肠胃炎，注意有没有脱水', advice: '补液盐、清淡饮食、先别吃油腻' },
  { symptoms: '胸闷、没力气、睡不好', judgment: '先排除疲劳，但胸闷要盯住', advice: '规律作息、开窗通风，两天不缓解去查' },
];

function renderHealthAssistant(index = 0) {
  const container = document.getElementById('healthAssistantContent');
  if (!container) return;
  const item = HEALTH_TIPS[index % HEALTH_TIPS.length];
  container.innerHTML = `
    <div class="mini-card"><span>可能会说</span><strong>${esc(item.symptoms)}</strong></div>
    <div class="mini-card"><span>大概是什么</span><strong>${esc(item.judgment)}</strong></div>
    <div class="mini-card"><span>可以先试</span><strong>${esc(item.advice)}</strong></div>`;
}

/* 首页：常用药品 / 热门病种（读真实接口） */
async function loadHomeFeeds() {
  const medList = document.getElementById('homeMedList');
  const disList = document.getElementById('homeDiseaseList');
  if (!medList || !disList) return;

  try {
    const meds = await apiGet('/api/medicines');
    const picked = [];
    const seen = new Set();
    meds.forEach((m) => {
      const key = m.category || '其他';
      if (picked.length >= 6 || seen.has(key)) return;
      seen.add(key);
      picked.push(m);
    });
    meds.forEach((m) => { if (picked.length < 6 && !picked.includes(m)) picked.push(m); });
    medList.innerHTML = picked.map((m) => `
      <a class="feed-row" href="medicine-detail.html?name=${encodeURIComponent(m.name)}">
        <span class="feed-thumb"><img src="${medThumb(m)}" alt="${esc(m.name)}" /></span>
        <span class="feed-main">
          <strong>${esc(m.name)}</strong>
          <span>${esc(m.category || '')} · ${esc(String(m.use || '').slice(0, 22))}</span>
        </span>
        <span class="feed-price">${esc(m.price || '')}</span>
      </a>`).join('');
  } catch (e) {
    medList.innerHTML = '<p class="empty-tip">药品数据加载失败</p>';
  }

  try {
    const diseases = await apiGet('/api/diseases');
    const hot = diseases.slice().sort((a, b) => Number(b.views || 0) - Number(a.views || 0)).slice(0, 6);
    disList.innerHTML = hot.map((d) => {
      const color = (CATEGORY_COLORS[d.category] || ['#2f9ec4'])[0];
      return `
      <a class="feed-row" href="disease-detail.html?name=${encodeURIComponent(d.name)}">
        <span class="feed-dot" style="background:${color}"></span>
        <span class="feed-main">
          <strong>${esc(d.name)}</strong>
          <span>${esc(d.category || '')} · 浏览 ${Number(d.views || 0)}</span>
        </span>
        <span class="feed-tag" style="color:${color};border-color:${color}33">${esc(d.tag || '')}</span>
      </a>`;
    }).join('');
  } catch (e) {
    disList.innerHTML = '<p class="empty-tip">病种数据加载失败</p>';
  }
}

/* 首页：BMI 自测 */
function initBmiTool() {
  const btn = document.getElementById('bmiCalc');
  if (!btn) return;
  const box = document.getElementById('bmiResult');
  const heightInput = document.getElementById('bmiHeight');
  const weightInput = document.getElementById('bmiWeight');

  const calc = () => {
    const h = parseFloat(heightInput.value);
    const w = parseFloat(weightInput.value);
    if (!h || !w || h < 80 || h > 250 || w < 20 || w > 300) {
      box.innerHTML = '请输入有效的身高（80–250 cm）与体重（20–300 kg）。';
      return;
    }
    const bmi = w / Math.pow(h / 100, 2);
    let level = '正常范围';
    let color = '#12857a';
    let tip = '保持每周 150 分钟中等强度运动，规律作息，按年体检即可。';
    if (bmi < 18.5) {
      level = '偏瘦'; color = '#c2803c';
      tip = '适当增加优质蛋白与主食摄入；若长期偏瘦，建议排查甲亢与消化吸收问题。';
    } else if (bmi < 24) {
      level = '正常范围';
    } else if (bmi < 28) {
      level = '超重'; color = '#c2803c';
      tip = '先减少精制糖与夜宵，每周至少 150 分钟有氧，3 个月后复测。';
    } else {
      level = '肥胖'; color = '#c14b5c';
      tip = '建议到内分泌或营养门诊评估，同时关注血压、血糖、血脂。';
    }
    box.innerHTML = `
      <strong style="color:${color}">${bmi.toFixed(1)}</strong>
      <span class="bmi-level" style="color:${color}">${level}</span>
      <p class="bmi-tip">${tip}</p>
      <em>BMI 只反映身高与体重的关系，肌肉量高的人会偏高，仅作参考。</em>`;
  };

  btn.addEventListener('click', calc);
  [heightInput, weightInput].forEach((el) => {
    el.addEventListener('keydown', (event) => { if (event.key === 'Enter') calc(); });
  });
}

function initSiteHome() {
  setupChrome('site-home');
  let healthIndex = 0;
  renderHealthAssistant(healthIndex);
  renderHomeDoses();
  loadHomeFeeds();
  initBmiTool();
  onPageResume(renderHomeDoses);
  const refreshBtn = document.getElementById('healthRefreshBtn');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', () => {
      healthIndex += 1;
      renderHealthAssistant(healthIndex);
      refreshBtn.classList.remove('spin');
      void refreshBtn.offsetWidth;
      refreshBtn.classList.add('spin');
      setTimeout(() => refreshBtn.classList.remove('spin'), 400);
    });
  }
}

/* ---------------------- 启动 ---------------------- */
async function boot() {
  const page = document.body.dataset.page;
  // 登录校验（登录页本身除外）
  if (!getUser() || !getUser().loggedIn) {
    if (page !== 'login') {
      window.location.href = 'login.html';
      return;
    }
  }
  if (page === 'login') return;

  // 真实药品照片清单（用于药品列表/详情/候选药品缩略图）
  await ensureMedImages();

  // 先确定当前成员，后面所有按人取的接口才能带上 memberId
  await loadMembers();

  // 有侧边栏的页面先加载档案，保证姓名/电话/邮箱正确
  const sidebarPages = ['profile', 'feedback', 'manage-disease', 'manage-medicine', 'records'];
  if (sidebarPages.includes(page)) {
    await loadProfile();
  }

    switch (page) {
    case 'site-home': initSiteHome(); break;
    case 'home': await initHome(); break;
    case 'medicine': await initMedicine('medicine'); break;
    case 'identify': await initIdentify(); break;
    case 'doctor': initChat(); break;
    case 'profile': await initProfile(); break;
    case 'cabinet': await initCabinet(); break;
    case 'lab-report': await initLabReport(); break;
    case 'favorites': await initFavorites(); break;
    case 'records': await initRecords(); break;
    case 'feedback': await initFeedback(); break;
    case 'manage-disease': await initManageDisease(); break;
    case 'manage-medicine': await initManageMedicine(); break;
    case 'search': await initSearch(); break;
    case 'disease-detail': await initDiseaseDetail(); break;
    case 'medicine-detail': await initMedicineDetail(); break;
    case 'about': setupChrome('about'); break;
    default: break;
  }
}

document.addEventListener('DOMContentLoaded', boot);
