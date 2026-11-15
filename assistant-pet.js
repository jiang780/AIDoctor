/*!
 * AIDoctor · IP 悬浮挂件
 *   1. 单指拖动，想放哪放哪
 *   2. 拖到左右 / 上下边缘自动贴边半隐藏
 *   3. 双击进入 AI 问诊页；单击只抖动一下，不跳转
 *
 * 不遮挡页面操作的做法：容器 pointer-events:none，交互全部在 window 上做命中检测
 * （比对图片 alpha 通道）。只有按在人物不透明的地方才接管，其余位置一律穿透。
 */
(function () {
  'use strict';
  if (window.__aiIpMounted) return;
  window.__aiIpMounted = true;

  var CHAT_PAGE = 'doctor.html';
  var HIDE_RATIO = 0.5;      // 贴边时藏到屏幕外的比例
  var SNAP = 56;              // 距边缘多少像素内自动吸附
  var STORE_KEY = 'aiip:pos';
  var ASSET_VER = '3';   // 换形象时递增，绕开浏览器缓存
  var TAP_MS = 330;           // 双击判定间隔
  var TAP_DIST = 40;          // 双击判定位移
  var DRAG_MIN = 6;           // 超过这个位移才算拖动

  var el, img, bob, tip;
  var state = { x: 0, y: 0, dock: '', w: 0, h: 0 };
  var mask = null, GW = 0, GH = 0;

  var dragging = false, moved = false, pid = null, startDock = '';
  var startX = 0, startY = 0, baseX = 0, baseY = 0;
  var lastMoveX = 0, lastMoveY = 0, rafId = 0;
  var lastTap = 0, lastTapX = 0, lastTapY = 0;
  var hoverOn = false, hoverRaf = 0, tipTimer = 0;
  // 通用兜底话术（无对应场景时使用）
  var GENERIC = [
    '你好，我是你的 AI 医生小助手，身体不舒服随时找我～',
    '换季温差大，记得添衣、多喝水，少去人多的地方。',
    '感冒发烧别硬扛，持续三天不退建议来问问我。',
    '吃药前看清说明书，不同药别随便混着吃。',
    '家里有老人小孩，备好体温计和常用药更安心。',
    '双击我就能开始问诊，把症状讲给我听就好。',
    '熬夜伤身，今晚早点休息，身体才扛得住。',
    '饭前便后勤洗手，小习惯能挡掉大毛病。'
  ];

  // 按页面（data-page 或文件名的场景）分场景的话术池
  // 每池第 1 条为该场景的"进场问候"，其余为轮换的健康/操作提示
  var SCENARIOS = {
    'site-home': [ // 首页
      '欢迎回来～首页可以一键直达问诊、查药和药箱。',
      '哪里不舒服？点「开始问诊」跟我说说症状。',
      '家里常用药备齐了吗？去「家庭药箱」查漏补缺。',
      '想快速找药或查病，顶部搜索框直接打字。',
      '拍张药盒照片就能识药，点「拍照识药」试试。'
    ],
    'doctor': [ // 智能医生问诊页
      '把不舒服的地方、多久了、有没有发烧告诉我～',
      '描述越具体，我的判断越准哦。',
      '老人、儿童或孕妇请务必说明，用药会不一样。',
      '拿不准时，可以拍化验单或药盒发给我看。',
      '问诊建议仅供参考，紧急情况请立即就医。'
    ],
    'cabinet': [ // 家庭药箱
      '药品快过期会标红，记得定期清理过期药。',
      '加新药时系统会自动帮你查相互作用和过敏冲突。',
      '老人小孩的药最好分开放，看清剂量再吃。',
      '不确定两种药能否同吃？点药品看「安全提醒」。',
      '多成员家庭可以给每个人建独立用药档案。'
    ],
    'identify': [ // 拍照识药
      '把药盒正面拍清楚，我就能认出是什么药。',
      '光线亮一点、别反光，识别更准。',
      '认出来后我能告诉你要怎么吃、注意什么。',
      '拍不清的话，也可以直接搜药名查。'
    ],
    'medicine': [ // 查找药品
      '看药品详情时，留意「相互作用」和「禁忌」。',
      '儿童用药要看体重剂量，别按成人量减半。',
      '拿不准怎么吃，把药名发到问诊页问我。',
      '收藏常用药品，下次在「我的收藏」里直接找。'
    ],
    'medicine-detail': [ // 药品详情
      '这页能看到用法、相互作用和禁忌，下单前看一眼。',
      '儿童用药要看体重剂量，别按成人量减半。',
      '拿不准怎么吃，把药名发到问诊页问我。',
      '点右上角收藏，常用药品下次直接找。'
    ],
    'home': [ // 疾病百科（disease.html）
      '查疾病时看看「常见症状」和「就医信号」。',
      '症状持续加重或出现警示信号，别拖，尽快就医。',
      '病种页的用药建议仅供参考，别自行乱吃。',
      '拿不准属于哪种病？来问诊页跟我描述症状。'
    ],
    'disease-detail': [ // 病种详情
      '这页有症状、用药和就医信号，对照看看。',
      '症状持续加重或出现警示信号，别拖，尽快就医。',
      '病种页的用药建议仅供参考，别自行乱吃。',
      '想确认自己是不是这病？来问诊页跟我聊聊。'
    ],
    'lab-report': [ // 化验单解读
      '上传化验单，我帮你标出异常指标。',
      '报告拍全一点，项目名称和数值都要清楚。',
      '指标异常先别慌，我结合症状一起帮你看。'
    ],
    'records': [ // 问诊记录
      '这里是你历次问诊的对话，随时回看。',
      '想把建议发给家人？记录页可以截图保存。',
      '回看历史时，注意用药建议可能已过时。'
    ],
    'favorites': [ // 我的收藏
      '收藏的药品和文章都在这里，方便下次找。',
      '不常用的收藏可以清理一下，保持清爽。'
    ],
    'search': [ // 搜索结果
      '搜不到结果？换个简称或品牌名试试。',
      '找到的药品点进去，能看到用法和注意事项。'
    ],
    'profile': [ // 我的资料
      '完善资料能让用药建议更贴合你。',
      '设置过敏史和基础病，加药时会自动核对。'
    ],
    'feedback': [ // 反馈管理
      '用得不顺手？在这里告诉我，我会越改越好。'
    ],
    'about': [ // 关于我们
      'AIDoctor 帮你自查症状、核对用药，仅供参考。'
    ],
    'doctor-admin': [ // 医生端（接诊台/开方/资料/注册/登录）
      '医生端：开方按成员切换，剂量自动按体重算。',
      '给儿童开药记得确认体重，避免超量。',
      '「我开出的方案」会按家庭成员分开显示。',
      '患者过敏史和基础病已自动带入开方核查。'
    ],
    'result': [ // 诊断结果
      '诊断结果仅供参考，必要时线下再找医生确认。',
      '拿不准用药，把结果发问诊页，一起看怎么处理。'
    ],
    'login': [ // 登录
      '登录后药箱和问诊记录会同步，换设备也能用。'
    ]
  };

  /** 当前页面对应的场景 key（优先 data-page，其次按文件名兜底） */
  function getPageKey() {
    var p = document.body && document.body.getAttribute('data-page');
    if (p) return p;
    var file = (location.pathname.split('/').pop() || '').toLowerCase();
    if (file.indexOf('doctor-') === 0) return 'doctor-admin';
    if (file === 'result.html') return 'result';
    if (file === 'login.html') return 'login';
    return '';
  }

  /** 当前场景的话术池（兜底用通用池） */
  function currentTips() {
    var pool = SCENARIOS[getPageKey()];
    return pool && pool.length ? pool : GENERIC;
  }

  var tipIndex = 0;
  var autoTimer = 0;

  /* ------------------------- 样式 ------------------------- */

  function injectStyle() {
    var css = ''
      + '.aiip{position:fixed;left:0;top:0;z-index:9000;pointer-events:none;will-change:transform;'
      + 'transition:transform .34s cubic-bezier(.22,.9,.28,1);-webkit-tap-highlight-color:transparent;}'
      + '.aiip.no-anim{transition:none;}'
      + '.aiip-bob{animation:aiipFloat 3.6s ease-in-out infinite;transform-origin:50% 94%;}'
      + '.aiip-img{display:block;height:150px;width:auto;user-select:none;-webkit-user-drag:none;'
      + 'filter:drop-shadow(0 12px 18px rgba(12,42,72,.18));transition:filter .18s ease;}'
      + '.aiip.is-hover .aiip-img{filter:drop-shadow(0 14px 22px rgba(12,42,72,.28)) brightness(1.03);}'
      + '.aiip.is-hover .aiip-bob{animation-duration:2.1s;}'
      + '.aiip.is-tap .aiip-bob{animation:aiipTap .44s ease;}'
      + '.aiip.is-dragging .aiip-img{filter:drop-shadow(0 18px 26px rgba(12,42,72,.32));}'
      + '.aiip-tip{position:absolute;right:2px;bottom:calc(100% - 4px);width:max-content;'
      + 'max-width:min(220px,64vw);padding:7px 11px;border-radius:3px;background:#fff;'
      + 'border:1px solid #d7e3ef;box-shadow:0 6px 18px rgba(12,42,72,.14);'
      + 'font:500 12.5px/1.45 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;'
      + 'color:#12365c;opacity:0;transform:translateY(6px);transition:opacity .3s,transform .3s;}'
      + '.aiip-tip::after{content:"";position:absolute;right:24px;top:100%;width:9px;height:9px;background:#fff;'
      + 'border-right:1px solid #d7e3ef;border-bottom:1px solid #d7e3ef;'
      + 'transform:translateY(-5px) rotate(45deg);}'
      + '.aiip-tip.show{opacity:1;transform:translateY(0);}'
      + '.aiip-tip.tip-right{right:auto;left:2px;}'
      + '.aiip-tip.tip-right::after{right:auto;left:24px;}'
      + '@keyframes aiipFloat{0%,100%{transform:translateY(0)}50%{transform:translateY(-7px)}}'
      + '@keyframes aiipTap{0%,100%{transform:translateY(0) scale(1)}'
      + '35%{transform:translateY(-5px) scale(1.04)}70%{transform:translateY(0) scale(.98)}}'
      + '@media (max-width:560px){.aiip-img{height:104px}.aiip-tip{font-size:12px;padding:6px 9px;}}'
      + '@media (prefers-reduced-motion:reduce){.aiip-bob{animation:none}}';

    var s = document.createElement('style');
    s.id = 'aiip-style';
    s.textContent = css;
    document.head.appendChild(s);
  }

  /* ------------------------- 构建 ------------------------- */

  function build() {
    var base = location.href.replace(/[^/]*$/, '');
    el = document.createElement('div');
    el.className = 'aiip';
    el.id = 'aiIp';
    el.setAttribute('role', 'button');
    el.setAttribute('tabindex', '0');
    el.setAttribute('aria-label', 'AI 医生助手，双击进入问诊');
    el.innerHTML = ''
      + '<div class="aiip-bob">'
      + '<img class="aiip-img" alt="" draggable="false"'
      + ' src="' + base + 'images/aiip.png?v=' + ASSET_VER + '"'
      + ' srcset="' + base + 'images/aiip.png?v=' + ASSET_VER + ' 1x, '
      + base + 'images/aiip@2x.png?v=' + ASSET_VER + ' 2x">'
      + '</div>'
      + '<div class="aiip-tip"></div>';

    document.body.appendChild(el);
    img = el.querySelector('.aiip-img');
    bob = el.querySelector('.aiip-bob');
    tip = el.querySelector('.aiip-tip');

    img.addEventListener('error', function () { el.remove(); });
    if (img.complete && img.naturalWidth) onReady();
    else img.addEventListener('load', onReady);
  }

  function onReady() {
    state.w = img.offsetWidth;
    state.h = img.offsetHeight;
    buildMask();
    restore();
    render(false);
    maybeShowTip();
  }

  /* --------------------- 命中检测（alpha） --------------------- */

  function buildMask() {
    GW = 72;
    GH = Math.max(1, Math.round(GW * img.naturalHeight / img.naturalWidth));
    var cv = document.createElement('canvas');
    cv.width = GW;
    cv.height = GH;
    var ctx = cv.getContext('2d');
    ctx.drawImage(img, 0, 0, GW, GH);
    var data;
    try {
      data = ctx.getImageData(0, 0, GW, GH).data;
    } catch (e) {
      mask = null;
      return;
    }
    mask = new Uint8Array(GW * GH);
    for (var i = 0; i < GW * GH; i++) mask[i] = data[i * 4 + 3];
  }

  function rect() {
    return img.getBoundingClientRect();
  }

  /** 该点是否落在人物主体上；贴边半隐藏时放宽为可见区域整体可抓 */
  function hit(cx, cy, radius) {
    var r = rect();
    if (!r.width || cx < r.left || cx > r.right || cy < r.top || cy > r.bottom) return false;
    if (state.dock) {
      return cx >= 0 && cx <= window.innerWidth && cy >= 0 && cy <= window.innerHeight;
    }
    if (!mask) return true;

    var gx = Math.floor((cx - r.left) / r.width * GW);
    var gy = Math.floor((cy - r.top) / r.height * GH);
    var rad = radius || 0;
    for (var y = gy - rad; y <= gy + rad; y++) {
      if (y < 0 || y >= GH) continue;
      for (var x = gx - rad; x <= gx + rad; x++) {
        if (x < 0 || x >= GW) continue;
        if (mask[y * GW + x] >= 22) return true;
      }
    }
    return false;
  }

  /* ------------------------- 位置 ------------------------- */

  function dockDelta(side) {
    if (side === 'left') return { dx: -state.w * HIDE_RATIO, dy: 0 };
    if (side === 'right') return { dx: state.w * HIDE_RATIO, dy: 0 };
    if (side === 'top') return { dx: 0, dy: -state.h * HIDE_RATIO };
    if (side === 'bottom') return { dx: 0, dy: state.h * HIDE_RATIO };
    return { dx: 0, dy: 0 };
  }

  function render(animate) {
    var d = dockDelta(state.dock);
    el.classList.toggle('no-anim', !animate);
    el.style.transform = 'translate3d(' + (state.x + d.dx) + 'px,' + (state.y + d.dy) + 'px,0)';
    el.classList.toggle('is-docked', !!state.dock);
  }

  function clamp() {
    var vw = window.innerWidth, vh = window.innerHeight;
    state.x = Math.min(vw - state.w * 0.4, Math.max(-state.w * 0.6, state.x));
    state.y = Math.min(vh - state.h * 0.4, Math.max(-state.h * 0.6, state.y));
  }

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({ x: state.x, y: state.y, dock: state.dock }));
    } catch (e) { /* 隐私模式下忽略 */ }
  }

  function snapTo(side) {
    var vw = window.innerWidth, vh = window.innerHeight;
    state.dock = side;
    if (side === 'left') state.x = 0;
    else if (side === 'right') state.x = vw - state.w;
    else if (side === 'top') state.y = 0;
    else if (side === 'bottom') state.y = vh - state.h;
  }

  function restore() {
    var vw = window.innerWidth, vh = window.innerHeight;
    var saved = null;
    try { saved = JSON.parse(localStorage.getItem(STORE_KEY) || 'null'); } catch (e) { saved = null; }

    if (saved && typeof saved.x === 'number' && typeof saved.y === 'number') {
      state.x = saved.x;
      state.y = saved.y;
      state.dock = saved.dock || '';
    } else {
      state.x = vw - state.w - 18;
      state.y = vh - state.h - 20;
      state.dock = '';
    }
    if (state.dock) snapTo(state.dock);
    clamp();
  }

  /** 松手后判断要不要贴边半隐藏 */
  function settle() {
    var vw = window.innerWidth, vh = window.innerHeight;
    var gaps = [
      ['left', state.x],
      ['right', vw - state.x - state.w],
      ['top', state.y],
      ['bottom', vh - state.y - state.h]
    ];
    var best = null, i;
    for (i = 0; i < gaps.length; i++) {
      if (gaps[i][1] <= SNAP && (!best || gaps[i][1] < best[1])) best = gaps[i];
    }
    if (best) snapTo(best[0]);
    else state.dock = '';
    render(true);
    save();
  }

  /* ------------------------- 交互 ------------------------- */

  /** 拖动/点击后，吞掉浏览器补发的那次 click，避免误触下层按钮 */
  function blockNextClick(ms) {
    function stop(e) { e.preventDefault(); e.stopPropagation(); }
    window.addEventListener('click', stop, true);
    setTimeout(function () { window.removeEventListener('click', stop, true); }, ms || 260);
  }

  function goChat() {
    var here = (location.pathname.split('/').pop() || 'index.html');
    if (here === CHAT_PAGE) {
      var input = document.getElementById('chatInput');
      if (input) input.focus({ preventScroll: false });
      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
      return;
    }
    location.href = CHAT_PAGE;
  }

  function tapFeedback() {
    el.classList.remove('is-tap');
    void el.offsetWidth;
    el.classList.add('is-tap');
    setTimeout(function () { el.classList.remove('is-tap'); }, 480);
  }

  function onDown(e) {
    if (dragging) return;
    if (typeof e.button === 'number' && e.button !== 0) return;
    if (!hit(e.clientX, e.clientY, 4)) return;

    dragging = true;
    moved = false;
    pid = e.pointerId;
    startDock = state.dock;
    startX = e.clientX;
    startY = e.clientY;
    lastMoveX = startX;
    lastMoveY = startY;

    var d = dockDelta(state.dock);
    baseX = state.x + d.dx;     // 以当前"看得到的位置"为拖拽起点
    baseY = state.y + d.dy;

    if (window.getSelection) {
      try { window.getSelection().removeAllRanges(); } catch (err) { /* ignore */ }
    }
    document.documentElement.style.cursor = 'grabbing';
    hideTip();
    e.preventDefault();
    e.stopPropagation();
  }

  function onMove(e) {
    if (!dragging) {
      scheduleHover(e);
      return;
    }
    if (e.pointerId !== pid) return;

    var dx = e.clientX - startX;
    var dy = e.clientY - startY;
    if (!moved) {
      if (Math.abs(dx) < DRAG_MIN && Math.abs(dy) < DRAG_MIN) return;
      moved = true;
      state.dock = '';                 // 一旦真的拖动就先拉出来
      el.classList.add('no-anim', 'is-dragging');
    }
    if (e.cancelable) e.preventDefault();

    lastMoveX = e.clientX;
    lastMoveY = e.clientY;
    state.x = baseX + (lastMoveX - startX);
    state.y = baseY + (lastMoveY - startY);
    clamp();
    render(false);
  }

  function onUp(e) {
    if (!dragging) return;
    if (e && e.pointerId !== undefined && e.pointerId !== pid) return;

    dragging = false;
    pid = null;
    el.classList.remove('no-anim', 'is-dragging');
    document.documentElement.style.cursor = '';
    hoverOn = false;
    el.classList.remove('is-hover');
    if (rafId) { cancelAnimationFrame(rafId); rafId = 0; }

    var cx = e && e.clientX !== undefined ? e.clientX : lastMoveX;
    var cy = e && e.clientY !== undefined ? e.clientY : lastMoveY;

    if (moved) {
      settle();
      blockNextClick(320);
      return;
    }

    // —— 没有位移：这是一次单击 ——
    tapFeedback();
    showNextTip();
    var now = Date.now();
    if (now - lastTap < TAP_MS &&
        Math.abs(cx - lastTapX) < TAP_DIST &&
        Math.abs(cy - lastTapY) < TAP_DIST) {
      lastTap = 0;
      hideTip();
      goChat();
      blockNextClick(320);
      return;
    }
    lastTap = now;
    lastTapX = cx;
    lastTapY = cy;
    blockNextClick(240);
  }

  function scheduleHover(e) {
    if (hoverRaf) return;
    hoverRaf = requestAnimationFrame(function () {
      hoverRaf = 0;
      var on = hit(e.clientX, e.clientY, 2);
      if (on === hoverOn) return;
      hoverOn = on;
      document.documentElement.style.cursor = on ? 'grab' : '';
      el.classList.toggle('is-hover', on);
    });
  }

  function resetHover() {
    if (!hoverOn) return;
    hoverOn = false;
    document.documentElement.style.cursor = '';
    el.classList.remove('is-hover');
  }

  function onTouchStart(e) {
    var t = e.touches && e.touches[0];
    if (!t) return;
    if (hit(t.clientX, t.clientY, 6) && e.cancelable) e.preventDefault();
  }

  function onTouchMove(e) {
    if (dragging && e.cancelable) e.preventDefault();
  }

  function onResize() {
    restore();
    render(false);
  }

  function onKey(e) {
    if (document.activeElement !== el) return;
    if (e.key !== 'Enter' && e.key !== ' ') return;
    e.preventDefault();
    goChat();
  }

  /* ------------------------- 提示气泡 ------------------------- */

  function showTip(text, holdMs) {
    if (!tip || state.dock) return;
    tip.textContent = text;
    tip.classList.toggle('tip-right', state.x < 150);
    tip.classList.add('show');
    tipTimer = setTimeout(hideTip, holdMs || 5200);
  }

  function showNextTip() {
    if (state.dock) return;
    var pool = currentTips();
    showTip(pool[tipIndex % pool.length], 5200);
    tipIndex++;
  }

  function maybeShowTip() {
    var seen = false;
    try { seen = sessionStorage.getItem('aiip:tip') === '1'; } catch (e) { seen = false; }
    if (!seen && !state.dock) {
    // 首次进入自动冒一句"贴合当前场景"的问候语
    tip.classList.toggle('tip-right', state.x < 150);
    tipTimer = setTimeout(function () {
      if (!tip || state.dock) return;
      var pool = currentTips();
      showTip(pool[0], 6000);
      tipIndex = 1;   // 问候已用第 1 条，下轮从下一条开始，避免重复
      try { sessionStorage.setItem('aiip:tip', '1'); } catch (e) { /* ignore */ }
    }, 1400);
    }
    // 之后周期性冒健康小贴士（未贴边、当前无气泡时）
    scheduleAuto();
  }

  function scheduleAuto() {
    if (autoTimer) clearTimeout(autoTimer);
    autoTimer = setTimeout(function () {
      if (!state.dock && tip && !tip.classList.contains('show')) showNextTip();
      scheduleAuto();
    }, 7000);
  }

  function hideTip() {
    if (tipTimer) { clearTimeout(tipTimer); tipTimer = 0; }
    if (tip) tip.classList.remove('show');
  }

  /* ------------------------- 启动 ------------------------- */

  function init() {
    if (!document.body) return;
    injectStyle();
    build();

    window.addEventListener('pointerdown', onDown, true);
    window.addEventListener('pointermove', onMove, { passive: false, capture: true });
    window.addEventListener('pointerup', onUp, true);
    window.addEventListener('pointercancel', onUp, true);
    window.addEventListener('touchstart', onTouchStart, { passive: false });
    window.addEventListener('touchmove', onTouchMove, { passive: false });
    window.addEventListener('resize', onResize);
    window.addEventListener('orientationchange', onResize);
    window.addEventListener('blur', resetHover);
    document.addEventListener('pointerleave', resetHover);
    el.addEventListener('keydown', onKey);

    // 便于自测/排查，不影响正常使用
    window.__aiIpDebug = {
      hit: hit,
      state: state,
      rect: rect
    };
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
