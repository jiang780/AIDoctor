async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  const text = await response.text();
  let data = text ? JSON.parse(text) : {};

  if (!response.ok) {
    throw new Error(data.message || '请求失败');
  }

  return data;
}

function applyAuth() {
  const currentPage = window.location.pathname.split('/').pop() || 'index.html';
  const user = JSON.parse(localStorage.getItem('aiDoctorUser') || 'null');
  const isLoggedIn = !!user && user.loggedIn;

  const doctorPages = ['doctor-login.html', 'doctor-register.html', 'doctor-console.html', 'doctor-profile.html', 'doctor-prescribe.html'];
  const isDoctorPage = doctorPages.includes(currentPage);

  if (!isDoctorPage && currentPage !== 'login.html' && !isLoggedIn) {
    window.location.href = 'login.html';
    return;
  }

  if (currentPage === 'login.html' && isLoggedIn) {
    window.location.href = 'index.html';
    return;
  }

  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      localStorage.removeItem('aiDoctorUser');
      window.location.href = 'login.html';
    });
  }

  const headerActions = document.querySelector('.topbar .header-actions');
  if (!isDoctorPage && headerActions && !document.getElementById('userCenterNavLink')) {
    const link = document.createElement('a');
    link.id = 'userCenterNavLink';
    link.href = 'profile.html';
    link.className = 'user-center-link';
    link.textContent = '👤 用户中心';
    headerActions.appendChild(link);
    updateNotificationBadge();
  }

  if (currentPage === 'doctor-console.html' && headerActions && !document.getElementById('doctorUserCenterLink')) {
    const link = document.createElement('a');
    link.id = 'doctorUserCenterLink';
    link.href = 'doctor-profile.html';
    link.className = 'user-center-link';
    link.textContent = '👤 用户中心';
    headerActions.appendChild(link);
  }

  const loginForm = document.getElementById('loginForm');
  if (loginForm) {
    const errorBox = document.getElementById('loginError');
    loginForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const username = document.getElementById('username').value.trim();
      const password = document.getElementById('password').value.trim();

      try {
        const result = await requestJson('/api/login', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
        });

        if (!result.success) {
          throw new Error(result.message || '登录失败');
        }

        localStorage.setItem('aiDoctorUser', JSON.stringify({ username, loggedIn: true }));
        window.location.href = 'index.html';
      } catch (error) {
        if (errorBox) {
          errorBox.textContent = error.message;
        }
      }
    });
  }

  const registerForm = document.getElementById('registerForm');
  if (registerForm) {
    const errorBox = document.getElementById('registerError');
    registerForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const username = document.getElementById('registerUsername').value.trim();
      const password = document.getElementById('registerPassword').value.trim();
      const confirmPassword = document.getElementById('confirmPassword').value.trim();

      if (!username || !password) {
        errorBox.textContent = '账号和密码不能为空';
        return;
      }

      if (password !== confirmPassword) {
        errorBox.textContent = '两次密码输入不一致';
        return;
      }

      try {
        const result = await requestJson('/api/register', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
        });

        if (!result.success) {
          throw new Error(result.message || '注册失败');
        }

        document.getElementById('username').value = username;
        document.getElementById('password').value = password;
        switchAuthMode('login');
        if (errorBox) {
          errorBox.textContent = '';
        }
        const loginBox = document.getElementById('loginError');
        if (loginBox) {
          loginBox.textContent = '注册成功，请使用新账号登录';
        }
      } catch (error) {
        if (errorBox) {
          errorBox.textContent = error.message;
        }
      }
    });
  }

  const switchButtons = document.querySelectorAll('.switch-btn');
  switchButtons.forEach((button) => {
    button.addEventListener('click', () => {
      switchAuthMode(button.dataset.mode);
    });
  });
}

function switchAuthMode(mode) {
  const isLogin = mode === 'login';
  const subtitle = document.getElementById('authSubtitle');
  const loginForm = document.getElementById('loginForm');
  const registerForm = document.getElementById('registerForm');
  const switchButtons = document.querySelectorAll('.switch-btn');

  if (subtitle) {
    subtitle.textContent = isLogin ? '欢迎登录健康管理平台' : '创建您的健康管理账号';
  }

  if (loginForm) loginForm.classList.toggle('active', isLogin);
  if (registerForm) registerForm.classList.toggle('active', !isLogin);

  switchButtons.forEach((button) => {
    const active = button.dataset.mode === mode;
    button.classList.toggle('active', active);
  });
}

const medicineGrid = document.getElementById('medicineGrid');
const medicineSearchInput = document.getElementById('medicineSearch');
const diseaseGrid = document.getElementById('diseaseGrid');
const diseaseSearchInput = document.getElementById('diseaseSearch');
const symptomOptions = document.getElementById('symptomOptions');
const quickQuestionOptions = document.getElementById('quickQuestionOptions');
const durationInput = document.getElementById('duration');
const durationValue = document.getElementById('durationValue');
const diagnoseBtn = document.getElementById('diagnoseBtn');
const aiQuestionInput = document.getElementById('aiQuestionInput');
const aiConsultBtn = document.getElementById('aiConsultBtn');
let medicineDataCache = [];
let diseaseDataCache = [];

const basicQuestions = [
  '发热',
  '咳嗽',
  '腹泻',
  '胸闷',
  '头痛',
  '乏力',
  '睡眠差',
  '食欲下降',
  '过敏史',
  '慢性病史'
];

applyAuth();

async function renderMedicine(filterText = '') {
  if (!medicineGrid) return;

  try {
    if (!medicineDataCache.length) {
      medicineDataCache = await requestJson('/api/medicines');
    }

    const keyword = String(filterText || '').trim().toLowerCase();
    const data = medicineDataCache.filter((item) => {
      if (!keyword) return true;
      return [item.name, item.category, item.use, item.price]
        .join(' ')
        .toLowerCase()
        .includes(keyword);
    });

    if (!data.length) {
      medicineGrid.innerHTML = '<p>没有找到相关药品，请换个关键词试试。</p>';
      return;
    }

    medicineGrid.innerHTML = data.slice(0, 6).map((item) => `
      <a class="info-card" href="medicine-detail.html?name=${encodeURIComponent(item.name)}">
        <span class="badge">${item.category}</span>
        <h3>${item.name}</h3>
        <p>${item.use}</p>
        <div class="meta-row">
          <span>适用场景</span>
          <strong>${item.price}</strong>
        </div>
      </a>
    `).join('');
  } catch (error) {
    medicineGrid.innerHTML = '<p>药品数据加载失败，请稍后重试。</p>';
  }
}

