import pathlib

# ---------- app.js：两页插入页头视觉带 ----------
p = pathlib.Path("app.js")
s = p.read_text(encoding="utf-8")

identify_old = """  panel.innerHTML = `
    <h1 class="panel-title">拍照识药</h1>
    <div class="identify-head">"""
identify_new = """  panel.innerHTML = `
    <h1 class="panel-title">拍照识药</h1>
    <div class="page-visual">
      <img src="images/identify-visual.jpg" alt="用手机拍摄药盒进行识别" />
      <div class="pv-text">
        <strong>拍一张药盒，先看清成分</strong>
        <span>支持药盒正面拍照与说明书文字粘贴，识别后按匹配度给出候选药品。</span>
      </div>
    </div>
    <div class="identify-head">"""
assert identify_old in s
s = s.replace(identify_old, identify_new, 1)

lab_old = """    <h1 class="panel-title">化验单解读</h1>
    <p class="panel-desc">"""
lab_new = """    <h1 class="panel-title">化验单解读</h1>
    <div class="page-visual">
      <img src="images/lab-visual.jpg" alt="检验科血样与化验报告单" />
      <div class="pv-text">
        <strong>数值原样照抄，异常项逐个标出</strong>
        <span>识别错或读不到的数字单独提示核对，不猜测、不换算。</span>
      </div>
    </div>
    <p class="panel-desc">"""
assert lab_old in s
s = s.replace(lab_old, lab_new, 1)

p.write_text(s, encoding="utf-8")

# ---------- app.css：页头视觉带样式 ----------
p = pathlib.Path("app.css")
s = p.read_text(encoding="utf-8")
s += """

/* ================= 页头视觉带（拍照识药 / 化验单解读） ================= */
.page-visual {
  position: relative;
  margin: 0 0 20px;
  border: 1px solid #e3eaf2;
  background: #0e3a66;
  overflow: hidden;
}
.page-visual img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  display: block;
}
.page-visual .pv-text {
  position: absolute;
  right: 0; top: 0; bottom: 0;
  width: 46%;
  padding: 20px 26px;
  background: rgba(11, 42, 72, .86);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 7px;
}
.page-visual .pv-text strong { font-size: 15.5px; font-weight: 700; }
.page-visual .pv-text span { font-size: 12.5px; line-height: 1.8; color: #cfdcea; }
@media (max-width: 760px) {
  .page-visual img { height: 130px; }
  .page-visual .pv-text { position: static; width: auto; padding: 16px 18px; }
}
"""
p.write_text(s, encoding="utf-8")
print("page-visual inserted")
