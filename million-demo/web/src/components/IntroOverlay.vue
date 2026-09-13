<template>
  <div class="intro-overlay">
    <div class="intro-card">
      <div class="tag">UAV-AMS · million-demo · 缩放规模演示</div>
      <h1>低空空域数字孪生监管平台</h1>
      <p class="sub">
        3 万架无人机 · 5Hz 实时二进制推流 · WebSocket 零拷贝 · Web Worker 解析 · rAF 合批渲染
      </p>
      <ul>
        <li>🛰️ 服务端视锥聚合：高空网格化 / 低空原始点，带宽自适应切换</li>
        <li>🚨 告警体系内建：<i class="red">红</i> 严重 · <i class="gold">金</i> 一般 · <i class="blue">蓝</i> 正常，随推流实时着色</li>
        <li>📈 百万级压测验证：~60FPS · 零丢帧，报告与复现脚本见 GitHub 仓库</li>
      </ul>
      <div class="acts">
        <button class="btn primary" @click="$emit('start')">▶ 开始引导演示（约 40 秒）</button>
        <button class="btn ghost" @click="$emit('skip')">跳过，直接探索</button>
      </div>
      <div class="foot">
        源码：<a href="https://github.com/lvyanyan/UAV-AMS" target="_blank" rel="noopener">github.com/lvyanyan/UAV-AMS</a>
      </div>
    </div>
  </div>
</template>

<script setup>
defineEmits(['start', 'skip'])
</script>

<style scoped>
.intro-overlay {
  position: absolute; inset: 0; z-index: 30;
  background: rgba(10, 14, 21, 0.72); backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center;
}
.intro-card {
  max-width: 560px; padding: 34px 40px; border-radius: 14px;
  background: rgba(13, 17, 23, 0.92); border: 1px solid rgba(79, 163, 199, 0.35);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.55); color: #e6edf3;
}
.tag {
  display: inline-block; font-size: 11px; letter-spacing: 1px; color: #4fa3c7;
  border: 1px solid rgba(79, 163, 199, 0.4); border-radius: 999px; padding: 2px 12px; margin-bottom: 14px;
}
h1 { font-size: 26px; margin: 0 0 10px; color: #fff; letter-spacing: 1px; }
.sub { font-size: 13px; color: #8ea3b5; margin-bottom: 18px; }
ul { list-style: none; padding: 0; margin: 0 0 24px; }
li { font-size: 13.5px; color: #b9c7d2; padding: 5px 0; }
li i { font-style: normal; font-weight: 700; }
.red { color: #ff5252; }
.gold { color: #ffd740; }
.blue { color: #6499ff; }
.acts { display: flex; gap: 12px; flex-wrap: wrap; }
.btn {
  padding: 10px 22px; border-radius: 8px; font-size: 14px; font-weight: 600;
  cursor: pointer; border: 1px solid transparent; transition: 0.15s;
}
.btn.primary { background: #2e86ab; color: #fff; }
.btn.primary:hover { background: #3799c2; }
.btn.ghost { background: transparent; color: #8ea3b5; border-color: #33414d; }
.btn.ghost:hover { color: #fff; border-color: #4fa3c7; }
.foot { margin-top: 20px; font-size: 12px; color: #5a6a78; }
.foot a { color: #4fa3c7; text-decoration: none; }
</style>