async function renderDisease(filterText = '') {
  if (!diseaseGrid) return;

  try {
    if (!diseaseDataCache.length) {
      diseaseDataCache = await requestJson('/api/diseases');
    }

    const keyword = String(filterText || '').trim().toLowerCase();
    const data = diseaseDataCache.filter((item) => {
      if (!keyword) return true;
      return [item.name, item.tag, item.description].join(' ').toLowerCase().includes(keyword);
    });

    if (!data.length) {
      diseaseGrid.innerHTML = '<p>没有找到相关病种，请换个关键词试试。</p>';
      return;
    }

    diseaseGrid.innerHTML = data.slice(0, 6).map((item) => `
      <a class="info-card" href="disease-detail.html?name=${encodeURIComponent(item.name)}">
        <span class="badge">${item.tag}</span>
        <h3>${item.name}</h3>
        <p>${item.description}</p>
      </a>
    `).join('');
  } catch (error) {
    diseaseGrid.innerHTML = '<p>病种数据加载失败，请稍后重试。</p>';
  }
}

function getQueryParam(key) {
  return new URLSearchParams(window.location.search).get(key);
}

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
};

const MEDICINE_ADVICE = {
  '退烧止痛': '请按说明书或医嘱服用，避免超量；用于退烧连续不超过3天，止痛不超过5天。',
  '感冒类': '对症治疗，注意休息、多饮水；如持续高热或症状加重请及时就医。',
  '抗感染': '抗生素需在医生指导下使用，按疗程服用，切勿自行停药或滥用。',
  '过敏类': '用药期间避免驾驶等需集中注意力的活动；出现皮疹、呼吸困难请立即停药就医。',
  '抗病毒': '抗病毒药应在发病早期使用效果更佳，具体请遵医嘱。',
  '消炎止痛': '饭后服用可减轻胃肠刺激；胃溃疡患者慎用，出现黑便或腹痛请及时就医。',
  '止咳类': '多喝水、保持空气湿润；咳嗽持续或伴胸痛、发热时请及时就医。',
  '营养补充': '按推荐量服用，不能替代正常饮食；如需长期补充请咨询医生。',
  '肠胃调理': '按说明服用，注意饮食清淡；腹泻持续或出现脱水请及时就医。',
  '中成药': '辨证使用，孕妇、儿童及慢性病患者请在医生指导下服用。',
  '止痛类': '短期缓解症状，避免长期依赖；疼痛原因不明或加重请及时就医。',
  '慢病类': '需长期规律用药，定期复诊，不要自行停药或调整剂量。',
};

const GENERIC_ADVICE = '请在医生指导下使用，注意休息、均衡饮食；如症状持续或加重，请及时就医。';

function renderMedicineDetail() {
  const box = document.getElementById('medicineDetailBox');
  if (!box) return;

  const name = getQueryParam('name');
  const titleEl = document.getElementById('medicineDetailTitle');
  if (titleEl && name) titleEl.textContent = name;

  requestJson('/api/medicines')
    .then((data) => {
      const item = data.find((m) => m.name === name);
      if (!item) {
        box.innerHTML = '<p>未找到该药品。</p>';
        return;
      }
      const advice = MEDICINE_ADVICE[item.category] || GENERIC_ADVICE;
      box.innerHTML = `
        <span class="detail-badge">${item.category}</span>
        <h2>${item.name}</h2>
        <p>${item.use}</p>
        <div class="meta-row">
          <span>参考价格</span>
          <strong>${item.price}</strong>
        </div>
        <div class="doctor-advice">
          <h3>医生建议</h3>
          <p>${advice}</p>
        </div>
      `;
    })
    .catch(() => {
      box.innerHTML = '<p>药品信息加载失败，请稍后重试。</p>';
    });
}

function renderDiseaseDetail() {
  const box = document.getElementById('diseaseDetailBox');
  if (!box) return;

  const name = getQueryParam('name');
  const titleEl = document.getElementById('diseaseDetailTitle');
  if (titleEl && name) titleEl.textContent = name;

  requestJson('/api/diseases')
    .then((data) => {
      const item = data.find((d) => d.name === name);
      if (!item) {
        box.innerHTML = '<p>未找到该病种。</p>';
        return;
      }
      const advice = DISEASE_ADVICE[item.tag] || GENERIC_ADVICE;
      box.innerHTML = `
        <span class="detail-badge">${item.tag}</span>
        <h2>${item.name}</h2>
        <p>${item.description}</p>
        <div class="doctor-advice">
          <h3>医生建议</h3>
          <p>${advice}</p>
        </div>
      `;
    })
    .catch(() => {
      box.innerHTML = '<p>病种信息加载失败，请稍后重试。</p>';
    });
}

async function renderSymptoms() {
  if (!symptomOptions) return;

  try {
    const data = await requestJson('/api/symptoms');
    symptomOptions.innerHTML = data.map((symptom) => `
      <button class="symptom-tag" data-symptom="${symptom}" type="button">${symptom}</button>
    `).join('');

    symptomOptions.querySelectorAll('.symptom-tag').forEach((tag) => {
      tag.addEventListener('click', () => {
        tag.classList.toggle('active');
      });
    });
  } catch (error) {
    symptomOptions.innerHTML = '<p>症状数据加载失败。</p>';
  }
}

function renderQuickQuestions() {
  if (!quickQuestionOptions) return;

  quickQuestionOptions.innerHTML = basicQuestions.map((item) => `
    <button class="symptom-tag" data-question="${item}" type="button">${item}</button>
  `).join('');

  quickQuestionOptions.querySelectorAll('.symptom-tag').forEach((tag) => {
    tag.addEventListener('click', () => {
      tag.classList.toggle('active');
    });
  });
}

function getSelectedSymptoms() {
  if (!symptomOptions) return [];
  return [...symptomOptions.querySelectorAll('.symptom-tag.active')].map((tag) => tag.dataset.symptom).filter(Boolean);
}

function getSelectedBasicQuestions() {
  if (!quickQuestionOptions) return [];
  return [...quickQuestionOptions.querySelectorAll('.symptom-tag.active')].map((tag) => tag.dataset.question).filter(Boolean);
}

async function diagnose() {
  const errorBox = document.getElementById('diagnoseError');
  const selected = getSelectedSymptoms();

  if (!selected.length) {
    if (errorBox) errorBox.textContent = '请选择至少一个症状';
    return;
  }

  try {
    const data = await requestJson('/api/diagnose', {
      method: 'POST',
      body: JSON.stringify({
        symptoms: selected,
        duration: Number(durationInput.value),
      }),
    });

    sessionStorage.setItem('aiDoctorDiagnosis', JSON.stringify({
      disease: data.disease,
      recommendations: data.recommendations || [],
      duration: data.duration || Number(durationInput.value),
      symptoms: selected,
    }));

    window.location.href = 'result.html';
  } catch (error) {
    if (errorBox) errorBox.textContent = error.message;
  }
}

async function askHealthAI() {
  const errorBox = document.getElementById('aiError');
  const selectedBasics = getSelectedBasicQuestions();
  const customQuestion = aiQuestionInput ? aiQuestionInput.value.trim() : '';
  const selectedSymptoms = getSelectedSymptoms();

  if (!selectedBasics.length && !customQuestion && !selectedSymptoms.length) {
    if (errorBox) errorBox.textContent = '请选择一个基础问题，或填写您的健康情况';
    return;
  }

  try {
    const data = await requestJson('/api/ai-consult', {
      method: 'POST',
      body: JSON.stringify({
        basicQuestions: selectedBasics,
        symptoms: selectedSymptoms,
        question: customQuestion,
      }),
    });

    sessionStorage.setItem('aiDoctorAI', JSON.stringify({
      answer: data.answer,
      question: customQuestion,
      basicQuestions: selectedBasics,
      symptoms: selectedSymptoms,
    }));

    window.location.href = 'result.html';
  } catch (error) {
    if (errorBox) errorBox.textContent = error.message;
  }
}

function renderResultsPage() {
  const diagnosisBox = document.getElementById('diagnosisResultBox');
  const aiBox = document.getElementById('aiResultBox');

  if (diagnosisBox) {
    const raw = sessionStorage.getItem('aiDoctorDiagnosis');
    if (raw) {
      const d = JSON.parse(raw);
      diagnosisBox.innerHTML = `
        <h3>诊断结果</h3>
        <p><strong>可能患病：</strong>${d.disease}</p>
        <p class="recommendation"><strong>持续时间：</strong>${d.duration}天</p>
        <p class="recommendation"><strong>推荐药品：</strong>${(d.recommendations || []).join('、')}</p>
        <p class="recommendation"><strong>健康提示：</strong>根据您选择的症状，结果仅作参考，若出现高热、呼吸困难或持续加重，请及时就医。</p>
      `;
    }
  }

  if (aiBox) {
    const raw = sessionStorage.getItem('aiDoctorAI');
    if (raw) {
      const a = JSON.parse(raw);
      aiBox.innerHTML = `
        <h3>AI 健康问答</h3>
        <p>${a.answer}</p>
      `;
    }
  }
}

if (durationInput && durationValue) {
  durationInput.addEventListener('input', () => {
    durationValue.textContent = `${durationInput.value}天`;
  });
}

if (medicineSearchInput && medicineGrid) {
  medicineSearchInput.addEventListener('input', (event) => {
    renderMedicine(event.target.value);
  });
}

if (diseaseSearchInput && diseaseGrid) {
  diseaseSearchInput.addEventListener('input', (event) => {
    renderDisease(event.target.value);
  });
}

if (diagnoseBtn) {
  diagnoseBtn.addEventListener('click', diagnose);
}

if (aiConsultBtn) {
  aiConsultBtn.addEventListener('click', askHealthAI);
}

const healthAssistantTips = [
  {
    symptoms: '咳嗽、发热、乏力',
    judgment: '可能为上呼吸道感染',
    advice: '对乙酰氨基酚、含片',
  },
  {
    symptoms: '鼻塞、流涕、打喷嚏',
    judgment: '可能为过敏性鼻炎',
    advice: '生理盐水鼻用喷雾、抗过敏药',
  },
  {
    symptoms: '腹痛、腹泻、恶心',
    judgment: '可能为急性肠胃炎',
    advice: '补液盐、清淡饮食、休息',
  },
  {
    symptoms: '胸闷、乏力、睡眠差',
    judgment: '需密切观察并排查疲劳或呼吸问题',
    advice: '规律作息、保持通风、及时复诊',
  }
];

function renderHealthAssistant(index = 0) {
  const container = document.getElementById('healthAssistantContent');
  if (!container) return;

  const item = healthAssistantTips[index % healthAssistantTips.length];
  container.innerHTML = `
    <div class="mini-card">
      <span>症状</span>
      <strong>${item.symptoms}</strong>
    </div>
    <div class="mini-card">
      <span>智能判断</span>
      <strong>${item.judgment}</strong>
    </div>
    <div class="mini-card">
      <span>推荐用药</span>
      <strong>${item.advice}</strong>
    </div>
  `;
}

const healthRefreshBtn = document.getElementById('healthRefreshBtn');
if (healthRefreshBtn) {
  let healthIndex = 0;
  healthRefreshBtn.addEventListener('click', () => {
    healthIndex += 1;
    renderHealthAssistant(healthIndex);
    healthRefreshBtn.classList.remove('spin');
    void healthRefreshBtn.offsetWidth;
    healthRefreshBtn.classList.add('spin');
    setTimeout(() => healthRefreshBtn.classList.remove('spin'), 400);
  });
}

const chatForm = document.getElementById('chatForm');
const chatInput = document.getElementById('chatInput');
const chatMessages = document.getElementById('chatMessages');

function appendChatMessage(role, text) {
  const wrapper = document.createElement('div');
  wrapper.className = 'chat-msg ' + (role === 'user' ? 'user' : 'bot');
  const avatar = document.createElement('div');
  avatar.className = 'chat-avatar';
  avatar.textContent = role === 'user' ? '我' : 'AI';
  const bubble = document.createElement('div');
  bubble.className = 'chat-bubble';
  bubble.textContent = text;
  wrapper.appendChild(avatar);
  wrapper.appendChild(bubble);
  chatMessages.appendChild(wrapper);
  chatMessages.scrollTop = chatMessages.scrollHeight;
  return bubble;
}

async function sendChat() {
  const text = chatInput.value.trim();
  if (!text) return;

  appendChatMessage('user', text);
  chatInput.value = '';
  saveChatRecord(text);

  const thinkingBubble = appendChatMessage('bot', '正在思考…');

  try {
    const data = await requestJson('/api/ai-consult', {
      method: 'POST',
      body: JSON.stringify({ question: text, basicQuestions: [], symptoms: [] }),
    });
    const source = data.source || 'AI 健康顾问';
    thinkingBubble.innerHTML = escapeHtml(data.answer || '抱歉，暂时没有给出建议。') +
      '<div class="answer-source">—— 来源：' + escapeHtml(source) + ' · 仅供参考，不构成医疗诊断</div>';
  } catch (error) {
    thinkingBubble.textContent = error.message || '出错了，请稍后重试。';
  }
  chatMessages.scrollTop = chatMessages.scrollHeight;
}

function saveChatRecord(text) {
  const user = getCurrentUser();
  if (!user || !user.username) return;
  requestJson('/api/records', {
    method: 'POST',
    body: JSON.stringify({ username: user.username, content: text }),
  }).catch(() => {});
}

if (chatForm) {
  chatForm.addEventListener('submit', (event) => {
    event.preventDefault();
    sendChat();
  });
}

function getCurrentUser() {
  return JSON.parse(localStorage.getItem('aiDoctorUser') || 'null');
}

async function renderProfilePage() {
  const form = document.getElementById('profileForm');
  if (!form) return;

  const user = getCurrentUser();
  const titleEl = document.getElementById('profileUserName');
  if (titleEl && user) titleEl.textContent = user.username + ' 的个人健康档案';
  if (!user || !user.username) return;

  try {
    const data = await requestJson('/api/profile?username=' + encodeURIComponent(user.username));
    const profile = data.profile || {};
    for (const [key, value] of Object.entries(profile)) {
      const field = form.elements[key];
      if (field && value) field.value = value;
    }
  } catch (error) {
    // 读取失败时保持表单为空
  }
}

function setupProfileForm() {
  const form = document.getElementById('profileForm');
  if (!form) return;

  const msg = document.getElementById('profileMsg');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const user = getCurrentUser();
    if (!user || !user.username) {
      if (msg) msg.textContent = '请先登录';
      return;
    }

    const data = { username: user.username };
    for (const field of form.elements) {
      if (field.name) data[field.name] = String(field.value || '').trim();
    }

    try {
      await requestJson('/api/profile', {
        method: 'POST',
        body: JSON.stringify(data),
      });
      if (msg) {
        msg.textContent = '健康档案已保存';
        setTimeout(() => { msg.textContent = ''; }, 2000);
      }
    } catch (error) {
      if (msg) msg.textContent = error.message || '保存失败';
    }
  });
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function renderRecords() {
  const list = document.getElementById('recordList');
  if (!list) return;

  const user = getCurrentUser();
  if (!user || !user.username) return;

  try {
    const data = await requestJson('/api/records?username=' + encodeURIComponent(user.username));
    const records = data.records || [];
    if (!records.length) {
      list.innerHTML = '<p class="record-empty">暂无问诊记录。</p>';
      return;
    }

    const latest = records.slice(0, 3);
    const items = latest.map((r) => `
      <div class="record-item">
        <div class="record-time">${r.createdAt}</div>
        <div class="record-content">${escapeHtml(r.content)}</div>
      </div>
    `).join('');

    list.innerHTML = records.length > 3
      ? items + '<a class="record-more" href="records.html">显示更多</a>'
      : items;
  } catch (error) {
    list.innerHTML = '<p class="record-empty">记录加载失败。</p>';
  }
}

async function renderAllRecords() {
  const list = document.getElementById('recordsList');
  if (!list) return;

  const user = getCurrentUser();
  if (!user || !user.username) return;

  try {
    const data = await requestJson('/api/records?username=' + encodeURIComponent(user.username));
    const records = data.records || [];
    if (!records.length) {
      list.innerHTML = '<p class="record-empty">暂无问诊记录。</p>';
      return;
    }
    list.innerHTML = records.map((r) => `
      <div class="record-item">
        <div class="record-time">${r.createdAt}</div>
        <div class="record-content">${escapeHtml(r.content)}</div>
      </div>
    `).join('');
  } catch (error) {
    list.innerHTML = '<p class="record-empty">记录加载失败。</p>';
  }
}

const SPECIALTIES = ['呼吸科', '消化科', '内科', '外科', '儿科', '皮肤科', '神经内科', '全科'];

function populateSpecialtySelects() {
  document.querySelectorAll('.specialty-select').forEach((select) => {
    if (select.dataset.populated === '1') return;
    SPECIALTIES.forEach((s) => {
      const opt = document.createElement('option');
      opt.value = s;
      opt.textContent = s;
      select.appendChild(opt);
    });
    select.dataset.populated = '1';
  });
}

function getDoctor() {
  return JSON.parse(localStorage.getItem('aiDoctorDoc') || 'null');
}

function setupDoctorRegisterForm() {
  const form = document.getElementById('doctorRegisterForm');
  if (!form) return;
  const msg = document.getElementById('doctorRegisterMsg');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const data = {
      username: form.username.value.trim(),
      password: form.password.value.trim(),
      name: form.name.value.trim(),
      specialty: form.specialty.value,
    };
    if (!data.username || !data.password || !data.name || !data.specialty) {
      if (msg) msg.textContent = '请填写完整信息';
      return;
    }
    try {
      await requestJson('/api/doctor/register', { method: 'POST', body: JSON.stringify(data) });
      if (msg) msg.textContent = '注册成功，即将跳转登录';
      setTimeout(() => { window.location.href = 'doctor-login.html'; }, 800);
    } catch (error) {
      if (msg) msg.textContent = error.message;
    }
  });
}

function setupDoctorLoginForm() {
  const form = document.getElementById('doctorLoginForm');
  if (!form) return;
  const msg = document.getElementById('doctorLoginMsg');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const username = form.username.value.trim();
    const password = form.password.value.trim();
    try {
      const result = await requestJson('/api/doctor/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      });
      if (!result.success) throw new Error(result.message || '登录失败');
      localStorage.setItem('aiDoctorDoc', JSON.stringify({ ...result.doctor, loggedIn: true }));
      window.location.href = 'doctor-console.html';
    } catch (error) {
      if (msg) msg.textContent = error.message;
    }
  });
}

function setupDoctorLogout() {
  const btn = document.getElementById('doctorLogoutBtn');
  if (!btn) return;
  btn.addEventListener('click', () => {
    localStorage.removeItem('aiDoctorDoc');
    window.location.href = 'doctor-login.html';
  });
}

function renderDoctorInfo() {
  const box = document.getElementById('doctorInfoBox');
  if (!box) return;

  const doctor = getDoctor();
  if (!doctor || !doctor.loggedIn) {
    window.location.href = 'doctor-login.html';
    return;
  }

  box.innerHTML = `
    <div class="doctor-info-item"><span>姓名</span><strong>${escapeHtml(doctor.name)}</strong></div>
    <div class="doctor-info-item"><span>账号</span><strong>${escapeHtml(doctor.username)}</strong></div>
    <div class="doctor-info-item"><span>接诊方向</span><strong>${escapeHtml(doctor.specialty)}</strong></div>
  `;
}

async function renderDoctorConsole() {
  const container = document.getElementById('doctorConsultList');
  if (!container) return;

  const doctor = getDoctor();
  if (!doctor || !doctor.loggedIn) {
    window.location.href = 'doctor-login.html';
    return;
  }

  try {
    const data = await requestJson('/api/doctor/consultations?username=' + encodeURIComponent(doctor.username));
    const list = data.consultations || [];
    if (!list.length) {
      container.innerHTML = '<p class="record-empty">暂无接诊。</p>';
      return;
    }
    container.innerHTML = list.map((c) => `
      <div class="consult-card">
        <div class="consult-head">
          <span class="consult-badge">${c.specialty}</span>
          <span class="consult-status">${c.status}</span>
        </div>
        <div class="consult-meta">患者：${escapeHtml(c.patient)} · ${c.createdAt}</div>
        <div class="consult-condition">${escapeHtml(c.condition)}</div>
        ${c.status === '待回复'
          ? `<div class="consult-reply">
               <textarea id="replyInput_${c.id}" rows="3" placeholder="输入回复内容"></textarea>
               <button class="primary-btn full" data-reply-id="${c.id}" type="button">发送回复</button>
             </div>`
          : `<div class="consult-replied"><strong>我的回复：</strong>${escapeHtml(c.reply)}</div>`}
      </div>
    `).join('');

    container.querySelectorAll('button[data-reply-id]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const id = btn.dataset.replyId;
        const reply = document.getElementById('replyInput_' + id).value.trim();
        if (!reply) {
          alert('请输入回复内容');
          return;
        }
        try {
          await requestJson('/api/doctor/reply', { method: 'POST', body: JSON.stringify({ id: Number(id), reply }) });
          renderDoctorConsole();
        } catch (error) {
          alert(error.message);
        }
      });
    });
  } catch (error) {
    container.innerHTML = '<p class="record-empty">接诊列表加载失败。</p>';
  }
}

function setupDoctorMode() {
  const switchButtons = document.querySelectorAll('.mode-btn');
  const aiPanel = document.getElementById('aiPanel');
  const realPanel = document.getElementById('realPanel');
  const submitBtn = document.getElementById('consultSubmit');

  switchButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.mode;
      switchButtons.forEach((b) => b.classList.toggle('active', b === btn));
      if (aiPanel) aiPanel.hidden = mode !== 'ai';
      if (realPanel) realPanel.hidden = mode !== 'real';
    });
  });

  if (submitBtn) {
    submitBtn.addEventListener('click', async () => {
      const user = getCurrentUser();
      if (!user || !user.username) {
        alert('请先登录');
        return;
      }
      const condition = document.getElementById('consultCondition').value.trim();
      const msg = document.getElementById('consultMsg');
      if (!condition) {
        if (msg) msg.textContent = '请填写患者状况';
        return;
      }
      try {
        const result = await requestJson('/api/consult', {
          method: 'POST',
          body: JSON.stringify({ patient: user.username, condition }),
        });
        if (msg) {
          msg.textContent = result.message;
          msg.classList.add('success');
        }
        document.getElementById('consultCondition').value = '';
      } catch (error) {
        if (msg) {
          msg.textContent = error.message;
          msg.classList.remove('success');
        }
      }
    });
  }
}

async function renderPatientConsultations() {
  const list = document.getElementById('patientConsultList');
  if (!list) return;

  const user = getCurrentUser();
  if (!user || !user.username) return;

  try {
    const data = await requestJson('/api/consultations?username=' + encodeURIComponent(user.username));
    const consultations = data.consultations || [];
    if (!consultations.length) {
      list.innerHTML = '<p class="record-empty">暂无真人医生接诊记录。</p>';
      return;
    }
    list.innerHTML = consultations.map((c) => `
      <div class="consult-card">
        <div class="consult-head">
          <span class="consult-badge">${c.specialty}</span>
          <span class="consult-status">${c.status}</span>
        </div>
        <div class="consult-meta">医生：${escapeHtml(c.doctorName)} · ${c.createdAt}</div>
        <div class="consult-condition">${escapeHtml(c.condition)}</div>
        ${c.status === '已回复'
          ? `<div class="consult-replied"><strong>医生回复：</strong>${escapeHtml(c.reply)}</div>`
          : '<div class="consult-pending">等待医生回复…</div>'}
      </div>
    `).join('');
  } catch (error) {
    list.innerHTML = '<p class="record-empty">接诊记录加载失败。</p>';
  }
  updateNotificationBadge();
}

async function updateNotificationBadge() {
  const link = document.getElementById('userCenterNavLink');
  const user = getCurrentUser();
  if (!link || !user || !user.username) return;

  try {
    const data = await requestJson('/api/notifications?username=' + encodeURIComponent(user.username));
    const count = data.count || 0;
    const old = link.querySelector('.notify-badge');
    if (old) old.remove();
    if (count > 0) {
      const badge = document.createElement('span');
      badge.className = 'notify-badge';
      badge.textContent = count > 99 ? '99+' : String(count);
      link.appendChild(badge);
    }
  } catch (error) {
    // 忽略通知获取失败
  }
}

renderHealthAssistant();
renderMedicine();
renderDisease();
renderSymptoms();
renderQuickQuestions();
renderResultsPage();
renderMedicineDetail();
renderDiseaseDetail();
renderProfilePage();
setupProfileForm();
renderRecords();
renderAllRecords();
renderPatientConsultations();
populateSpecialtySelects();
setupDoctorRegisterForm();
setupDoctorLoginForm();
setupDoctorLogout();
setupDoctorMode();
renderDoctorInfo();
renderDoctorConsole();

/* ---------------- 医生端：给患者下达用药方案 ---------------- */
function setupDoctorPrescribe() {
  const form = document.getElementById('rxForm');
  if (!form) return;

  const doctor = getDoctor();
  if (!doctor || !doctor.loggedIn) {
    window.location.href = 'doctor-login.html';
    return;
  }

  const patientList = document.getElementById('rxPatientList');
  const patientSearch = document.getElementById('rxPatientSearch');
  const selectedBox = document.getElementById('rxSelected');
  const msg = document.getElementById('rxMsg');
  const listBox = document.getElementById('rxList');
  const countBox = document.getElementById('rxCount');
  const patientSafety = document.getElementById('rxPatientSafety');
  const safetyBox = document.getElementById('rxSafety');
  const medInput = document.getElementById('rxMedicine');
  let patients = [];
  let selected = null;
  let members = [];
  let selectedMemberId = 0;
  let safetyPreview = null;
  let checkTimer = null;
  let checkSeq = 0;

  const today = new Date();
  document.getElementById('rxStart').value = today.toISOString().slice(0, 10);

  // 日期输入：用文本框显示「年-月-日」清晰格式，点日历图标仍可调起原生选择器
  function wireDatePair(textId, nativeId) {
    const text = document.getElementById(textId);
    const native = document.getElementById(nativeId);
    if (!text || !native) return;
    native.value = /^\d{4}-\d{2}-\d{2}$/.test(text.value) ? text.value : '';
    // 点图标/输入框右侧 -> 打开原生选择器；选完后把 ISO 写回文本框
    const open = () => { native.showPicker ? native.showPicker() : native.click(); };
    native.addEventListener('change', () => { if (native.value) text.value = native.value; });
    text.addEventListener('click', (e) => { if (e.offsetX > text.clientWidth - 36) open(); });
    const icon = text.parentElement.querySelector('.rx-date-icon');
    if (icon) icon.addEventListener('click', open);
  }
  wireDatePair('rxStart', 'rxStartNative');
  wireDatePair('rxEnd', 'rxEndNative');

  // 把各种手写日期统一成 YYYY-MM-DD（空值返回 ''）
  function normDate(v) {
    v = (v || '').trim();
    if (!v) return '';
    let m = v.match(/^(\d{4})[/\-.年](\d{1,2})[/\-.月](\d{1,2})日?$/);
    if (m) return m[1] + '-' + String(m[2]).padStart(2, '0') + '-' + String(m[3]).padStart(2, '0');
    return v;
  }

  function note(text, ok) {
    msg.textContent = text;
    msg.classList.toggle('success', !!ok);
    msg.classList.toggle('error', !ok);
  }

  function selectPatient(p) {
    selected = p || null;
    selectedMemberId = 0;
    members = [];
    selectedBox.textContent = selected
      ? '当前患者：' + (selected.name || selected.username) + '（' + selected.username + '）'
      : '尚未选择患者，请先在左侧点选一位';
    selectedBox.classList.toggle('active', !!selected);
    renderPatients();
    safetyPreview = null;
    hideSafety();
    loadPatientMembers();
    loadPatientSafety();
    // 换患者时如果药名已经填了，按新患者重新预检
    const name = medInput.value.trim();
    if (name.length >= 2) runCheck(name);
  }

  /* ---------- 用药安全：患者现状 + 开药即时预检 ---------- */
  function hideSafety() {
    safetyBox.hidden = true;
    safetyBox.innerHTML = '';
    safetyBox.className = 'rx-safety';
  }

  async function loadPatientSafety() {
    if (!selected) {
      patientSafety.hidden = true;
      return;
    }
    patientSafety.hidden = false;
    patientSafety.className = 'rx-safety-strip';
    patientSafety.textContent = '正在读取该患者的用药情况…';
    try {
      const qs = '/api/doctor/safety?patient=' + encodeURIComponent(selected.username)
        + (selectedMemberId ? '&memberId=' + selectedMemberId : '');
      const data = await requestJson(qs);
      const stats = data.stats || {};
      const drugs = stats.drugs || 0;
      if (data.empty || !drugs) {
        patientSafety.className = 'rx-safety-strip green';
        patientSafety.innerHTML = '该患者药箱暂无在用药记录';
        return;
      }
      patientSafety.className = 'rx-safety-strip ' + (data.level || 'green');
      const red = stats.red || 0;
      const amber = stats.amber || 0;
      const detail = red > 0 ? `${red} 项需立即处理`
        : (amber > 0 ? `${amber} 项需留意` : '未发现冲突');
      patientSafety.innerHTML = `<strong>当前用药安全：${escapeHtml(data.levelLabel || '')}</strong>`
        + `<span>在用药 ${drugs} 种 · ${escapeHtml(detail)}</span>`;
    } catch (error) {
      patientSafety.className = 'rx-safety-strip';
      patientSafety.textContent = '患者用药情况读取失败，仍可继续开方';
    }
  }

  async function loadPatientMembers() {
    const wrap = document.getElementById('rxMemberWrap');
    const chips = document.getElementById('rxMemberChips');
    if (!selected) {
      wrap.hidden = true;
      return;
    }
    try {
      const data = await requestJson('/api/doctor/patient-members?patient=' + encodeURIComponent(selected.username));
      members = data.members || [];
      if (!members.length) {
        wrap.hidden = true;
        selectedMemberId = 0;
        loadPlans();
        return;
      }
      const def = members.find((m) => m.isDefault) || members[0];
      selectedMemberId = def.id;
      loadPlans();
      wrap.hidden = false;
      chips.innerHTML = members.map((m) => `
        <button type="button" class="rx-member ${m.id === selectedMemberId ? 'selected' : ''}" data-id="${m.id}">
          ${escapeHtml(m.name)}${m.relation ? ' · ' + escapeHtml(m.relation) : ''}${m.isChild ? ' · 儿童' : ''}
        </button>`).join('');
      chips.querySelectorAll('.rx-member').forEach((btn) => {
        btn.addEventListener('click', () => {
          selectedMemberId = Number(btn.dataset.id);
          chips.querySelectorAll('.rx-member').forEach((b) => b.classList.toggle('selected', b === btn));
          loadPatientSafety();
          loadPlans();
          const name = medInput.value.trim();
          if (name.length >= 2) runCheck(name);
        });
      });
    } catch (error) {
      wrap.hidden = true;
    }
  }

  // 解析剂量文本："8 mL" -> {value:8, unit:'mL'}；"2 袋" -> {value:2, unit:'袋'}
  function parseDose(text) {
    if (!text) return null;
    const m = String(text).trim().match(/([0-9.]+)\s*(mL|ml|袋|片|粒|mg|g|ml|ML)?/i);
    if (!m) return null;
    const v = parseFloat(m[1]);
    if (isNaN(v)) return null;
    return { value: v, unit: (m[2] || '').toLowerCase() };
  }
  // 单位归一化比较：mL/ml 等价，袋/片/粒各自等价
  function sameUnit(a, b) {
    if (!a || !b) return false;
    a = a.toLowerCase(); b = b.toLowerCase();
    if (a === b) return true;
    if ((a === 'ml' && b === 'ml') || (a === 'ml' && b === 'ml')) return true;
    return false;
  }
  // 给预览结果附加「按体重剂量建议 + 超量判断」，返回新的 view 对象
  async function attachDose(previewData, medicineName, doseText) {
    const view = Object.assign({}, previewData);
    view.dose = null;
    view.overdose = null;
    const member = members.find((m) => m.id === selectedMemberId);
    const weight = member ? parseFloat(member.weightKg) : 0;
    if (weight > 0) {
      try {
        const ds = await requestJson('/api/dose-suggest?medicine=' + encodeURIComponent(medicineName)
          + '&weightKg=' + weight);
        if (ds && ds.found) {
          view.dose = ds;
          const parsed = parseDose(doseText);
          if (parsed && ds.volUnit && sameUnit(parsed.unit, ds.volUnit) && ds.volMax) {
            if (parsed.value > ds.volMax + 1e-6) {
              view.overdose = {
                level: 'red',
                title: '单次剂量可能超量',
                reason: `按 ${weight}kg 体重，建议每次 ${ds.volMin}–${ds.volMax}${ds.volUnit}（${ds.doseMinMg}–${ds.doseMaxMg}mg）。你开的 ${parsed.value}${parsed.unit} 已超过单次上限。`,
                advice: '儿童肝肾功能尚未成熟，单次超量可能造成肝肾损伤或不良反应，请核对剂量或遵医嘱。',
              };
            }
          }
        }
      } catch (e) { /* 剂量查询不可用时忽略 */ }
    }
    return view;
  }

  function renderPreview(data) {
    safetyBox.hidden = false;
    safetyBox.className = 'rx-safety ' + (data.level || 'green');
    const mark = data.level === 'red' ? '!' : (data.level === 'amber' ? '!' : '✓');
    const overdoseLi = data.overdose ? `
      <li class="red">
        <strong>${escapeHtml(data.overdose.title)}</strong>
        <p>${escapeHtml(data.overdose.reason)}</p>
        <p class="rx-safety-advice">建议：${escapeHtml(data.overdose.advice)}</p>
      </li>` : '';
    const list = (data.issues || []).map((issue) => `
      <li class="${issue.level === 'red' ? 'red' : 'amber'}">
        <strong>${escapeHtml(issue.title)}</strong>
        ${(issue.drugs || []).length ? `<span class="rx-safety-drugs">${issue.drugs.map((d) => `<em>${escapeHtml(d)}</em>`).join('')}</span>` : ''}
        <p>${escapeHtml(issue.reason || '')}</p>
        ${issue.advice ? `<p class="rx-safety-advice">建议：${escapeHtml(issue.advice)}</p>` : ''}
      </li>`).join('');
    const doseHtml = data.dose ? `
      <div class="rx-dose-hint">
        <span class="rx-dose-icon">⚖</span>
        <div class="rx-dose-body">
          <strong>按体重给药建议（${escapeHtml(String(data.dose.weightKg))}kg）</strong>
          <p>参考药：${escapeHtml(data.dose.matchedName || data.dose.medicine)} · 每次建议 ${data.dose.volMin}–${data.dose.volMax}${escapeHtml(data.dose.volUnit)}（${data.dose.doseMinMg}–${data.dose.doseMaxMg}mg），每日不超过 ${data.dose.dayMaxVol}${escapeHtml(data.dose.volUnit)}（${data.dose.maxFreq} 次/日）。</p>
          ${data.dose.note ? `<p class="rx-dose-note">注：${escapeHtml(data.dose.note)}</p>` : ''}
        </div>
      </div>` : '';
    safetyBox.innerHTML = `
      <div class="rx-safety-head">
        <span class="rx-safety-dot">${mark}</span>
        <strong>${escapeHtml(data.levelLabel || '')}</strong>
        <span class="rx-safety-sum">${escapeHtml(data.summary || '')}</span>
      </div>
      ${doseHtml}
      ${list || overdoseLi ? `<ul class="rx-safety-list">${list}${overdoseLi}</ul>` : ''}`;
  }

  async function runCheck(medicineName) {
    if (!selected || !medicineName || medicineName.length < 2) return null;
    const seq = ++checkSeq;
    safetyBox.hidden = false;
    safetyBox.className = 'rx-safety checking';
    safetyBox.innerHTML = '<div class="rx-safety-head"><span class="rx-safety-dot">…</span><strong>正在比对患者用药</strong></div>';
    try {
      const data = await requestJson('/api/doctor/safety', {
        method: 'POST',
        body: JSON.stringify({ patient: selected.username, medicineName, memberId: selectedMemberId || 0 }),
      });
      if (seq !== checkSeq) return null;   // 丢弃过期响应，避免旧结果覆盖新输入
      const view = await attachDose(data, medicineName, document.getElementById('rxDosage').value.trim());
      safetyPreview = view;
      renderPreview(view);
      return view;
    } catch (error) {
      if (seq === checkSeq) hideSafety();
      return null;
    }
  }

  medInput.addEventListener('input', () => {
    window.clearTimeout(checkTimer);
    safetyPreview = null;
    const name = medInput.value.trim();
    if (!selected || name.length < 2) {
      hideSafety();
      return;
    }
    checkTimer = window.setTimeout(() => runCheck(name), 450);
  });

  // 医生修改「每次剂量」时，若已有预检结果，重新比对超量（体重药联动）
  const rxDoseEl = document.getElementById('rxDosage');
  if (rxDoseEl) {
    rxDoseEl.addEventListener('input', () => {
      if (!safetyPreview) return;
      const name = medInput.value.trim();
      if (name.length < 2) return;
      attachDose(safetyPreview, name, rxDoseEl.value.trim()).then(renderPreview);
    });
  }

  function renderPatients() {
    const kw = patientSearch.value.trim().toLowerCase();
    const list = patients.filter((p) =>
      !kw || (p.name + ' ' + p.username).toLowerCase().includes(kw));
    if (!list.length) {
      patientList.innerHTML = '<p class="record-empty">没有匹配的患者</p>';
      return;
    }
    patientList.innerHTML = list.map((p) => `
      <button type="button" class="rx-patient ${selected && selected.username === p.username ? 'selected' : ''}" data-username="${escapeHtml(p.username)}">
        <span class="rx-patient-name">${escapeHtml(p.name || p.username)}</span>
        <span class="rx-patient-meta">${escapeHtml(p.username)} · 问诊 ${p.consults} 次${p.scripts ? ' · 在服 ' + p.scripts + ' 条医嘱' : ''}</span>
        ${p.lastCondition ? `<span class="rx-patient-cond">最近病情：${escapeHtml(String(p.lastCondition).slice(0, 32))}</span>` : ''}
      </button>`).join('');

    patientList.querySelectorAll('.rx-patient').forEach((btn) => {
      btn.addEventListener('click', () => {
        selectPatient(patients.find((p) => p.username === btn.dataset.username) || null);
      });
    });
  }

  async function loadPatients() {
    try {
      const data = await requestJson('/api/doctor/patients');
      patients = data.patients || [];
      // 只有一位患者时自动选中，省一次点击
      if (!selected && patients.length === 1) {
        selectPatient(patients[0]);
      } else {
        renderPatients();
      }
    } catch (error) {
      patientList.innerHTML = '<p class="record-empty">患者列表加载失败</p>';
    }
  }

  async function loadMedicines() {
    try {
      const data = await requestJson('/api/medicines');
      const list = Array.isArray(data) ? data : (data.medicines || data.items || []);
      document.getElementById('rxMedicineOptions').innerHTML =
        list.map((m) => `<option value="${escapeHtml(m.name)}"></option>`).join('');
    } catch (error) {
      /* 药品清单加载失败时仍可手动输入 */
    }
  }

  async function loadPlans() {
    try {
      const qs = '/api/doctor/prescriptions?doctor=' + encodeURIComponent(doctor.username)
        + (selectedMemberId ? '&memberId=' + selectedMemberId : '');
      const data = await requestJson(qs);
      const list = data.prescriptions || [];
      const fm = members.find((m) => m.id === selectedMemberId);
      countBox.textContent = (fm ? '「' + fm.name + (fm.relation ? ' · ' + fm.relation : '') + (fm.isChild ? ' · 儿童' : '') + '」' : '') + (list.length ? '共 ' + list.length + ' 条方案' : '');
      if (!list.length) {
        listBox.innerHTML = '<p class="record-empty">还没有开出方案，选一位患者开始吧。</p>';
        return;
      }
      const filterNote = fm
        ? `<div class="rx-filter-note">当前查看：${escapeHtml(fm.name)}${fm.relation ? ' · ' + escapeHtml(fm.relation) : ''}${fm.isChild ? ' · 儿童' : ''} 的用药方案</div>`
        : '';
      listBox.innerHTML = filterNote + list.map((p) => `
        <div class="rx-plan ${p.active ? '' : 'off'}">
          <div class="rx-plan-head">
            <strong>${escapeHtml(p.patientName || p.patient)}${p.memberName ? ' · ' + escapeHtml(p.memberName) : ''}</strong>
            <span class="rx-badge ${p.active ? '' : 'off'}">${p.active ? '生效中' : '已停用'}</span>
          </div>
          <div class="rx-plan-med">${escapeHtml(p.medicineName)} · ${escapeHtml(p.dosage || '按医嘱')}</div>
          <div class="rx-times">${(p.times || []).map((t) => `<span>${escapeHtml(t)}</span>`).join('')}</div>
          <div class="rx-plan-meta">${escapeHtml(p.startDate || '')}${p.endDate ? ' 至 ' + escapeHtml(p.endDate) : ' 起长期'} · ${escapeHtml(p.createdAt)} 开具</div>
          ${p.note ? `<div class="rx-note">医嘱：${escapeHtml(p.note)}</div>` : ''}
          <div class="rx-plan-actions">
            <button type="button" class="secondary-btn rx-mini" data-toggle="${p.id}">${p.active ? '停用' : '恢复'}</button>
            <button type="button" class="secondary-btn rx-mini danger" data-del="${p.id}">删除</button>
          </div>
        </div>`).join('');

      listBox.querySelectorAll('[data-toggle]').forEach((btn) => {
        btn.addEventListener('click', () => sendAction('toggle', Number(btn.dataset.toggle)));
      });
      listBox.querySelectorAll('[data-del]').forEach((btn) => {
        btn.addEventListener('click', () => {
          if (window.confirm('删除后患者药箱里这条提醒也会一并移除，确定删除？')) {
            sendAction('delete', Number(btn.dataset.del));
          }
        });
      });
    } catch (error) {
      listBox.innerHTML = '<p class="record-empty">方案列表加载失败</p>';
    }
  }

  async function sendAction(action, id) {
    try {
      const result = await requestJson('/api/doctor/prescriptions', {
        method: 'POST',
        body: JSON.stringify({ action, id, doctorUsername: doctor.username }),
      });
      if (action === 'delete') {
        note(result.message || '方案已删除', true);
      }
      // 删除/停用后同步刷新「我开出的方案」与左侧患者在服医嘱数
      loadPlans();
      loadPatients();
    } catch (error) {
      window.alert(error.message);
      // 失败时也刷新一次，避免页面停留在过期数据上
      loadPlans();
      loadPatients();
    }
  }

  patientSearch.addEventListener('input', renderPatients);

  document.querySelectorAll('.rx-chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      document.getElementById('rxTimes').value = chip.dataset.times;
    });
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!selected) {
      note('请先在左侧选择患者', false);
      return;
    }
    const medicineName = document.getElementById('rxMedicine').value.trim();
    const times = document.getElementById('rxTimes').value.trim();
    if (!medicineName) { note('请填写药品名称', false); return; }
    if (!times) { note('请填写服药时间，如 08:00', false); return; }

    const submit = document.getElementById('rxSubmit');
    submit.disabled = true;
    try {
      // 提交前强制做一次校验，避免医生改了药名但预检结果还停留在上一个药
      const preview = await runCheck(medicineName);
      if (preview && preview.blocked) {
        const risks = (preview.issues || [])
          .filter((i) => i.level === 'red')
          .map((i) => '· ' + i.title + '：' + i.reason)
          .join('\n');
        if (!window.confirm('用药安全提示：\n' + risks + '\n\n仍要下达这条方案吗？')) {
          note('已取消下达，请调整药品后重试', false);
          return;
        }
      }
      if (preview && preview.overdose) {
        if (!window.confirm('⚠️ ' + preview.overdose.title + '\n' + preview.overdose.reason + '\n\n仍要按此剂量下达方案吗？')) {
          note('已取消下达，请核对剂量后重试', false);
          return;
        }
      }
      const result = await requestJson('/api/doctor/prescriptions', {
        method: 'POST',
        body: JSON.stringify({
          action: 'save',
          doctorUsername: doctor.username,
          doctorName: doctor.name,
          patient: selected.username,
          memberId: selectedMemberId || 0,
          medicineName,
          dosage: document.getElementById('rxDosage').value.trim(),
          times,
          startDate: normDate(document.getElementById('rxStart').value),
          endDate: normDate(document.getElementById('rxEnd').value),
          note: document.getElementById('rxNote').value.trim(),
        }),
      });
      note(result.message || '方案已下达', true);
      document.getElementById('rxMedicine').value = '';
      document.getElementById('rxDosage').value = '';
      document.getElementById('rxNote').value = '';
      safetyPreview = null;
      hideSafety();
      loadPlans();
      loadPatients();
      loadPatientSafety();
    } catch (error) {
      note(error.message, false);
    } finally {
      submit.disabled = false;
    }
  });

  loadPatients();
  loadMedicines();
  loadPlans();
}

setupDoctorPrescribe();
